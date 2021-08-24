package DronazonPackage;

import REST.beans.Drone;
import SImulatori.Measurement;
import SImulatori.PM10Simulator;
import Support.*;
import gRPCService.*;
import io.grpc.*;
import org.eclipse.paho.client.mqttv3.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class DroneClient{

    private final Random rnd;
    private final Logger LOGGER;
    private final QueueOrdini queueOrdini;
    private final String LOCALHOST;
    private final Object sync;
    private final MqttClient client;
    private final Object inDelivery;
    private final Object inForward;
    private List<Drone> drones;
    private final Object election;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;

    public DroneClient() throws MqttException {
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
        asynchronousMedthods = new AsynchronousMedthods(drones);
    }
    
    public void start(){
        try{
            LOGGER.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            LogFormatter formatter = new LogFormatter();
            handler.setFormatter(formatter);
            LOGGER.addHandler(handler);

            int portaAscolto = rnd.nextInt(1000) + 8080;
            Drone drone = new Drone(rnd.nextInt(10000), portaAscolto, LOCALHOST);

            drones = ServerMethods.addDroneServer(drone);
            drones = methodSupport.updatePositionPartenzaDrone(drone);

            if (drones.size()==1){
                drone.setIsMaster(true);
                drone.setDroneMaster(drone);
                methodSupport.getDroneFromList(drone.getId()).setIsMaster(true);
            }
            else
                drone.setIsMaster(false);

            //ordino totale della lista in base all'id
            synchronized (drones) {
                drones.sort(Comparator.comparingInt(Drone::getId));
            }
            startServiceGrpc(portaAscolto, drones, drone, client);

            if (drone.getIsMaster()) {
                MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);

                Threads.SendConsegnaThread sendConsegnaThread = new Threads.SendConsegnaThread(drones, drone, sync, queueOrdini);
                sendConsegnaThread.start();


                Threads.SendStatisticToServer sendStatisticToServer = new Threads.SendStatisticToServer(drones, queueOrdini);
                sendStatisticToServer.start();
            }
            else {
                asynchronousMedthods.asynchronousSendDroneInformation(drone);
                asynchronousMedthods.asynchronousReceiveWhoIsMaster(drone);
                asynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                        drones.get(drones.indexOf(methodSupport.findDrone(drone))).getPosizionePartenza(),
                        drone.getDroneMaster());
            }

            Threads.PingeResultThread pingeResultThread = new Threads.PingeResultThread(drones, drone);
            pingeResultThread.start();

            //start Thread in attesa di quit
            Threads.StopThread stop = new Threads.StopThread(drone, drones, inDelivery, inForward, client, queueOrdini, sync);
            stop.start();

            startSensori(drone);

        }catch (Exception e) {
            e.printStackTrace();
            LOGGER.info("PORTA GIA USATA, ESCO");
            System.exit(0);
        }
    }

    private void startServiceGrpc(int portaAscolto, List<Drone> drones, Drone drone, MqttClient client) throws IOException {
        Server server = ServerBuilder.forPort(portaAscolto)
                .addService(new DronePresentationImpl(drones, sync, election))
                .addService(new ReceiveWhoIsMasterImpl(drone))
                .addService(new SendPositionToDroneMasterImpl(drones))
                .addService(new SendConsegnaToDroneImpl(drones, drone, queueOrdini, client, sync, inDelivery, inForward))
                .addService(new ReceiveInfoAfterConsegnaImpl(drones, sync))
                .addService(new PingAliveImpl())
                .addService(new ElectionImpl(drone, drones))
                .addService(new NewIdMasterImpl(drones, drone, sync, client, election))
                .addService(new SendUpdatedInfoToMasterImpl(drones, drone, inForward))
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

    public static void main(String[] args) throws MqttException {
        DroneClient droneClient = new DroneClient();
        droneClient.start();
    }
}
