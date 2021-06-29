package gRPCService;

import REST.beans.Drone;
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

    public SendConsegnaToDroneImpl(List<Drone> drones, Drone drone){
        this.drones = drones;
        this.drone = drone;
    }


    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver) {

        if (consegna.getIdDrone() == drone.getId()){
            try {
                faiConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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

    private void forwardConsegna(Consegna consegna) throws InterruptedException {
        Drone d = drones.get(drones.indexOf(findDrone(drones, drone)));

        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + takeDroneSuccessivo(d, drones).getPortaAscolto()).usePlaintext().build();

        SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

        stub.sendConsegna(consegna, new StreamObserver<ackMessage>() {
            @Override
            public void onNext(ackMessage value) {
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.info("Error" + t.getMessage());
                LOGGER.info("Error" + t.getCause());
                LOGGER.info("Error" + t.getLocalizedMessage());
                LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void faiConsegna(Consegna consegna) throws InterruptedException {
        //sendStatistics();
        LOGGER.info("INIZIO CONSEGNA");
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
        LOGGER.info("INFO SINGOLO DRONE AGGIORNATE");
        LOGGER.info("NUOVA POSIZIONE DEL DRONE: " + drone.getPosizionePartenza());
        return posizioneInizialeDrone.distance(posizioneRitiro) + posizioneRitiro.distance(posizioneConsegna);
    }

    /**
     * @param consegna
     * @throws InterruptedException
     * Manda le informazioni dopo la consegna al drone master:
     * - id drone, - timestamp, - km percorsi, - posizioneArrivo, - batteria residua
     */
    private void asynchronousSendStatisticsAndInfoToMaster(Consegna consegna) throws InterruptedException {

        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();
        SendInfoAfterConsegnaGrpc.SendInfoAfterConsegnaStub stub = SendInfoAfterConsegnaGrpc.newStub(channel);

        SendStat.Posizione pos = SendStat.Posizione.newBuilder()
                .setX(consegna.getPuntoConsegna().getX())
                .setY(consegna.getPuntoConsegna().getY())
                .build();

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        Point posizioneInizialeDrone = new Point(drone.getPosizionePartenza().x, drone.getPosizionePartenza().y);
        Point posizioneRitiro = new Point(consegna.getPuntoRitiro().getX(), consegna.getPuntoRitiro().getY());
        Point posizioneConsegna = new Point(consegna.getPuntoConsegna().getX(), consegna.getPuntoConsegna().getY());

        /*LOGGER.info("POSIZIONE ORDINE: " + posizioneRitiro);
        LOGGER.info("POSIZIONE CONSEGNA" + posizioneConsegna);
        LOGGER.info("KM PERCORSI" + drone.getKmPercorsiSingoloDrone());
        LOGGER.info("BETTARIA RESIDUA: " + drone.getBatteria());*/



        SendStat stat = SendStat.newBuilder()
                .setIdDrone(drone.getId())
                .setTimestampArrivo(sdf3.format(timestamp))
                .setKmPercorsi(drone.getKmPercorsiSingoloDrone())
                .setBetteriaResidua(drone.getBatteria())
                .setPosizioneArrivo(pos)
                .build();

        /*if (drone.getBatteria() < 15) {
            softQuitFromRing(drone, drones);
        }*/

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
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void softQuitFromRing(Drone drone, List<Drone> drones){
        if (!drone.getIsMaster()) {
            removeDroneServer(drone);
            updateRingAfterSimpleDroneQuit(drone, drones);
        }
        else{
            removeDroneServer(drone);
        }
    }

    private static void updateRingAfterSimpleDroneQuit(Drone drone, List<Drone> drones) {
        LOGGER.info("PRIMA DI AGGIORNARE"+drones.toString());
        drones.remove(drone);
        LOGGER.info("UPDATE ANELLO");
        LOGGER.info(drones.toString());
    }

    private static Drone findDrone(List<Drone> drones, Drone drone){

        for (Drone d: drones){
            if (d.getId() == drone.getId())
                return d;
        }
        return drone;
    }

    private static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        int pos = drones.indexOf(findDrone(drones, drone));
        return drones.get( (pos+1)%drones.size());
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
}
