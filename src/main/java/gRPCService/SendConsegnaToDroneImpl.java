package gRPCService;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import REST.beans.Statistic;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.Message.*;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc.*;
import com.example.grpc.SendInfoAfterConsegnaGrpc;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.awt.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class SendConsegnaToDroneImpl extends SendConsegnaToDroneImplBase {

    private static final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private final List<Drone> drones;
    private final Drone drone;
    private static final SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private double kmPercorsiConsenga;
    private final QueueOrdini queueOrdini;
    private static final String LOCALHOST = "localhost";
    private static final String broker = "tcp://localhost:1883";
    private static final String clientId = MqttClient.generateClientId();
    private static MqttClient client = null;
    private final Object sync;

    static {
        try {
            client = new MqttClient(broker, clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public SendConsegnaToDroneImpl(List<Drone> drones, Drone drone, QueueOrdini queueOrdini, MqttClient client, Object sync){
        this.drones = drones;
        this.drone = drone;
        this.queueOrdini = queueOrdini;
        this.client = client;
        this.sync = sync;
    }


    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver ) {

        drone.setInDeliveryOrForwaring(true);
        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        if (consegna.getIdDrone() == drone.getId()){
            try {
                faiConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        else if(drone.getIsMaster() && drone.getId() != consegna.getIdDrone()){
            LOGGER.info("IL DRONE: "+ consegna.getIdDrone() + " È CADUTO E LO TOLGO");
            drones.remove(MethodSupport.takeDroneFromId(drones, consegna.getIdDrone()));

            Point puntoRitiro = new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY());
            Point puntoConsegna = new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY());

            Ordine ordineDaRiaggiungere = new Ordine(consegna.getIdDrone(), puntoRitiro, puntoConsegna);
            queueOrdini.add(ordineDaRiaggiungere);
        }
        else {
            try {
                LOGGER.info("CONSEGNA INOLTRATA, IL RICEVENTE È: " + consegna.getIdDrone());
                forwardConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void quitDroneMaster() throws InterruptedException {
        LOGGER.info("IL DRONE NON HA PIÙ BATTERIA, GESTISCO TUTTO PRIMA DI CHIUDERLO");
        synchronized (drone){
            if (drone.isInDeliveryOrForwaring()) {
                LOGGER.info("IL DRONE NON PUÒ USCIRE, WAIT...");
                drone.wait();
                LOGGER.info("IL DRONE E' IN FASE DI USCITA");
            }
        }
        try {
            client.disconnect();
        } catch (MqttException e) {
            LOGGER.info("MASTER DISCONNESSO DAL BROKER");
        }
        synchronized (queueOrdini){
            if (queueOrdini.size() > 0){
                LOGGER.info("CI SONO ANCORA CONSEGNE DA GESTIRE, WAIT...");
                queueOrdini.wait();
            }
        }
        if (!MethodSupport.thereIsDroneLibero(drones)){
            LOGGER.info("CI SONO ANCORA DRONI OCCUPATI CHE STANNO CONSEGNANDO, WAIT...");
            synchronized (sync){
                sync.wait();
            }
        }
        sendStatistics(drones);
        removeDroneServer(drone);
        LOGGER.info("IL DRONE MASTER È USCITO PER LA BETTERIA INFERIORE DEL 15%");
        System.exit(0);
    }

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

    private void forwardConsegna(Consegna consegna) throws InterruptedException {

        Drone d = MethodSupport.takeDroneFromList(drone, drones);
        Drone successivo = MethodSupport.takeDroneSuccessivo(d, drones);

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

            stub.sendConsegna(consegna, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        channel.shutdownNow();
                        if (MethodSupport.takeDroneSuccessivo(d, drones).getIsMaster()){
                            Drone masterCaduto = MethodSupport.takeDroneSuccessivo(d, drones);
                            LOGGER.info("IL DRONE PRIMA DEL MASTER SI È ACCORTO CHE IL MASTER È CADUTO, INDICE UNA NUOVA ELEZIONE");
                            startElection(drones, d, masterCaduto);
                        }
                        drones.remove(MethodSupport.takeDroneSuccessivo(d, drones));
                        forwardConsegna(consegna);
                    } catch (InterruptedException e) {
                        try {
                            channel.awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                            e.printStackTrace();
                            LOGGER.info("Error" + t.getMessage());
                            LOGGER.info("Error" + t.getCause());
                            LOGGER.info("Error" + t.getLocalizedMessage());
                            LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                            LOGGER.info("forwardConsegna");
                        }
                    }
                }

                @Override
                public void onCompleted() {
                    LOGGER.info("INFORMAZIONI SULLA CONSEGNA INOLTRATE AL SUCCESSIVO");
                    drone.setInDeliveryOrForwaring(false);
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

    private void startElection(List<Drone> drones, Drone drone, Drone masterCaduto) {
        drones.remove(masterCaduto);
        AsynchronousMedthods.asynchronousStartElection(drones, drone);
    }

    private void faiConsegna(Consegna consegna) throws InterruptedException {
        Thread.sleep(5000);
        drone.setBatteria(drone.getBatteria()-10);
        drone.setCountConsegne(drone.getCountConsegne()+1);
        kmPercorsiConsenga = updatePosizioneDroneAfterConsegnaAndComputeKmPercorsi(drone, consegna);
        LOGGER.info("KM PERCORSI SINGOLO DRONE: " + kmPercorsiConsenga);
        drone.setKmPercorsiSingoloDrone(drone.getKmPercorsiSingoloDrone() + kmPercorsiConsenga);
        LOGGER.info("CONSEGNA EFFETTUATA");
        asynchronousSendStatisticsAndInfoToMaster(consegna);
    }

    private double updatePosizioneDroneAfterConsegnaAndComputeKmPercorsi(Drone drone, Consegna consegna) {
        Point posizioneInizialeDrone = new Point(drone.getPosizionePartenza().x, drone.getPosizionePartenza().y);
        Point posizioneRitiro = new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY());
        Point posizioneConsegna = new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY());

        drone.setPosizionePartenza(posizioneConsegna);
        return posizioneInizialeDrone.distance(posizioneRitiro) + posizioneRitiro.distance(posizioneConsegna);
    }

    /**
     * @param consegna
     * @throws InterruptedException
     * Manda le informazioni dopo la consegna al drone master:
     * - id drone, - timestamp, - km percorsi, - posizioneArrivo, - batteria residua
     */
    private void asynchronousSendStatisticsAndInfoToMaster(Consegna consegna) {

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();
            SendInfoAfterConsegnaGrpc.SendInfoAfterConsegnaStub stub = SendInfoAfterConsegnaGrpc.newStub(channel);

            SendStat.Posizione pos = SendStat.Posizione.newBuilder()
                    .setX(consegna.getPuntoConsegna().getX())
                    .setY(consegna.getPuntoConsegna().getY())
                    .build();

            Timestamp timestamp = new Timestamp(System.currentTimeMillis());

            SendStat stat = SendStat.newBuilder()
                    .setIdDrone(drone.getId())
                    .setTimestampArrivo(sdf3.format(timestamp))
                    .setKmPercorsi(drone.getKmPercorsiSingoloDrone())
                    .setBetteriaResidua(drone.getBatteria())
                    .setPosizioneArrivo(pos)
                    .build();

            stub.sendInfoDopoConsegna(stat, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    LOGGER.info("asynchronousSendStatisticsAndInfoToMaster");
                }

                @Override
                public void onCompleted() {
                    drone.setInDeliveryOrForwaring(false);
                    try {
                        checkBatteryDrone(drone);
                    } catch (MqttException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    channel.shutdown();
                }
            });
            try {
                channel.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void checkBatteryDrone(Drone drone) throws MqttException, InterruptedException {
        if (drone.getBatteria()<= 15){
            if (!drone.getIsMaster()){
                LOGGER.info("IL DRONE VUOLE USCIRE E NON È IL MASTER");
                synchronized (drone){
                    if (drone.isInDeliveryOrForwaring()){
                        try {
                            LOGGER.info("IL DRONE VUOLE USCIRE PER LA BATTERIA MA È IN DELIVERY O FORWARDING");
                            drone.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                removeDroneServer(drone);
                LOGGER.info("IL DRONE È USCITO PER LA BETTERIA INFERIORE DEL 15%");
                System.exit(0);
            }else {
                quitDroneMaster();
            }
        }
    }

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

}
