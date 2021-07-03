package DronazonPackage;

import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.*;
import com.google.gson.Gson;
import gRPCService.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
                    asynchronousSendConsegna(drones, drone);
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

    public static Drone test(List<Drone> drones, Ordine ordine) throws InterruptedException {

        if (!MethodSupport.thereIsDroneLibero(drones)){
            LOGGER.info("IN WAIT");
            synchronized (sync){
                sync.wait();
            }
        }
        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        //TOLGO IL MASTER SE HA MENO DEL 15% PERCHE DEVE USCIRE
        droni.removeIf(d -> d.getIsMaster() & d.getBatteria() < 15);

        return droni.stream().filter(Drone::consegnaNonAssegnata)
                .min(Comparator.comparing(drone -> drone.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }

    public static Drone findDroneToConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {

        Drone drone = null;
        ArrayList<Drone> lista = new ArrayList<>();
        ArrayList<Pair<Drone, Double>> coppieDistanza = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieBatteria = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieIdMaggiore = new ArrayList<>();

        if (!MethodSupport.thereIsDroneLibero(drones)){
            //LOGGER.info("IN WAIT");
            synchronized (sync){
                sync.wait();
            }
        }

        for (Drone d: drones){
            if (d.consegnaNonAssegnata()){
                lista.add(d);
            }
        }

        //creo le varie liste
        for (Drone d: lista){
            coppieDistanza.add(new Pair<>(d, d.getPosizionePartenza().distance(ordine.getPuntoRitiro())));
            coppieBatteria.add(new Pair<>(d, d.getBatteria()));
            coppieIdMaggiore.add(new Pair<>(d, d.getId()));
        }

        Optional<Pair<Drone, Double>> droneMinDistance = coppieDistanza.stream()
                .min(Comparator.comparing(Pair::getValue));

        Optional<Pair<Drone, Integer>> droneMaxBatteria = coppieBatteria.stream()
                .max(Comparator.comparing(Pair::getValue));

        Optional<Pair<Drone, Integer>> droneMaxId = coppieIdMaggiore.stream()
                .max(Comparator.comparing(Pair::getValue));



        Pair<Drone, Double> droneDistanzaMinima = droneMinDistance.orElse(null);
        drone = droneDistanzaMinima.getKey();
        //LOGGER.info("SOLO UN DRONE CON DISTANZA MINIMA");
        return drone;
    }

    public static void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
        Drone d = MethodSupport.takeDroneFromList(drone, drones);
        Ordine ordine = queueOrdini.consume();

        Drone successivo = MethodSupport.takeDroneSuccessivo(d, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();
            SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

            Message.Consegna.Posizione posizioneRitiro = Message.Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoRitiro().x)
                    .setY(ordine.getPuntoRitiro().y)
                    .build();

            Message.Consegna.Posizione posizioneConsegna = Message.Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoConsegna().x)
                    .setY(ordine.getPuntoConsegna().y)
                    .build();

            Drone droneACuiConsegnare = null;
            try {
                droneACuiConsegnare = test(drones, ordine);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Message.Consegna consegna = Message.Consegna.newBuilder()
                    .setIdConsegna(ordine.getId())
                    .setPuntoRitiro(posizioneRitiro)
                    .setPuntoConsegna(posizioneConsegna)
                    .setIdDrone(droneACuiConsegnare.getId())
                    .build();

            //aggiorno la lista mettendo il drone che deve ricevere la consegna come occupato
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaNonAssegnata(true);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            synchronized (queueOrdini){
                if (queueOrdini.size() == 0)
                    //LOGGER.info("CODA COMPLETAMENTE SVUOTATA");
                    queueOrdini.notify();
            }
            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                    //LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        channel.shutdownNow();
                        /*LOGGER.info("LO STATO DELLA LISTA È: " + getAllIdDroni(drones) +
                                "\n IL DRONE CHE STA PROVANDO A FARE LA CONSEGNA È: " + d.getId() +
                                "\n IL DRONE SUCCESSIVO A LUI È: " + takeDroneSuccessivo(d, drones).getId());*/
                        drones.remove(MethodSupport.takeDroneSuccessivo(d, drones));
                        //LOGGER.info("STATO LISTA DOPO RIMOZIONE: " + getAllIdDroni(drones));
                        asynchronousSendConsegna(drones, d);
                    } catch (InterruptedException e) {
                        try {
                            e.printStackTrace();
                            LOGGER.info("Error" + t.getMessage());
                            LOGGER.info("Error" + t.getCause());
                            LOGGER.info("Error" + t.getLocalizedMessage());
                            LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                            channel.awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                        }

                    }
                }
                public void onCompleted() {
                    channel.shutdown();
                }
            });
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
