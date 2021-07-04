package gRPCService;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.ServerMethods;
import com.example.grpc.Message.*;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc.*;
import com.example.grpc.SendInfoAfterConsegnaGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.awt.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
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


            Ordine ordineDaRiaggiungere = new Ordine(consegna.getIdDrone(),
                    new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY()),
                    new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY()));

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

    private void quitDroneMaster() throws InterruptedException, MqttException {
        LOGGER.info("IL DRONE NON HA PIÙ BATTERIA, GESTISCO TUTTO PRIMA DI CHIUDERLO");
        for (Drone dr: drones) {
            synchronized (drone) {
                while (dr.isInDeliveryOrForwaring()) {
                    LOGGER.info("IL DRONE NON PUÒ USCIRE, WAIT...");
                    drone.wait();
                    LOGGER.info("IL DRONE E' IN FASE DI USCITA");
                }
            }
        }
        client.disconnect();
        LOGGER.info("MASTER DISCONNESSO DAL BROKER");

        synchronized (queueOrdini){
            while (queueOrdini.size() > 0){
                LOGGER.info("CI SONO ANCORA CONSEGNE DA GESTIRE, WAIT...");
                queueOrdini.wait();
            }
        }

        synchronized (sync){
            while (!MethodSupport.thereIsDroneLibero(drones)){
                LOGGER.info("CI SONO ANCORA DRONI A CUI È STATA ASSEGNATA UNA CONSEGNA, WAIT...");
                sync.wait();
            }
        }
        ServerMethods.sendStatistics(drones);
        ServerMethods.removeDroneServer(drone);
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
                        LOGGER.info("CHECK BATTERIA");
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

    private void checkBatteryDrone(Drone d) throws MqttException, InterruptedException {
        if (d.getBatteria() <= 15) {
            if (!d.getIsMaster()) {
                LOGGER.info("IL DRONE VUOLE USCIRE E NON È IL MASTER");
                synchronized (drone) {
                    if (d.isInDeliveryOrForwaring()) {
                        try {
                            LOGGER.info("IL DRONE VUOLE USCIRE PER LA BATTERIA MA È IN DELIVERY O FORWARDING");
                            drone.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                ServerMethods.removeDroneServer(d);
                LOGGER.info("IL DRONE È USCITO PER LA BETTERIA INFERIORE DEL 15%");
                System.exit(0);
            } else {
                quitDroneMaster();
                LOGGER.info("IL DRONE MASTER È USCITO PER LA BETTERIA INFERIORE DEL 15%");
                System.exit(0);
            }
        }
    }
}
