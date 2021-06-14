package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Posizione;
import REST.beans.Statistic;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Message.*;
import com.example.grpc.SendWhoIsMasterGrpc;
import com.example.grpc.SendWhoIsMasterGrpc.*;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import gRPCService.DronePresentationImpl;
import gRPCService.SendWhoIsMasterImpl;
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
    private static Gson gson = new Gson();

    public static void main(String[] args) {

        try{

            int id = rnd.nextInt(10000);
            int portaAscolto = rnd.nextInt(100) + 8080;
            String ip = "localhost";

            Drone drone = new Drone(id, portaAscolto, ip);

            List<Drone> drones = addDroneServer(drone);

            System.out.println("Porta:" + drone.getPortaAscolto());
            if (drones.size()==1){
                System.out.println("ok");
                //drones.get(drones.indexOf(findDrone(drones, drone))).setIsMaster(true);
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
            }

            //ordino totale della lista in base all'id
            drones.sort(Comparator.comparingInt(Drone::getId));

            Server server = ServerBuilder.forPort(portaAscolto)
                    .addService(new DronePresentationImpl(drones))
                    .addService(new SendWhoIsMasterImpl(drones, drone))
                    .build();
            server.start();
            System.out.println("server Started");


            if (drone.getIsMaster()) {
                System.out.println("Sono il primo master");
                subTopic("dronazon/smartcity/orders/", drones, drone);
            }
            else {
                asynchronousSendDroneInformation(drone, drones);
                asynchronousSendWhoIsMaster(drones, drone);
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

    private static void asynchronousSendWhoIsMaster(List<Drone> drones, Drone drone) throws InterruptedException {

        Drone succ = takeDroneSuccessivo(drone, drones);
        System.out.println("succ:"+succ.toString());
        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+ succ.getPortaAscolto()).usePlaintext().build();

        SendWhoIsMasterStub stub = SendWhoIsMasterGrpc.newStub(channel);

        WhoMaster info = WhoMaster.newBuilder().build();
        stub.master(info, new StreamObserver<WhoIsMaster>() {
            @Override
            public void onNext(WhoIsMaster value) {
                drone.setDroneMaster(takeDroneFromId(drones, value.getIdMaster()));
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Error" + t.getMessage());
                System.out.println("Error" + t.getCause());
                System.out.println("Error" + t.getLocalizedMessage());
                System.out.println("Error" + Arrays.toString(t.getStackTrace()));
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        for (Drone d: drones){
            if (d.getId()==id)
                return d;
        }
        return null;
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

        //mando a tutti le informazioni dei parametri del drone
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
            channel.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void subTopic(String topic, List<Drone> drones, Drone drone) {
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

                    Ordine ordine = gson.fromJson(receivedMessage, Ordine.class);
                    //System.out.println(ordine.toString());

                    //Drone droneTarget =
                    computeDroneCheDeveEffettuareLaConsegna(ordine, drones);
                    //sendConsegnaAlDroneSuccessivo(receivedMessage, drones, drone);
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

    private static int computeDistance(Drone drone, Posizione posizione){

    }


    private static void computeDroneCheDeveEffettuareLaConsegna(Ordine ordine, List<Drone> drones){
        Posizione puntoRitiro = ordine.getPuntoRitiro();
        int distanza = 0;
        for(Drone d : drones){
            if (!d.isOccupato()){
                distanza = computeDistance(d, puntoRitiro);
            }
        }
    }

    /*rivate static void sendConsegnaAlDroneSuccessivo(String receivedMessage, List<Drone> drones, Drone drone) {
        Drone succ = takeDroneSuccessivo(drone, drones);
        Drone target = computeDroneCheDeveEffettuareLaConsegna(receivedMessage, drones);



    }*/

    private static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        int pos = drones.indexOf(findDrone(drones, drone));
        Drone succ = drones.get( (pos+1)%drones.size());
        return succ;
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
