package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.*;
import com.example.grpc.DronePresentationGrpc.*;
import com.example.grpc.Message.*;
import com.example.grpc.SendWhoIsMasterGrpc.*;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import gRPCService.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.*;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
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
    private static final Object sync = new Object();
    private static final String broker = "tcp://localhost:1883";
    private static final String clientId = MqttClient.generateClientId();
    private static MqttClient client = null;

    static {
        try {
            client = new MqttClient(broker, clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        try{

            int id = rnd.nextInt(10000);
            int portaAscolto = rnd.nextInt(100) + 8080;

            Drone drone = new Drone(id, portaAscolto, LOCALHOST);

            LOGGER.info("ID DRONE: " + drone.getId());
            List<Drone> drones = addDroneServer(drone);
            drones = updatePositionDrone(drones, drone);
            //LOGGER.info("POSIZIONE INZIALE MAIN:" + drone.getPosizionePartenza());
            if (drones.size()==1){
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
            }
            else
                drone.setIsMaster(false);

            //ordino totale della lista in base all'id
            drones.sort(Comparator.comparingInt(Drone::getId));

            try{
            Server server = ServerBuilder.forPort(portaAscolto)
                    .addService(new DronePresentationImpl(drones))
                    .addService(new SendWhoIsMasterImpl(drones, drone))
                    .addService(new SendPositionToDroneMasterImpl(drones))
                    .addService(new SendConsegnaToDroneImpl(drones, drone, queueOrdini, client, sync))
                    .addService(new SendInfoAfterConsegnaImpl(drones, sync))
                    .addService(new PingAliveImpl())
                    .addService(new ElectionImpl(drone, drones, sync))
                    .addService(new NewIdMasterImpl(drones, drone))
                    .addService(new SendUpdatedInfoToMasterImpl(drones, drone))
                    .build();
            server.start();
            }catch (BindException b){
                drone.setPortaAscolto(rnd.nextInt(100) + 8080);
            }
            //LOGGER.info("server Started");


            if (drone.getIsMaster()) {
                LOGGER.info("Sono il primo master");
                subTopic("dronazon/smartcity/orders/", client);

                SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
                sendConsegnaThread.start();

                SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones);
                sendStatisticToServer.start();
            }
            else {
                AsynchronousMedthods.asynchronousSendDroneInformation(drone, drones);
                AsynchronousMedthods.asynchronousSendWhoIsMaster(drones, drone);
                //LOGGER.info("Il master è: " + drone.getDroneMaster().getId());
                //LOGGER.info("pos"+drones.get(drones.indexOf(findDrone(drones, drone))).getPosizionePartenza());
                AsynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                        drones.get(drones.indexOf(MethodSupport.findDrone(drones, drone))).getPosizionePartenza(),
                        drone.getDroneMaster());
            }

            PingeResultThread pingeResultThread = new PingeResultThread(drones, drone);
            pingeResultThread.start();

            //start Thread in attesa di quit
            StopThread stop = new StopThread(drone, drones);
            stop.start();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class SendStatisticToServer extends Thread{

        private final List<Drone> drones;

        public SendStatisticToServer(List<Drone> drones){
            this.drones = drones;
        }

        @Override
        public void run(){
            while(true){
                sendStatistics(drones);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class SendConsegnaThread extends Thread {

        private final List<Drone> drones;
        private final Drone drone;

        public SendConsegnaThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
        }

        @Override
        public void run(){
            while (true) {
                try {
                    AsynchronousMedthods.asynchronousSendConsegna(drones, drone, queueOrdini, sync);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class PingeResultThread extends Thread{

        private final List<Drone> drones;
        private final Drone drone;

        public PingeResultThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
        }

        @Override
        public void run(){
            while(true){
                try {
                    //LOGGER.info("PING ALIVE");
                    AsynchronousMedthods.asynchronousPingAlive(drone, drones);
                    printInformazioni(drone.getKmPercorsiSingoloDrone(), drone.getCountConsegne(), drone.getBatteria(), drones, drone);
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private static void printInformazioni(double arrayKmPercorsi, int countConsegne, int batteriaResidua, List<Drone> drones, Drone drone){
        LOGGER.info("TOTALE CONSEGNE EFFETTUATE: " + countConsegne+"\n"
                + "TOTALE KM PERCORSI: "+ arrayKmPercorsi +"\n"
                + "PERCENTUALE BATTERIA RESIDUA: " + batteriaResidua + "\n"
                + "LISTA DRONI ATTUALE: " + MethodSupport.getAllIdDroni(drones) + "\n"
                + "L'ATTUALE MASTER È " + drone.getDroneMaster().getId());
    }

    /**
     * @param drone
     * @param drones
     * Pinga il drone successivo nell'anello e nel caso in cui ritorni nella onError lo rimuove dalla sua lista locale
     */

    /**
     * mando la posizione al drone master
     */


    static class StopThread extends Thread{

        private final Drone drone;
        private final List<Drone> drones;

        public StopThread(Drone drone, List<Drone> drones){
            this.drone = drone;
            this.drones = drones;
        }

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
        @Override
        public void run() {
            while (true) {
                try {
                    if (bf.readLine().equals("quit")){
                        LOGGER.info("SI ACCORGE CHE PREMO WAIT?");
                        if (!drone.getIsMaster()) {
                            synchronized (drone) {
                                if (drone.isInDeliveryOrForwaring()) {
                                    LOGGER.info("IL DRONE NON PUÒ USCIRE, WAIT...");
                                    drone.wait();
                                    LOGGER.info("IL DRONE E' IN FASE DI USCITA");
                                }
                            }
                            removeDroneServer(drone);
                            break;
                        }else{
                            LOGGER.info("IL DRONE MASTER È STATO QUITTATO, GESTISCO TUTTO PRIMA DI CHIUDERLO");
                            synchronized (drone){
                                if (drone.isInDeliveryOrForwaring()) {
                                    LOGGER.info("IL DRONE NON PUÒ USCIRE, WAIT...");
                                    drone.wait();
                                    LOGGER.info("IL DRONE E' IN FASE DI USCITA");
                                }
                            }
                            client.disconnect();
                            synchronized (queueOrdini){
                                if (queueOrdini.size() > 0){
                                    LOGGER.info("CI SONO ANCORA CONSEGNE DA GESTIRE, WAIT...");
                                    queueOrdini.wait();
                                }
                            }
                            if (!(MethodSupport.thereIsDroneLibero(drones))){
                                LOGGER.info("CI SONO ANCORA DRONI OCCUPATI CHE STANNO CONSEGNANDO, WAIT...");
                                synchronized (sync){
                                    sync.wait();
                                }
                            }
                            removeDroneServer(drone);
                            sendStatistics(drones);
                            break;
                        }
                    }
                } catch (IOException | InterruptedException | MqttException e) {
                    e.printStackTrace();
                }
            }
            LOGGER.info("IL DRONE È USCITO IN MANIERA FORZATA!");
            System.exit(0);
        }
    }

    private static void subTopic(String topic, MqttClient client) {
        int qos = 0;
        try {
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.connect();

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.info(clientId + " Connection lost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    String receivedMessage = new String(message.getPayload());
                    /*LOGGER.info(clientId +" Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "\n");*/

                    Ordine ordine = gson.fromJson(receivedMessage, Ordine.class);

                    queueOrdini.add(ordine);
                    //LOGGER.info("ordini:" + queueOrdini);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            //LOGGER.info(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
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



    /**
     * Calcolo il drone più vicino al punto di ritiro della consegna
     * il drone non deve essere occupato. Viene scelto il drone più vicino con maggiore livello di batteria.
     * Nel caso ci siano più droni con queste caratteristiche viene preso quello con id maggiore.
     */

    public static String sendStatistics(List<Drone> drones){
        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        int mediaCountConsegne = 0;
        double mediaKmPercorsi = 0.0;
        int mediaInquinamento = 0;
        int mediaBatteriaResidua = 0;
        int countDroniAttivi = 0;

        mediaCountConsegne = drones.stream().map(Drone::getCountConsegne).reduce(0, Integer::sum);
        mediaBatteriaResidua = drones.stream().map(Drone::getBatteria).reduce(0, Integer::sum);
        mediaKmPercorsi = drones.stream().map(Drone::getKmPercorsiSingoloDrone).reduce(0.0, Double::sum);
        countDroniAttivi = (int) drones.stream().map(Drone::getId).count();

        Statistic statistic = new Statistic(ts.toString(),  mediaCountConsegne/countDroniAttivi,
                mediaKmPercorsi / countDroniAttivi,
                mediaInquinamento,
                mediaBatteriaResidua/countDroniAttivi);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        return "Output from Server .... \n" + response.getEntity(String.class);
    }

    /**
     * @param drone
     * @return
     * Il drone viene aggiunto al server
     */
    public static List<Drone> addDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }

    /**
     * @param drones
     * @param drone
     * @return
     * Aggiorna la posizione del drone all'interno della lista dei droni
     * Aggiorna inoltre l'attributo della posizione del singolo drone
     */
    public static List<Drone> updatePositionDrone(List<Drone> drones, Drone drone){
        Random rnd = new Random();
        Point posizionePartenza = new Point(rnd.nextInt(10), rnd.nextInt(10));
        drones.get(drones.indexOf(MethodSupport.findDrone(drones, drone))).setPosizionePartenza(posizionePartenza);

        drone.setPosizionePartenza(posizionePartenza);
        return drones;
    }

    /**
     * @param drone
     * Rimuove il drone dal server a seguito della "quit"
     */
    private static void removeDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/remove/" + drone.getId());

        ClientResponse response = webResource.type("application/json").delete(ClientResponse.class, drone.getId());

        if (response.getStatus() != 200){
            throw new RuntimeException("Fallito : codice HTTP " + response.getStatus());
        }
    }
}
