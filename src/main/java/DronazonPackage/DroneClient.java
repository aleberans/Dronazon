package DronazonPackage;

import REST.beans.Drone;
import SImulatori.Measurement;
import SImulatori.PM10Simulator;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.*;
import gRPCService.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DroneClient{

    private final Random rnd;
    private final Logger LOGGER;
    private final QueueOrdini queueOrdini;
    private final String LOCALHOST;
    private final Object sync;
    private final String broker;
    private final String clientId;
    private MqttClient client;

    public DroneClient() throws MqttException {
        rnd = new Random();
        LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
        queueOrdini = new QueueOrdini();
        LOCALHOST = "localhost";
        sync = new Object();
        broker = "tcp://localhost:1883";
        clientId = MqttClient.generateClientId();
        client = new MqttClient(broker, clientId);
    }


    public void start(){
        try{
            int portaAscolto = rnd.nextInt(1000) + 8080;

            Drone drone = new Drone(rnd.nextInt(10000), portaAscolto, LOCALHOST);

            LOGGER.info("ID DRONE: " + drone.getId());
            List<Drone> drones = ServerMethods.addDroneServer(drone);
            drones = MethodSupport.updatePositionPartenzaDrone(drones, drone);
            if (drones.size()==1){
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
            }
            else
                drone.setIsMaster(false);

            //ordino totale della lista in base all'id
            drones.sort(Comparator.comparingInt(Drone::getId));

            startServiceGrpc(portaAscolto, drones, drone, client);

            if (drone.getIsMaster()) {
                LOGGER.info("Sono il primo master");
                MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);

                SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
                sendConsegnaThread.start();

                SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones);
                sendStatisticToServer.start();
            }
            else {
                AsynchronousMedthods.asynchronousSendDroneInformation(drone, drones);
                AsynchronousMedthods.asynchronousSendWhoIsMaster(drones, drone);
                AsynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                        drones.get(drones.indexOf(MethodSupport.findDrone(drones, drone))).getPosizionePartenza(),
                        drone.getDroneMaster());
            }

            PingeResultThread pingeResultThread = new PingeResultThread(drones, drone);
            pingeResultThread.start();

            //start Thread in attesa di quit
            StopThread stop = new StopThread(drone, drones);
            stop.start();

            startSensori(drone);

        }catch (Exception e) {
            LOGGER.info("PORTA GIA USATA, ESCO");
            System.exit(0);
        }
    }

    private void startServiceGrpc(int portaAscolto, List<Drone> drones, Drone drone, MqttClient client) throws IOException {
        Server server = ServerBuilder.forPort(portaAscolto)
                .addService(new DronePresentationImpl(drones))
                .addService(new SendWhoIsMasterImpl(drone))
                .addService(new SendPositionToDroneMasterImpl(drones))
                .addService(new SendConsegnaToDroneImpl(drones, drone, queueOrdini, client, sync))
                .addService(new SendInfoAfterConsegnaImpl(drones, sync))
                .addService(new PingAliveImpl())
                .addService(new ElectionImpl(drone, drones, sync, client))
                .addService(new NewIdMasterImpl(drones, drone))
                .addService(new SendUpdatedInfoToMasterImpl(drones, drone, sync))
                .build();
        server.start();
    }

    private void startSensori(Drone drone){
        PM10Buffer pm10Buffer = new PM10Buffer(
                pm10 -> drone.getBufferPM10()
                        .add(pm10.readAllAndClean()
                                .stream()
                                .map(Measurement::getValue)
                                .reduce(0.0, Double::sum)
                                /8.0));
        new PM10Simulator(pm10Buffer).start();
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

    class SendConsegnaThread extends Thread {

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

    class PingeResultThread extends Thread{

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
                    AsynchronousMedthods.asynchronousPingAlive(drone, drones);
                    LOGGER.info("TOTALE CONSEGNE EFFETTUATE: " + drone.getCountConsegne() + "\n"
                            + "TOTALE KM PERCORSI: "+ drone.getKmPercorsiSingoloDrone() +"\n"
                            + "PERCENTUALE BATTERIA RESIDUA: " + drone.getBatteria() + "\n"
                            + "LISTA DRONI ATTUALE: " + MethodSupport.getAllIdDroni(drones) + "\n"
                            + "L'ATTUALE MASTER È " + drone.getDroneMaster().getId());
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class StopThread extends Thread{

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
                                for (Drone d: drones) {
                                    if (d.isInDeliveryOrForwaring()) {
                                        LOGGER.info("IL DRONE NON PUÒ USCIRE, WAIT...");
                                        drone.wait();
                                        LOGGER.info("IL DRONE E' IN FASE DI USCITA");
                                    }
                                }
                            }

                            client.disconnect();
                            synchronized (queueOrdini){
                                if (queueOrdini.size() > 0){
                                    LOGGER.info("CI SONO ANCORA CONSEGNE IN CODA DA GESTIRE, WAIT..." + "\n"
                                    + queueOrdini);
                                    queueOrdini.wait();
                                }
                            }

                            if (!(MethodSupport.thereIsDroneLibero(drones))){
                                LOGGER.info("CI SONO ANCORA DRONI A CUI È STATA ASSEGNATA UNA CONSENGA, WAIT...");
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

    public Drone cercaDroneCheConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {

        while (!MethodSupport.thereIsDroneLibero(drones)) {
            LOGGER.info("IN WAIT");
            synchronized (sync) {
                sync.wait();
            }
        }
        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        //TOLGO IL MASTER SE HA MENO DEL 15% PERCHE DEVE USCIRE
        droni.removeIf(d -> d.getIsMaster() && d.getBatteria() < 15);

        return droni.stream().filter(Drone::consegnaNonAssegnata)
                .min(Comparator.comparing(drone -> drone.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }

    public void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
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
                droneACuiConsegnare = cercaDroneCheConsegna(drones, ordine);
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
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaNonAssegnata(false);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            synchronized (queueOrdini){
                if (queueOrdini.size() == 0)
                    queueOrdini.notifyAll();
            }

            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        LOGGER.info("DURANTE L'INVIO DELL'ORDINE IL SUCCESSIVO È MORTO, LO ELIMINO E RIPROVO MANDANDO LA CONSEGNA AL SUCCESSIVO DEL SUCCESSIVO");
                        channel.shutdownNow();
                        drones.remove(MethodSupport.takeDroneSuccessivo(d, drones));
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

    public static void main(String[] args) throws MqttException {
        DroneClient droneClient = new DroneClient();
        droneClient.start();
    }
}
