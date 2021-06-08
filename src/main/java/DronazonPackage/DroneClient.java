package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Message.*;
import com.example.grpc.SendWhoIsMasterGrpc;
import com.example.grpc.SendWhoIsMasterGrpc.*;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DroneClient{

    private final static Random rnd = new Random();
    private static Drone drone;

    public static void main(String[] args) {

        try{

            int id = rnd.nextInt(10000);
            int portaAscolto = rnd.nextInt(1000) + 8888;
            String ip = "localhost";

            drone = new Drone(id, portaAscolto, ip);

            List<Drone> drones = addDroneServer(drone);

            System.out.println("Porta:" + drone.getPortaAscolto());
            if (drones.size()==1)
                drone.setIsMaster(true);

            //ordino totale della lista in base all'id
            drones.sort(Comparator.comparingInt(Drone::getId));

            Server server = ServerBuilder.forPort(portaAscolto).addService(new DronePresentationImpl(drones)).build();
            server.start();
            System.out.println("server Started");

            if (drone.getIsMaster()) {
                System.out.println("Sono il primo master");
                subTopic("dronazon/smartcity/orders/");
            }
            else {
                //prendo la posizione del nodo successivo in modulo
                //int posSuccessivo = (drones.indexOf(findDrone(drones, drone))+1)%drones.size();
                asynchronousSendDroneInformation(drone, drones);
                asynchronousSendWhoIsMaster(drones);
            }

            //start Thread in attesa di quit
            StopThread stop = new StopThread();
            stop.start();
            synchronized (stop){
                stop.wait();
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void asynchronousSendWhoIsMaster(List<Drone> drones, drone) throws InterruptedException {
        for (Drone d: drones){
            if (d.getIsMaster()){
                final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + d.getPortaAscolto()).usePlaintext().build();

                SendWhoIsMasterStub stub1 = SendWhoIsMasterGrpc.newStub(channel);

                WhoMaster whoMaster = WhoMaster.newBuilder().setMaster(d.getId()).build();
                stub1.master(whoMaster , new StreamObserver<ackMessage> (){

                    @Override
                    public void onNext(ackMessage value) {
                        System.out.println(value.getMessage());
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("Error" + t.getMessage());
                        System.out.println("Error" + t.getCause());
                        System.out.println("Error" + t.getLocalizedMessage());
                        System.out.println("Error" + Arrays.toString(t.getStackTrace()));
                    }

                    @Override
                    public void onCompleted() {
                        channel.shutdown();
                    }
                });
                channel.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    public static Drone findDrone(List<Drone> drones, Drone drone){

        for (Drone d: drones){
            if (d.getId() == drone.getId())
                return d;
        }
        return drone;
    }

    static class StopThread extends Thread{
        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
        @Override
        public void run() {
            while (true) {
                try {
                    if (!!bf.readLine().equals("quit")) break;
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            System.exit(0);
            System.out.println("Il drone è uscito dalla rete in maniera forzata!");
        }
    }

    public static void asynchronousSendDroneInformation(Drone drone, List<Drone> drones) throws InterruptedException {

        //trovo la lista di droni a cui mandare il messaggio escludendo il drone che chiama il metodo asynchronousSendDroneInformation
        Drone d = findDrone(drones, drone);
        Predicate<Drone> byId = dr -> dr.getId() != d.getId();
        List<Drone> pulito = drones.stream().filter(byId).collect(Collectors.toList());

        //mando a tutti le informazioni relative ai parametri del drone
        for (Drone dron: pulito){
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + dron.getPortaAscolto()).usePlaintext().build();

            DronePresentationStub stub = DronePresentationGrpc.newStub(channel);

            SendInfoDrone.Position position = SendInfoDrone.Position.newBuilder().setX(dron.getPosizionePartenza().getxPosizioneIniziale())
                    .setY(dron.getPosizionePartenza().getyPosizioneIniziale()).build();

            SendInfoDrone info = SendInfoDrone.newBuilder().setId(drone.getId()).setPortaAscolto(drone.getPortaAscolto())
                    .setIndirizzoDrone(drone.getIndirizzoIpDrone()).setPosition(position).build();

            stub.presentation(info, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                    System.out.println(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("Error" + t.getMessage());
                    System.out.println("Error" + t.getCause());
                    System.out.println("Error" + t.getLocalizedMessage());
                    System.out.println("Error" + Arrays.toString(t.getStackTrace()));
                }

                @Override
                public void onCompleted() {
                    channel.shutdown();
                }
            });
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
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

    /*private static void ringUpdateNextDrone(Drone drone, List<Drone> drones) {
        drone.setNextDrone(drones.get(0));

        for(Drone d: drones){
            if (d.getNextDrone() == drones.get(0))
                d.setNextDrone(drone);
        }
    }*/

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
