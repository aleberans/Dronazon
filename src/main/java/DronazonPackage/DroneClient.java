package DronazonPackage;

import REST.beans.Drone;
import SImulatori.Measurement;
import SImulatori.PM10Simulator;
import Support.*;
import com.example.grpc.*;
import gRPCService.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class DroneClient {

    private final Random rnd;
    private final Logger LOGGER;
    private final QueueOrdini queueOrdini;
    private final String LOCALHOST;
    private final Object sync;
    private final MqttClient client;
    private final Object inDelivery;
    private final Object inForward;
    private final List<Drone> drones;
    private final Object election;
    private final MethodSupport methodSupport;
    private final ServerMethods serverMethods;
    private final AsynchronousMedthods asynchronousMedthods;
    private final DroneRechargingQueue droneRechargingQueue;
    private final Object recharge;
    private final BufferedReader bf;
    private final HashMap<Drone, String> dronesMap;

    public DroneClient(BufferedReader bf) throws MqttException {
        this.bf = bf;
        this.dronesMap = new HashMap<>();
        rnd = new Random();
        LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
        queueOrdini = new QueueOrdini();
        LOCALHOST = "localhost";
        sync = new Object();
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        client = new MqttClient(broker, clientId);
        inDelivery = new Object();
        inForward = new Object();
        drones = new ArrayList<>();
        election = new Object();
        methodSupport = new MethodSupport(drones);
        serverMethods = new ServerMethods(drones);
        asynchronousMedthods = new AsynchronousMedthods(methodSupport);
        droneRechargingQueue = new DroneRechargingQueue(methodSupport, drones);
        recharge = new Object();
    }


    public void start() {
        try {
            LOGGER.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            LogFormatter formatter = new LogFormatter();
            handler.setFormatter(formatter);
            LOGGER.addHandler(handler);

            int portaAscolto = rnd.nextInt(1000) + 8080;
            Drone drone = new Drone(rnd.nextInt(10000), portaAscolto, LOCALHOST);
            LOGGER.info("ID DRONE: " + drone.getId());
            drones.addAll(serverMethods.addDroneServer(drone));

            methodSupport.updatePositionPartenzaDrone(drones, drone);

            if (drones.size() == 1) {
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
                methodSupport.getDroneFromList(drone.getId(), drones).setIsMaster(true);
            } else
                drone.setIsMaster(false);

            //ordino totale della lista in base all'id
            synchronized (drones) {
                drones.sort(Comparator.comparingInt(Drone::getId));
            }
            startServiceGrpc(portaAscolto, drones, drone, client);

            if (drone.getIsMaster()) {
                MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);

                SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
                sendConsegnaThread.start();


                SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones, queueOrdini);
                sendStatisticToServer.start();
            } else {
                asynchronousMedthods.asynchronousSendDroneInformation(drone, drones);
                asynchronousMedthods.asynchronousReceiveWhoIsMaster(drones, drone);
                asynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                        drones.get(drones.indexOf(methodSupport.findDrone(drones, drone))).getPosizionePartenza(),
                        drone.getDroneMaster());
            }

            PingeResultThread pingeResultThread = new PingeResultThread(drones, drone);
            //pingeResultThread.start();

            //start Thread in attesa di quit
            StopAndRechargeThread stopAndRechargeThread = new StopAndRechargeThread(drone, bf);
            stopAndRechargeThread.start();


            startSensori(drone);

        } catch (Exception e) {
            e.printStackTrace();
            //LOGGER.info("PORTA GIA USATA, ESCO");
            System.exit(0);
        }
    }

    private void startServiceGrpc(int portaAscolto, List<Drone> drones, Drone drone, MqttClient client) throws IOException {
        Server server = ServerBuilder.forPort(portaAscolto)
                .addService(new DronePresentationImpl(drones, sync, election, methodSupport))
                .addService(new ReceiveWhoIsMasterImpl(drone))
                .addService(new SendPositionToDroneMasterImpl(drones, methodSupport))
                .addService(new SendConsegnaToDroneImpl(drones, drone, queueOrdini, client, sync, inDelivery,
                        inForward, methodSupport, serverMethods, asynchronousMedthods))
                .addService(new ReceiveInfoAfterConsegnaImpl(drones, sync, methodSupport))
                .addService(new PingAliveImpl())
                .addService(new ElectionImpl(drone, drones, methodSupport))
                .addService(new NewIdMasterImpl(drones, drone, sync, client, election, methodSupport, serverMethods, asynchronousMedthods))
                .addService(new SendUpdatedInfoToMasterImpl(drones, drone, inForward, methodSupport))
                .addService(new RechargeImpl(drones, drone, droneRechargingQueue, methodSupport, asynchronousMedthods, recharge))
                .addService(new AnswerRechargeImpl(drones, dronesMap, methodSupport, recharge))
                .build();
        server.start();
    }

    private void startSensori(Drone drone) {
        PM10Buffer pm10Buffer = new PM10Buffer(
                pm10 -> drone.getBufferPM10()
                        .add(pm10.readAllAndClean()
                                .stream()
                                .map(Measurement::getValue)
                                .reduce(0.0, Double::sum)
                                / 8.0));
        new PM10Simulator(pm10Buffer).start();
    }

    static class SendStatisticToServer extends Thread {

        private final List<Drone> drones;
        private final QueueOrdini queueOrdini;
        private final ServerMethods serverMethods;

        public SendStatisticToServer(List<Drone> drones, QueueOrdini queueOrdini) {
            this.drones = drones;
            this.queueOrdini = queueOrdini;
            this.serverMethods = new ServerMethods(drones);
        }

        @Override
        public void run() {
            while (true) {
                if (queueOrdini.size() != 0) {
                    serverMethods.sendStatistics(drones);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
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
        public void run() {
            while (true) {
                try {
                    synchronized (sync) {
                        while (!methodSupport.thereIsDroneLibero(drones)) {
                            LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                            sync.wait();
                            LOGGER.info("SVEGLIATO SU SYNC");
                        }
                    }
                    asynchronousSendConsegna(drones, drone);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class PingeResultThread extends Thread {

        private final List<Drone> drones;
        private final Drone drone;

        public PingeResultThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    asynchronousMedthods.asynchronousPingAlive(drone, drones);
                    LOGGER.info("\nID DEL DRONE: " + drone.getId() + "\n"
                            + "ID DEL MASTER CORRENTE: " + drone.getDroneMaster().getId() + "\n"
                            + "POSIZIONE ATTUALE: " + drone.getPosizionePartenza() + "\n"
                            + "TOTALE CONSEGNE EFFETTUATE: " + drone.getCountConsegne() + "\n"
                            + "TOTALE KM PERCORSI: " + drone.getKmPercorsiSingoloDrone() + "\n"
                            + "P10 RILEVATO: " + drone.getBufferPM10() + "\n"
                            + "PERCENTUALE BATTERIA RESIDUA: " + drone.getBatteria() + "\n"
                            + "LISTA DRONI ATTUALE: " + methodSupport.getAllIdDroni(drones) + "\n");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class RechargeThread extends Thread {

        private final Drone drone;
        private final BufferedReader bf;

        public RechargeThread(Drone drone, BufferedReader bf) {
            this.drone = drone;
            this.bf = bf;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String check = bf.readLine();
                    LOGGER.info("PAROLA: " + check);
                    if (check.equals("rec")) {
                        LOGGER.info("DRONE INIZIA PROCESSO DI RICARICA");
                        drone.setWantRecharging(true);
                        asynchronousMedthods.rechargeBattery(drone, drones);
                        drone.setRecharged(true);
                        drone.setWantRecharging(false);
                    }
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class StopAndRechargeThread extends Thread {

        private final Drone drone;
        private final BufferedReader bf;

        public StopAndRechargeThread(Drone drone, BufferedReader bf) {
            this.drone = drone;
            this.bf = bf;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String check = bf.readLine();
                    if (check.equals("rec")) {
                        LOGGER.info("DRONE INIZIA PROCESSO DI RICARICA");
                        Thread.sleep(5000);
                        drone.setWantRecharging(true);
                        asynchronousMedthods.rechargeBattery(drone, drones);
                        drone.setRecharged(true);
                        drone.setWantRecharging(false);
                        rechargeProcess(drone);
                    } else if (check.equals("quit")) {
                        if (!drone.getIsMaster()) {
                            synchronized (inDelivery) {
                                while (drone.isInDelivery()) {
                                    LOGGER.info("IL DRONE È IL DELIVERY, WAIT...");
                                    inDelivery.wait();
                                }
                            }
                            LOGGER.info("IL DRONE HA FINITO LA DELIVERY, ESCO!");
                            synchronized (inForward) {
                                while (drone.isInForwarding()) {
                                    LOGGER.info("IL DRONE È IN FORWARDING, WAIT...");
                                    inForward.wait();
                                }
                            }
                            LOGGER.info("IL DRONE HA FINITO DI FARE FORWARDING, ESCO!");
                            serverMethods.removeDroneServer(drone);
                            break;
                        } else {
                            client.disconnect();
                            LOGGER.info("IL DRONE MASTER È STATO QUITTATO, GESTISCO TUTTO PRIMA DI CHIUDERLO");
                            LOGGER.info("STATO DRONE: \n" +
                                    "DELIVERY: " + drone.isInDelivery() + "\n" +
                                    "FORWARDING: " + drone.isInForwarding());
                            synchronized (inDelivery) {
                                while (drone.isInDelivery()) {
                                    LOGGER.info("IL DRONE È IL DELIVERY, WAIT...");
                                    inDelivery.wait();
                                }
                            }
                            synchronized (sync) {
                                while (queueOrdini.size() > 0 || !methodSupport.thereIsDroneLibero(drones)) {
                                    LOGGER.info("CI SONO ANCORA CONSEGNE IN CODA DA GESTIRE E NON CI SONO DRONI O C'E' UN DRONE A CUI E' STATA DATA UNA CONSEGNA, WAIT...\n"
                                            + queueOrdini.size() + "\n" + "lista: " + drones);
                                    sync.wait();
                                }
                            }
                            LOGGER.info("TUTTI GLI ORDINI SONO STATI CONSUMATI");
                            methodSupport.getDroneFromList(drone.getId(), drones).setConsegnaAssegnata(false);
                            synchronized (sync) {
                                while (!methodSupport.allDroniLiberi(drones)) {
                                    LOGGER.info("CI SONO ANCORA DRONI OCCUPATI NELLE CONSEGNE");
                                    sync.wait();
                                }
                            }
                            serverMethods.removeDroneServer(drone);
                            serverMethods.sendStatistics(drones);
                            break;
                        }
                    }
                } catch (MqttException | IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LOGGER.info("IL DRONE È USCITO IN MANIERA FORZATA!");
            System.exit(0);
        }
    }

    public void rechargeProcess(Drone drone) {
        if (checkRecharge(drones)) {
            methodSupport.takeDroneFromList(drone, drones).setConsegnaAssegnata(true);
            drone.setInRecharging(true);
            doRecharge(drone);
            drone.setInRecharging(false);
            LOGGER.info("MANDO OK AGLI ALTRI DRONI IN ATTESA");
            asynchronousMedthods.asynchronousSendOkAfterCompleteRecharge(droneRechargingQueue, drone);
            droneRechargingQueue.cleanQueue();
        }
    }

    public void doRecharge(Drone drone) {
        LOGGER.info("DRONE IN RICARICA...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("RICARICA DRONE EFFETTUATA");
        drone.setPosizionePartenza(new Point(0, 0));
        drone.setBatteria(100);
        asynchronousMedthods.asynchronousSendInfoAggiornateToNewMaster(drone);
    }

    public boolean checkRecharge(List<Drone> drones) {
        boolean check = false;
        synchronized (recharge) {
            while (!(dronesMap.keySet().size() == drones.size())) {
                try {
                    LOGGER.info("VA IN WAIT SU RECHARGE");
                    recharge.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        List<Integer> idDroni = new ArrayList<>();
        for (Drone drone : dronesMap.keySet()) {
            idDroni.add(drone.getId());
        }
        idDroni.sort(Comparator.naturalOrder());
        LOGGER.info("ID DRONI MAPPA ORDINATA: " + idDroni);

        for (int i = 0; i < drones.size(); i++) {
            check = drones.get(i).getId() == idDroni.get(i);
        }
        return check;
    }



    public Drone cercaDroneCheConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {
        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        droni.removeIf(d -> (d.getIsMaster() && d.getBatteria() < 20));

        LOGGER.info("DRONI DISPONIBILI: " + droni);

        return droni.stream().filter(d -> !d.consegnaAssegnata())
                    .min(Comparator.comparing(drone -> drone.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                    .orElse(null);
    }

    public void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {

        Drone d = methodSupport.takeDroneFromList(drone, drones);
        Ordine ordine = queueOrdini.consume();

        Drone successivo = methodSupport.takeDroneSuccessivo(d, drones);
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

            synchronized (drones) {
                //aggiorno la lista mettendo il drone che deve ricevere la consegna come occupato
                drones.get(drones.indexOf(methodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaAssegnata(true);
            }
            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        LOGGER.info("DURANTE L'INVIO DELL'ORDINE IL SUCCESSIVO È MORTO, LO ELIMINO E RIPROVO MANDANDO LA CONSEGNA AL SUCCESSIVO DEL SUCCESSIVO");
                        channel.shutdownNow();
                        synchronized (drones) {
                            drones.remove(methodSupport.takeDroneSuccessivo(d, drones));
                        }
                        synchronized (sync){
                            while (!methodSupport.thereIsDroneLibero(drones)) {
                                LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                                sync.wait();
                                LOGGER.info("SVEGLIATO SU SYNC");
                            }
                        }
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
                    LOGGER.info("CONSEGNA MANDATA NELL'ANELLO");
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
        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
        DroneClient droneClient = new DroneClient(bf);
        droneClient.start();
    }
}
