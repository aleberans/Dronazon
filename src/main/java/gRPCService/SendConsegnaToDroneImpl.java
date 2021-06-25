package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc.*;
import com.example.grpc.SendInfoAfterConsegnaGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class SendConsegnaToDroneImpl extends SendConsegnaToDroneImplBase {

    private static final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private final List<Drone> drones;
    private final Drone drone;

    public SendConsegnaToDroneImpl(List<Drone> drones, Drone drone){
        this.drones = drones;
        this.drone = drone;
    }


    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver) {

        if (consegna.getIdDrone() == drone.getId()){
            try {
                LOGGER.info("INIZIO CONSEGNA");
                faiConsegna(consegna);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
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
                LOGGER.info(value.getMessage());
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
        Thread.sleep(5000);
        drone.setBatteria(drone.getBatteria()-10);
        LOGGER.info("CONSEGNA EFFETTUATA");
        asynchronousSendStatisticsAndInfoToMaster(consegna);
    }

    private void asynchronousSendStatisticsAndInfoToMaster(Consegna consegna) throws InterruptedException {

        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();
        SendInfoAfterConsegnaGrpc.SendInfoAfterConsegnaStub stub = SendInfoAfterConsegnaGrpc.newStub(channel);

        SendStat.Posizione pos = SendStat.Posizione.newBuilder()
                .setX(consegna.getPuntoConsegna().getX())
                .setY(consegna.getPuntoConsegna().getY())
                .build();

        SendStat stat = SendStat.newBuilder()
                .setIdDrone(drone.getId())
                .setTimestampArrivo("ok")
                .setKmPercorsi(5)
                .setBetteriaResidua(50)
                .setPosizioneArrivo(pos)
                .build();

        stub.sendInfoDopoConsegna(stat, new StreamObserver<ackMessage>() {
            @Override
            public void onNext(ackMessage value) {
                LOGGER.info(value.getMessage());
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

    public static Drone findDrone(List<Drone> drones, Drone drone){

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


}
