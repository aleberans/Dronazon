package gRPCService;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.LogFormatter;
import Support.MethodSupport;
import Support.ServerMethods;
import com.example.grpc.Message.*;
import com.example.grpc.ReceiveInfoAfterConsegnaGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc.*;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.awt.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;


public class SendConsegnaToDroneImpl extends SendConsegnaToDroneImplBase {

    private static final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private final List<Drone> drones;
    private final Drone drone;
    private final SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final QueueOrdini queueOrdini;
    private final MqttClient client;
    private final Object sync;
    private final Object inDelivery;
    private final Object inForward;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;

    public SendConsegnaToDroneImpl(List<Drone> drones, Drone drone, QueueOrdini queueOrdini, MqttClient client, Object sync, Object inDelivery, Object inForward){
        this.drones = drones;
        this.drone = drone;
        this.queueOrdini = queueOrdini;
        this.client = client;
        this.sync = sync;
        this.inDelivery = inDelivery;
        this.inForward = inForward;
        methodSupport = new MethodSupport(drones);
        asynchronousMedthods = new AsynchronousMedthods(drones);
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        LogFormatter formatter = new LogFormatter();
        handler.setFormatter(formatter);
        LOGGER.addHandler(handler);
    }
    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver ) {

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        if (consegna.getIdDrone() == drone.getId()){
            try {
                drone.setInDelivery(true);
                LOGGER.info("IN CONSEGNA...");
                faiConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        else if(drone.getIsMaster() && drone.getId() != consegna.getIdDrone()){
            LOGGER.info("IL DRONE: "+ consegna.getIdDrone() + " È CADUTO E LO TOLGO");
            synchronized (drones) {
                drones.remove(methodSupport.takeDroneFromId(consegna.getIdDrone()));
            }

            Ordine ordineDaRiaggiungere = new Ordine(consegna.getIdDrone(),
                    new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY()),
                    new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY()));

            queueOrdini.add(ordineDaRiaggiungere);
        }
        else {
            try {
                drone.setInForwarding(true);
                LOGGER.info("CONSEGNA INOLTRATA, IL RICEVENTE È: " + consegna.getIdDrone());
                forwardConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void forwardConsegna(Consegna consegna) throws InterruptedException {

        Drone d = methodSupport.takeDroneFromList(drone);
        Drone successivo = methodSupport.takeDroneSuccessivo(d);

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
                        if (methodSupport.takeDroneSuccessivo(d).getIsMaster()) {
                            Drone masterCaduto = methodSupport.takeDroneSuccessivo(d);
                            LOGGER.info("IL DRONE PRIMA DEL MASTER SI È ACCORTO CHE IL MASTER È CADUTO, INDICE UNA NUOVA ELEZIONE");
                            synchronized (drones) {
                                drones.remove(masterCaduto);
                                drone.setInElection(true);
                                methodSupport.getDroneFromList(drone.getId()).setInElection(true);
                            }
                        }
                        else {
                            synchronized (drones) {
                                drones.remove(methodSupport.takeDroneSuccessivo(d));
                            }
                        }
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
                    channel.shutdownNow();
                    LOGGER.info("INFORMAZIONI SULLA CONSEGNA INOLTRATE AL SUCCESSIVO");
                    drone.setInForwarding(false);
                }
            });
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void faiConsegna(Consegna consegna) throws InterruptedException {
        Thread.sleep(5000);
        Point posizioneInizialeDrone = new Point(drone.getPosizionePartenza().x, drone.getPosizionePartenza().y);
        Point posizioneRitiro = new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY());
        Point posizioneConsegna = new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY());

        LOGGER.info("CONSEGNA EFFETTUATA");
        drone.setPosizionePartenza(posizioneConsegna);
        drone.setBatteria(drone.getBatteria()-10);
        drone.setCountConsegne(drone.getCountConsegne()+1);
        drone.setKmPercorsiSingoloDrone(posizioneInizialeDrone.distance(posizioneRitiro) + posizioneRitiro.distance(posizioneConsegna));
        asynchronousSendStatisticsAndInfoToMaster(consegna);
    }

    private void asynchronousSendStatisticsAndInfoToMaster(Consegna consegna) {

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();
            ReceiveInfoAfterConsegnaGrpc.ReceiveInfoAfterConsegnaStub stub = ReceiveInfoAfterConsegnaGrpc.newStub(channel);

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
                    .addAllInquinamento(drone.getBufferPM10())
                    .build();

            stub.receiveInfoDopoConsegna(stat, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    t.printStackTrace();
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    LOGGER.info("asynchronousSendStatisticsAndInfoToMaster");
                }

                @Override
                public void onCompleted() {
                    LOGGER.info("CONSEGNA E INVIO INFORMAZIONI EFFETTUATE");
                    drone.setBufferPM10(new ArrayList<>());
                    drone.setInDelivery(false);
                    synchronized (inDelivery) {
                        LOGGER.info("NOTIFICA CHE HA FINITO LA CONSEGNA");
                        inDelivery.notify();
                    }
                    try {
                        //LOGGER.info("CHECK BATTERIA");
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
                //LOGGER.info("IL DRONE VUOLE USCIRE E NON È IL MASTER");
                synchronized (inForward) {
                    while (d.isInForwarding()) {
                        //LOGGER.info("IL DRONE VUOLE USCIRE PER LA BATTERIA MA È IN FORWARDING");
                        inForward.wait();
                    }
                }
                synchronized (inDelivery) {
                    while (d.isInDelivery()) {
                        //LOGGER.info("IL DRONE VUOLE USCIRE PER LA BATTERIA MA È IN DELIVERY");
                        inDelivery.wait();
                    }
                }
                ServerMethods.removeDroneServer(d);
                LOGGER.info("IL DRONE È USCITO PER LA BETTERIA INFERIORE DEL 15%");
            } else {
                //LOGGER.info("IL DRONE NON HA PIÙ BATTERIA, GESTISCO TUTTO PRIMA DI CHIUDERLO");
                quitDroneMaster();
                LOGGER.info("IL DRONE MASTER È USCITO PER LA BATTERIA INFERIORE DEL 15%");
            }
            System.exit(0);
        }
    }

    private void quitDroneMaster() throws InterruptedException, MqttException {
        if (client.isConnected())
            client.disconnect();
        //LOGGER.info("MASTER DISCONNESSO DAL BROKER");

        synchronized (sync){
            while (queueOrdini.size() > 0 || !methodSupport.thereIsDroneLibero()){
                LOGGER.info("CI SONO ANCORA CONSEGNE IN CODA DA GESTIRE E NON CI SONO DRONI O C'E' UN DRONE A CUI E' STATA DATA UNA CONSEGNA, WAIT...\n"
                        + queueOrdini.size());
                sync.wait();
            }
        }

        LOGGER.info("TUTTI GLI ORDINI SONO STATI CONSUMATI");
        methodSupport.getDroneFromList(drone.getId()).setConsegnaAssegnata(false);

        synchronized (inForward) {
            while (drone.isInForwarding()) {
                LOGGER.info("IL MASTER È IN FORWARDING, WAIT...");
                inForward.wait();
            }
        }
        synchronized (inDelivery){
            while(drone.isInDelivery()) {
                LOGGER.info("IL MASTER È IN DELIVERY, WAIT...");
                inDelivery.wait();
            }
        }
        synchronized (sync){
            while(!methodSupport.allDroniLiberi()){
                LOGGER.info("CI SONO ANCORA DRONI OCCUPATI NELLE CONSEGNE");
                sync.wait();
            }
        }

        ServerMethods.sendStatistics(drones);
        ServerMethods.removeDroneServer(drone);
    }
}
