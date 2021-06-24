package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.example.grpc.DronePresentationGrpc;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Message.*;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendPositionToDroneMasterGrpc;
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
import gRPCService.SendConsegnaToDroneImpl;
import gRPCService.SendPositionToDroneMasterImpl;
import gRPCService.SendWhoIsMasterImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DroneClient{

    private final static Random rnd = new Random();
    private static final Gson gson = new Gson();
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private static final QueueOrdini queueOrdini = new QueueOrdini();
    private static final String LOCALHOST = "localhost";

    public static void main(String[] args) {

        try{
            int id = rnd.nextInt(10000);
            int portaAscolto = rnd.nextInt(100) + 8080;

            Drone drone = new Drone(id, portaAscolto, LOCALHOST);

            List<Drone> drones = addDroneServer(drone);
            drones = updatePositionDrone(drones, drone);
            LOGGER.info("Porta:" + drone.getPortaAscolto());
            if (drones.size()==1){
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
            }

            //ordino totale della lista in base all'id
            drones.sort(Comparator.comparingInt(Drone::getId));

            Server server = ServerBuilder.forPort(portaAscolto)
                    .addService(new DronePresentationImpl(drones))
                    .addService(new SendWhoIsMasterImpl(drones, drone))
                    .addService(new SendPositionToDroneMasterImpl(drones))
                    .addService(new SendConsegnaToDroneImpl(drones, drone))
                    .build();
            server.start();
            LOGGER.info("server Started");


            if (drone.getIsMaster()) {
                LOGGER.info("Sono il primo master");
                subTopic("dronazon/smartcity/orders/", drones, drone);
                asynchronousSendConsegna(drones, drone);
            }
            else {
                asynchronousSendDroneInformation(drone, drones);
                asynchronousSendWhoIsMaster(drones, drone);
                //LOGGER.info("Il master è: " + drone.getDroneMaster().getId());
                //LOGGER.info("pos"+drones.get(drones.indexOf(findDrone(drones, drone))).getPosizionePartenza());
                asynchronousSendPositionToMaster(drone.getId(),
                        drones.get(drones.indexOf(findDrone(drones, drone))).getPosizionePartenza(),
                        drone.getDroneMaster());
            }

            //start Thread in attesa di quit
            StopThread stop = new StopThread();
            stop.start();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean droniDisponibili(List<Drone> drones){
        boolean disponibilita = false;
        for (Drone d: drones){
            if (!d.isOccupato()) {
                disponibilita = true;
                break;
            }
        }
        notify();
        return disponibilita;
    }

    private static void asynchronousSendPositionToMaster(int id, Point posizione, Drone master) throws InterruptedException {
        /**
         * mando la posizione al drone master
         */
        final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+master.getPortaAscolto()).usePlaintext().build();
        SendPositionToDroneMasterGrpc.SendPositionToDroneMasterStub stub = SendPositionToDroneMasterGrpc.newStub(channel);

        SendPositionToMaster.Posizione pos = SendPositionToMaster.Posizione.newBuilder().setX(posizione.x).setY(posizione.y).build();

        SendPositionToMaster position = SendPositionToMaster.newBuilder().setPos(pos).setId(id).build();

        stub.sendPosition(position, new StreamObserver<ackMessage>() {
            @Override
            public void onNext(ackMessage value) {
                LOGGER.info(value.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.info("Error" + t.getMessage());
                LOGGER.info("Error" + t.getCause());
                LOGGER.info("Error" + t.getLocalizedMessage());
                LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    private static void asynchronousSendWhoIsMaster(List<Drone> drones, Drone drone) throws InterruptedException {

        Drone succ = takeDroneSuccessivo(drone, drones);
        //LOGGER.info("successivo:"+succ.toString());
        final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+ succ.getPortaAscolto()).usePlaintext().build();

        SendWhoIsMasterStub stub = SendWhoIsMasterGrpc.newStub(channel);

        WhoMaster info = WhoMaster.newBuilder().build();
        stub.master(info, new StreamObserver<WhoIsMaster>() {
            @Override
            public void onNext(WhoIsMaster value) {
                drone.setDroneMaster(takeDroneFromId(drones, value.getIdMaster()));
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.info("Error" + t.getMessage());
                LOGGER.info("Error" + t.getCause());
                LOGGER.info("Error" + t.getLocalizedMessage());
                LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
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
                    if (bf.readLine().equals("quit")) break;
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            System.exit(0);
            LOGGER.info("Il drone è uscito dalla rete in maniera forzata!");
        }
    }

    public static void asynchronousSendDroneInformation(Drone drone, List<Drone> drones) throws InterruptedException {

        //trovo la lista di droni a cui mandare il messaggio escludendo il drone che chiama il metodo asynchronousSendDroneInformation
        Drone d = findDrone(drones, drone);
        Predicate<Drone> byId = dr -> dr.getId() != d.getId();
        List<Drone> pulito = drones.stream().filter(byId).collect(Collectors.toList());

        //mando a tutti le informazioni dei parametri del drone
        for (Drone dron: pulito){
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":" + dron.getPortaAscolto()).usePlaintext().build();

            DronePresentationStub stub = DronePresentationGrpc.newStub(channel);


            SendInfoDrone info = SendInfoDrone.newBuilder().setId(drone.getId()).setPortaAscolto(drone.getPortaAscolto())
                    .setIndirizzoDrone(drone.getIndirizzoIpDrone()).build();

            stub.presentation(info, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                    LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
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
                    LOGGER.info(clientId + " Connection lost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    String receivedMessage = new String(message.getPayload());
                    LOGGER.info(clientId +" Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "\n");

                    Ordine ordine = gson.fromJson(receivedMessage, Ordine.class);

                    queueOrdini.add(ordine);
                    //LOGGER.info("ordini:" + queueOrdini);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            LOGGER.info(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            client.subscribe(topic,qos);
            LOGGER.info(clientId + " Subscribed to topics : " + topic);

        } catch (MqttException me) {
            LOGGER.info("reason " + me.getReasonCode());
            LOGGER.info("msg " + me.getMessage());
            LOGGER.info("loc " + me.getLocalizedMessage());
            LOGGER.info("cause " + me.getCause());
            LOGGER.info("excep " + me);
            me.printStackTrace();

        }

    }

    private static void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
        Drone d = drones.get(drones.indexOf(findDrone(drones, drone)));

        Ordine ordine = DroneClient.queueOrdini.consume();
        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + takeDroneSuccessivo(d, drones).getPortaAscolto()).usePlaintext().build();

        SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

        Consegna.Posizione posizioneRitiro = Consegna.Posizione.newBuilder()
                .setX(ordine.getPuntoRitiro().x)
                .setY(ordine.getPuntoRitiro().y).build();

        Consegna.Posizione posizioneConsegna = Consegna.Posizione.newBuilder()
                .setX(ordine.getPuntoConsegna().x)
                .setY(ordine.getPuntoConsegna().y).build();

        Drone droneACuiConsegnare = findDroneToConsegnare(drones,
                ordine);

        Consegna consegna = Consegna.newBuilder()
                .setIdConsegna(ordine.getId())
                .setPuntoRitiro(posizioneRitiro)
                .setPuntoConsegna(posizioneConsegna)
                .setIdDrone(droneACuiConsegnare.getId())
                .build();

        //aggiorno la lista mettendo il drone che deve ricevere la consegna come occupato
        drones.get(drones.indexOf(findDrone(drones, droneACuiConsegnare))).setOccupato(true);


        LOGGER.info("consegna:" + consegna);

        stub.sendConsegna(consegna, new StreamObserver<ackMessage>() {
            @Override
            public void onNext(ackMessage value) {
                LOGGER.info(value.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.info("Error" + t.getMessage());
                LOGGER.info("Error" + t.getCause());
                LOGGER.info("Error" + t.getLocalizedMessage());
                LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(1, TimeUnit.SECONDS);

    }

    /**
     * Calcolo il drone più vicino al punto di ritiro della consegna
     * il drone non deve essere occupato. Viene scelto il drone più vicino con maggiore livello di batteria.
     * Nel caso ci siano più droni con queste caratteristiche viene preso quello con id maggiore.
     */
    private static Drone findDroneToConsegnare(List<Drone> drones, Ordine ordine){
        double distanceMin = 100;
        int count = 0;
        Drone droneVicino = null;
        ArrayList<Drone> droniDistantiUguale = new ArrayList<>();
        int batteriaResidua=100;
        ArrayList<Drone> droniDistantieBatteriaUguale = new ArrayList<>();

        for (Drone d: drones){
            if (!d.isOccupato()){
                if (computeDistance(d, ordine) == distanceMin){
                    count++;
                    droniDistantiUguale.add(d);
                }
                else if(computeDistance(d, ordine) < distanceMin){
                    distanceMin = computeDistance(d, ordine);
                    droneVicino = d;
                    count=0;
                }
            }
        }
        if (count==0)
            return droneVicino;
        else{
            int count2 = 0;
            for (Drone d: droniDistantiUguale){
                if (d.getBatteria() < batteriaResidua){
                    batteriaResidua = d.getBatteria();
                    droneVicino = d;
                    count2 =0;
                }
                else if (d.getBatteria() == batteriaResidua){
                    count2++;
                    droniDistantieBatteriaUguale.add(d);
                }
            }
            if (count2 == 0){
                return droneVicino;
            }
            else{
                int id = 0;
                for (Drone d: droniDistantieBatteriaUguale){
                    if (d.getId() > id)
                        id = d.getId();
                }
                return drones.get(drones.indexOf(takeDroneFromId(drones, id)));
            }
        }
    }

    private static double computeDistance(Drone drone, Ordine ordine){
        return Math.sqrt( ( (ordine.getPuntoRitiro().x - drone.getPosizionePartenza().x)^2)
                + ( ordine.getPuntoRitiro().y - drone.getPosizionePartenza().y)^2);
    }

    private static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        int pos = drones.indexOf(findDrone(drones, drone));
        return drones.get( (pos+1)%drones.size());
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

    public static List<Drone> updatePositionDrone(List<Drone> drones, Drone drone){
        Random rnd = new Random();
        drones.get(drones.indexOf(findDrone(drones, drone))).setPosizionePartenza(new Point(rnd.nextInt(10), rnd.nextInt(10)));
        return drones;
    }

}
