package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
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
            List<Drone> drones = ServerMethods.addDroneServer(drone);
            drones = MethodSupport.updatePositionPartenzaDrone(drones, drone);
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
                MqttMethods.subTopic("dronazon/smartcity/orders/", client, clientId, queueOrdini);

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
                ServerMethods.sendStatistics(drones);
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
                            ServerMethods.removeDroneServer(drone);
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
                            ServerMethods.removeDroneServer(drone);
                            ServerMethods.sendStatistics(drones);
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
}
