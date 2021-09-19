package gRPCService;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.ElectionGrpc.ElectionImplBase;
import com.example.grpc.ElectionGrpc.ElectionStub;
import com.example.grpc.Message.ElectionMessage;
import com.example.grpc.Message.IdMaster;
import com.example.grpc.Message.ackMessage;
import com.example.grpc.NewIdMasterGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ElectionImpl extends ElectionImplBase {

    private final Drone drone;
    private final List<Drone> drones;
    private final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private final MethodSupport methodSupport;


    public ElectionImpl(Drone drone, List<Drone> drones, MethodSupport methodSupport){
        this.drone = drone;
        this.drones = drones;
        this.methodSupport = methodSupport;
    }

    @Override
    public void sendElection(ElectionMessage electionMessage,  StreamObserver<ackMessage> streamObserver) {

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

        int currentBatteriaResidua = electionMessage.getBatteriaResidua();
        int currentIdMaster = electionMessage.getIdCurrentMaster();


        if (!drone.isInElection() || (drone.getId() <= currentIdMaster )){

            drone.setInForwarding(true);
            drone.setInElection(true);

            //SE L'ID È UGUALE SIGNIFICA CHE IL MESSAGGIO HA FATTO TUTTO IL GIRO DELL'ANELLO ED È LUI IL MASTER
            if (currentIdMaster == drone.getId()) {
                drone.setIsMaster(true);
                drone.setInDelivery(true);
                
                methodSupport.getDroneFromList(drone.getId()).setIsMaster(true);
                LOGGER.info("ELEZIONE FINITA, PARTE LA TRASMISSIONE DEL NUOVO MASTER CON ID: " + currentIdMaster);
                try {
                    electionCompleted(drone, currentIdMaster, drones);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                if (currentBatteriaResidua < drone.getBatteria()) {
                    methodSupport.getDroneFromList(currentIdMaster).setInDelivery(false);
                    forwardElection(drone, drone.getId(), drone.getBatteria());
                    //LOGGER.info("TROVATO DRONE CON BATTERIA MAGGIORE, LIBERO IL DRONE CHE ERA OCCUPATO");
                } else if (currentBatteriaResidua > drone.getBatteria()) {
                    methodSupport.getDroneFromList(currentIdMaster).setInDelivery(true);
                    forwardElection(drone, currentIdMaster, currentBatteriaResidua);
                    //LOGGER.info("TROVATO DRONE CON BATTERIA MINORE");
                } else {
                    if (currentIdMaster < drone.getId()) {
                        //LOGGER.info("ID DEL DRONE È PIÙ GRANDE DELL'ID CHE STA GIRANDO COME MASTER, LIBERO IL DRONE CHE ERA OCCUPATO");
                        methodSupport.getDroneFromList(currentIdMaster).setInDelivery(false);
                        forwardElection(drone, drone.getId(), drone.getBatteria());
                    } else if (currentIdMaster > drone.getId()) {
                        methodSupport.getDroneFromList(currentIdMaster).setInDelivery(true);
                        forwardElection(drone, currentIdMaster, currentBatteriaResidua);
                        //LOGGER.info("ID DEL DRONE È PIÙ PICCOLO DELL'ID CHE STA GIRANDO COME MASTER");
                    }
                }
            }
        }
        else{
            LOGGER.info("ELEZIONE GIÀ IN CORSO, NON SI PUÒ INDIRNE UN'ALTRA!");
        }
    }

    private void forwardElection(Drone drone, int updateIdMAster, int updatedBatteriaResidua){
        Drone successivo = methodSupport.takeDroneSuccessivo(drone);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            ElectionStub stub = ElectionGrpc.newStub(channel);

            ElectionMessage newElectionMessage = ElectionMessage
                    .newBuilder()
                    .setIdCurrentMaster(updateIdMAster)
                    .setBatteriaResidua(updatedBatteriaResidua)
                    .build();

            stub.sendElection(newElectionMessage, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    synchronized (drones) {
                        drones.remove(successivo);
                    }
                    forwardElection(drone, updateIdMAster, updatedBatteriaResidua);
                }

                @Override
                public void onCompleted() {
                    channel.shutdown();
                    drone.setInForwarding(false);
                }
            });
            try {
                channel.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void electionCompleted(Drone drone, int newId, List<Drone> drones) throws InterruptedException {
        Drone successivo = methodSupport.takeDroneSuccessivo(drone);
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
                    synchronized (drones){
                        drones.remove(successivo);
                    }
                    try {
                        electionCompleted(drone, newId, drones);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
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
}
