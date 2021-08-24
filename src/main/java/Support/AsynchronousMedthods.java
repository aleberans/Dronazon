package Support;

import REST.beans.Drone;
import com.example.grpc.*;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AsynchronousMedthods {

    private static final String LOCALHOST = "localhost";
    private static final Logger LOGGER = Logger.getLogger(AsynchronousMedthods.class.getSimpleName());
    private final MethodSupport methodSupport;

    public AsynchronousMedthods(MethodSupport methodSupport){
        this.methodSupport = methodSupport;
    }


    public void asynchronousPingAlive(Drone drone, List<Drone> drones) throws InterruptedException {

        Drone successivo = methodSupport.takeDroneSuccessivo(drone, drones);

        final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+successivo.getPortaAscolto()).usePlaintext().build();
        PingAliveGrpc.PingAliveStub stub = PingAliveGrpc.newStub(channel);

        Message.PingMessage pingMessage = Message.PingMessage.newBuilder().build();

        stub.ping(pingMessage, new StreamObserver<Message.PingMessage>() {
            @Override
            public void onNext(Message.PingMessage value) {
            }

            @Override
            public void onError(Throwable t) {
                try {
                    channel.shutdown();
                    if (drone.getDroneMaster() == successivo){
                        LOGGER.info("ELEZIONE INDETTA TRAMITE PING");
                        synchronized (drones){
                            drones.remove(successivo);
                        }
                        asynchronousStartElection(drones, drone);
                    }
                    else
                        synchronized (drones) {
                            drones.remove(successivo);
                        }
                    asynchronousPingAlive(drone, drones);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCompleted() {
                channel.shutdown();
            }
        });
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    public void asynchronousStartElection(List<Drone> drones, Drone drone){
        Drone successivo = methodSupport.takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+successivo.getPortaAscolto()).usePlaintext().build();

            ElectionGrpc.ElectionStub stub = ElectionGrpc.newStub(channel);

            Message.ElectionMessage electionMessage = Message.ElectionMessage
                    .newBuilder()
                    .setIdCurrentMaster(drone.getId())
                    .setBatteriaResidua(drone.getBatteria())
                    .build();

            stub.sendElection(electionMessage, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {

                }
                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    synchronized (drones) {
                        drones.remove(successivo);
                    }
                    asynchronousStartElection(drones, drone);
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

    public void asynchronousSendPositionToMaster(int id, Point posizione, Drone master) {

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+master.getPortaAscolto()).usePlaintext().build();
            SendPositionToDroneMasterGrpc.SendPositionToDroneMasterStub stub = SendPositionToDroneMasterGrpc.newStub(channel);

            Message.SendPositionToMaster.Posizione pos = Message.SendPositionToMaster.Posizione.newBuilder().setX(posizione.x).setY(posizione.y).build();

            Message.SendPositionToMaster position = Message.SendPositionToMaster.newBuilder().setPos(pos).setId(id).build();

            stub.sendPosition(position, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                }

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

    public void asynchronousSendDroneInformation(Drone drone, List<Drone> drones) {

        //trovo la lista di droni a cui mandare il messaggio escludendo il drone che chiama il metodo asynchronousSendDroneInformation
        Drone d = methodSupport.findDrone(drones, drone);
        Predicate<Drone> byId = dr -> dr.getId() != d.getId();
        List<Drone> pulito = drones.stream().filter(byId).collect(Collectors.toList());

        //mando a tutti le informazioni dei parametri del drone
        for (Drone dron: pulito){
            Context.current().fork().run( () -> {
                final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":" + dron.getPortaAscolto()).usePlaintext().build();

                DronePresentationGrpc.DronePresentationStub stub = DronePresentationGrpc.newStub(channel);


                Message.SendInfoDrone info = Message.SendInfoDrone.newBuilder().setId(drone.getId()).setPortaAscolto(drone.getPortaAscolto())
                        .setIndirizzoDrone(drone.getIndirizzoIpDrone()).build();

                stub.presentation(info, new StreamObserver<Message.ackMessage>() {
                    @Override
                    public void onNext(Message.ackMessage value) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOGGER.info("Error" + t.getMessage());
                        LOGGER.info("Error" + t.getCause());
                        LOGGER.info("Error" + t.getLocalizedMessage());
                        LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    }

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
    }

    public void asynchronousReceiveWhoIsMaster(List<Drone> drones, Drone drone) {
        Drone succ = methodSupport.takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+ succ.getPortaAscolto()).usePlaintext().build();

            ReceiveWhoIsMasterGrpc.ReceiveWhoIsMasterStub stub = ReceiveWhoIsMasterGrpc.newStub(channel);

            Message.WhoMaster info = Message.WhoMaster.newBuilder().build();
            stub.master(info, new StreamObserver<Message.WhoIsMaster>() {
                @Override
                public void onNext(Message.WhoIsMaster value) {
                    drone.setDroneMaster(methodSupport.takeDroneFromId(drones, value.getIdMaster()));
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    t.printStackTrace();
                }

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
}
