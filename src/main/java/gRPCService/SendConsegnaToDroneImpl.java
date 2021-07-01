package gRPCService;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
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
    private QueueOrdini queueOrdini;
    private static final String LOCALHOST = "localhost";

    public SendConsegnaToDroneImpl(List<Drone> drones, Drone drone, QueueOrdini queueOrdini){
        this.drones = drones;
        this.drone = drone;
        this.queueOrdini = queueOrdini;
    }


    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver) {

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
            drones.remove(takeDroneFromId(drones, consegna.getIdDrone()));

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

        Drone d = drones.get(drones.indexOf(findDrone(drones, drone)));

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + takeDroneSuccessivo(d, drones).getPortaAscolto()).usePlaintext().build();

            SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

            stub.sendConsegna(consegna, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        channel.shutdownNow();
                        if (takeDroneSuccessivo(d, drones).getIsMaster()){
                            Drone masterCaduto = takeDroneSuccessivo(d, drones);
                            startElection(drones, d, masterCaduto);
                        }
                        drones.remove(takeDroneSuccessivo(d, drones));
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

        //ELETTO QUELLO CON BATTERIA MAGGIORE
        asynchronousStartElection(drones, drone);
    }

    private void asynchronousStartElection(List<Drone> drones, Drone drone){
        Drone successivo = takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+successivo.getPortaAscolto()).usePlaintext().build();

            ElectionGrpc.ElectionStub stub = ElectionGrpc.newStub(channel);

            ElectionMessage electionMessage = ElectionMessage.newBuilder().setIdCurrentMaster(drone.getId()).build();

            stub.sendElection(electionMessage, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    drones.remove(successivo);
                    asynchronousStartElection(drones, drone);
                    LOGGER.info("PROVA A MANDARE IL MESSAGGIO DI ELEZIONE AL SUCCESSIVO MA È MORTO");
                }

                @Override
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
                    checkBatteryDrone(drone);
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

    private void checkBatteryDrone(Drone drone) {
        if (!drone.getIsMaster()){
            if (drone.getBatteria()<= 15){
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
            }
        }
    }

    private static Drone findDrone(List<Drone> drones, Drone drone){

        for (Drone d: drones){
            if (d.getId() == drone.getId())
                return d;
        }
        return drone;
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        for (Drone d: drones){
            if (d.getId()==id)
                return d;
        }
        return null;
    }

    private static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        int pos = drones.indexOf(findDrone(drones, drone));
        return drones.get( (pos+1)%drones.size());
    }

}
