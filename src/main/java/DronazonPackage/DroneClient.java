package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Message.*;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import gRPCService.DronePresentationImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DroneClient{

    private final static Random rnd = new Random();
    private static int id;
    private static int portaAscoltoComunicazioneDroni;
    private static String indirizzoIP;
    private static Drone drone;

    public DroneClient(int id, int portaAscoltoComunicazioneDroni, String indirizzoIP){
        this.id = id;
        this.portaAscoltoComunicazioneDroni = portaAscoltoComunicazioneDroni;
        this.indirizzoIP = indirizzoIP;
    }

    public static void main(String[] args) {

        try{
            BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

            int id = rnd.nextInt(10000);
            String portaAscolto = Integer.toString(rnd.nextInt(1000) + 8888);
            String ip = "localhost";

            drone = new Drone(id, portaAscolto, ip);

            List<Drone> drones = addDroneServer(drone);


            if (drones.size()==1)
                drone.setIsMaster(true);

            ringUpdateNextDrone(drone, drones);

            System.out.println("ciao");

            Server server = ServerBuilder.forPort(portaAscoltoComunicazioneDroni).addService(new DronePresentationImpl(drones)).build();
            server.start();
            System.out.println("server Started");

            if (drone.getIsMaster()) {
                subTopic("dronazon/smartcity/orders/");
            }
            else {
                asynchronousSendDroneInformation(drone.getNextDrone().getPortaAscolto());
                System.out.println("ciao");
            }

            while(!bf.readLine().equals("quit")){


            }
            System.exit(0);
            System.out.println("Il drone è uscito dalla rete in maniera forzata!");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void asynchronousSendDroneInformation(String porta) throws InterruptedException {
        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+porta).usePlaintext().build();

        DronePresentationStub stub = DronePresentationGrpc.newStub(channel);

        SendInfoDrone.Position position = SendInfoDrone.Position.newBuilder().setX(1)
                .setY(1).build();

        SendInfoDrone info = SendInfoDrone.newBuilder().setId(drone.getId()).setPortaAscolto(Integer.parseInt(drone.getPortaAscolto()))
                .setIndirizzoDrone(drone.getIndirizzoIpDrone()).setPosition(position).build();

        stub.presentation(info, new StreamObserver<ackMessage>() {
            @Override
            public void onNext(ackMessage value) {
                System.out.println(value.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Error" + t.getMessage());
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void subTopic(String topic) {
        MqttClient client;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        int qos = 0;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.connect();

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connectionlost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }


                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    String receivedMessage = new String(message.getPayload());
                    System.out.println(clientId +" Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "\n");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            System.out.println(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            client.subscribe(topic,qos);
            System.out.println(clientId + " Subscribed to topics : " + topic);

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();

        }

    }

    private static void ringUpdateNextDrone(Drone drone, List<Drone> drones) {
        drone.setNextDrone(drones.get(0));

        for(Drone d: drones){
            if (d.getNextDrone() == drones.get(0))
                d.setNextDrone(drone);
        }
    }

    public static String sendStatistics(){
        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        int numeroConsegne = 10;
        int kmPercorsi = 2;
        int inquinamento = 9;
        int batteriaResidua = 3;

        Statistic statistic = new Statistic(ts.toString(), numeroConsegne, kmPercorsi, inquinamento, batteriaResidua);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        return "Output from Server .... \n" + response.getEntity(String.class);
    }

    public static List<Drone> addDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }

}
