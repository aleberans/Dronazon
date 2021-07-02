package gRPCService;

import REST.beans.Drone;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.ElectionGrpc.*;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ElectionImpl extends ElectionImplBase {

    private final Drone drone;
    private final List<Drone> drones;
    private static final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());

    public ElectionImpl(Drone drone, List<Drone> drones){
        this.drone = drone;
        this.drones = drones;
    }

    @Override
    public void sendElection(ElectionMessage electionMessage, StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();
        int currentIdMaster = electionMessage.getIdCurrentMaster();

        if (currentIdMaster < drone.getId()) {
            LOGGER.info("ID DEL DRONE È PIÙ GRANDE DELL'ID CHE STA GIRANDO COME MASTER");
            forwardElection(drone, drone.getId(), drones);
        }
        else if (currentIdMaster > drone.getId()) {
            forwardElection(drone, currentIdMaster, drones);
            LOGGER.info("ID DEL DRONE È + PICCOLO DELL'ID CHE STA GIRANDO COME MASTER");
        }
        else {
            electionCompleted(drone, currentIdMaster, drones);
            LOGGER.info("ELEZIONE FINITA, PARTE LA TRASMISSIONE DEL NUOVO MASTER CON ID: " + currentIdMaster);
        }
    }

    private void forwardElection(Drone drone, int updateIdMAster, List<Drone> drones){

        Drone successivo = takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            ElectionStub stub = ElectionGrpc.newStub(channel);

            ElectionMessage newElectionMessage = ElectionMessage.newBuilder().setIdCurrentMaster(updateIdMAster).build();

            stub.sendElection(newElectionMessage, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    drones.remove(successivo);
                    forwardElection(drone, updateIdMAster ,drones);
                }

                @Override
                public void onCompleted() {
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

    private void electionCompleted(Drone drone, int newId, List<Drone> drones){
        Drone successivo = takeDroneSuccessivo(drone, drones);

        if (newId == drone.getId()){
            LOGGER.info("IL MESSAGGIO DI ELEZIONE È TORNATO AL DRONE CON ID MAGGIORE, TUTTO REGOLARE");
        }
        else
            LOGGER.info("QUALCOSA NEL PASSAGGIO DEL NUOVO MASTER È ANDATO STORTO");
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            NewIdMasterGrpc.NewIdMasterStub stub = NewIdMasterGrpc.newStub(channel);

            IdMaster newIdMaster = IdMaster.newBuilder().setIdNewMaster(newId).build();

            stub.sendNewIdMaster(newIdMaster, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    drones.remove(successivo);
                    electionCompleted(drone, newId, drones);
                }

                @Override
                public void onCompleted() {
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
}
