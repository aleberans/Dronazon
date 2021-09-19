package Support;

import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import com.example.grpc.*;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AsynchronousMedthods {

    private static final String LOCALHOST = "localhost";
    private static final Logger LOGGER = Logger.getLogger(AsynchronousMedthods.class.getSimpleName());
    private final MethodSupport methodSupport;
    private final List<Drone> drones;


    public AsynchronousMedthods(MethodSupport methodSupport, List<Drone> drones){
        this.methodSupport = methodSupport;
        this.drones = drones;
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        LogFormatterBlue formatter = new LogFormatterBlue();
        handler.setFormatter(formatter);
        LOGGER.addHandler(handler);
    }

    public void asynchronousSendInfoAggiornateToNewMaster(Drone drone){
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();

            SendUpdatedInfoToMasterGrpc.SendUpdatedInfoToMasterStub stub = SendUpdatedInfoToMasterGrpc.newStub(channel);

            Message.Info.Posizione newPosizione = Message.Info.Posizione.newBuilder().setX(drone.getPosizionePartenza().x).setY(drone.getPosizionePartenza().y).build();

            Message.Info info = Message.Info.newBuilder().setId(drone.getId()).setPosizione(newPosizione).setBatteria(drone.getBatteria()).build();

            stub.updatedInfo(info, new StreamObserver<Message.ackMessage>() {
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

    public void asynchronousPingAlive(Drone drone) throws InterruptedException {

        Drone successivo = methodSupport.takeDroneSuccessivo(drone);

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
                    if (drone.getDroneMaster() == successivo && !drone.isInElection()){
                        synchronized (drones){
                            drones.remove(successivo);
                        }
                        LOGGER.info("ELEZIONE INDETTA TRAMITE PING");
                        asynchronousStartElection(drone);
                    }
                    else
                        synchronized (drones) {
                            drones.remove(successivo);
                        }
                    asynchronousPingAlive(drone);
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

    public void asynchronousStartElection(Drone drone) throws InterruptedException {
        Drone successivo = methodSupport.takeDroneSuccessivo(drone);
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
                    try {
                        asynchronousStartElection(drone);
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
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void asynchronousSendPositionToMaster(Point posizione, Drone master, Drone drone){

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+master.getPortaAscolto()).usePlaintext().build();
            SendPositionToDroneMasterGrpc.SendPositionToDroneMasterStub stub = SendPositionToDroneMasterGrpc.newStub(channel);

            Message.SendPositionToMaster.Posizione pos = Message.SendPositionToMaster.Posizione.newBuilder().setX(posizione.x).setY(posizione.y).build();

            Message.SendPositionToMaster position = Message.SendPositionToMaster.newBuilder().setPos(pos).setId(drone.getId()).build();

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

    public void rechargeBattery(Drone drone){
        synchronized (drones) {
            for (Drone d : drones) {
                Context.current().fork().run(() -> {
                    final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":" + d.getPortaAscolto()).usePlaintext().build();

                    RechargeGrpc.RechargeStub stub = RechargeGrpc.newStub(channel);
                    Date date = new Date();
                    Timestamp ts = new Timestamp(date.getTime());
                    Message.MessageRecharge rec = Message.MessageRecharge.newBuilder()
                            .setName("RechargingStation")
                            .setId(drone.getId())
                            .setTimestamp(ts.toString())
                            .build();

                    stub.checkForRecharge(rec, new StreamObserver<Message.ackMessage>() {
                        @Override
                        public void onNext(Message.ackMessage value) {

                        }

                        @Override
                        public void onError(Throwable t) {
                        /*LOGGER.info("Error" + t.getMessage());
                        LOGGER.info("Error" + t.getCause());
                        LOGGER.info("Error" + t.getLocalizedMessage());
                        LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));*/
                            channel.shutdown();
                            synchronized (drones) {
                                drones.remove(d);
                            }
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
        }
    }

    public void asynchronousSendOkAfterCompleteRecharge(DroneRechargingQueue droneRechargingQueue, Drone drone){
        List<Drone> droniACuiMandareOk = droneRechargingQueue.takeDronesFromQueueInDrones();

        for (Drone d: droniACuiMandareOk){
            LOGGER.info("DRONI A CUI BISOGNA MANDARE IL MESSAGGIO CON OK: " + d.getId());
            Context.current().fork().run( () -> {
                final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+d.getPortaAscolto()).usePlaintext().build();

                AnswerRechargeGrpc.AnswerRechargeStub stub = AnswerRechargeGrpc.newStub(channel);
                Message.Answer answer = Message.Answer.newBuilder().setAnswer("ok").setId(drone.getId()).build();

                stub.okRecharge(answer, new StreamObserver<Message.ackMessage>() {
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


    }

    public void asynchronousAnswerToRequestOfRecharge(Drone drone, Drone droneCheRisponde){
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+drone.getPortaAscolto()).usePlaintext().build();

            AnswerRechargeGrpc.AnswerRechargeStub stub = AnswerRechargeGrpc.newStub(channel);
            Message.Answer answer = Message.Answer.newBuilder().setAnswer("ok").setId(droneCheRisponde.getId()).build();

            stub.okRecharge(answer, new StreamObserver<Message.ackMessage>() {
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

    public void asynchronousSendDroneInformation(Drone drone) {

        //trovo la lista di droni a cui mandare il messaggio escludendo il drone che chiama il metodo asynchronousSendDroneInformation
        Drone d = methodSupport.findDrone(drone);
        Predicate<Drone> byId = dr -> dr.getId() != d.getId();
        List<Drone> pulito = drones.stream().filter(byId).collect(Collectors.toList());

        //mando a tutti le informazioni dei parametri del drone
        for (Drone dron: pulito){
            Context.current().fork().run( () -> {
                final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":" + dron.getPortaAscolto()).usePlaintext().build();

                DronePresentationGrpc.DronePresentationStub stub = DronePresentationGrpc.newStub(channel);
                Message.SendInfoDrone info = Message.SendInfoDrone.newBuilder().setId(drone.getId()).setPortaAscolto(drone.getPortaAscolto())
                        .setIndirizzoDrone(drone.getIndirizzoIpDrone()).build();

                stub.presentation(info, new StreamObserver<Message.isInElection>() {
                    @Override
                    public void onNext(Message.isInElection value) {
                        methodSupport.takeDroneFromList(dron).setInElection(value.getInElection());
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

    public void asynchronousReceiveWhoIsMaster(Drone drone) {
        Drone succ = methodSupport.takeDroneSuccessivo(drone);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+ succ.getPortaAscolto()).usePlaintext().build();

            ReceiveWhoIsMasterGrpc.ReceiveWhoIsMasterStub stub = ReceiveWhoIsMasterGrpc.newStub(channel);

            Message.WhoMaster info = Message.WhoMaster.newBuilder().build();
            stub.master(info, new StreamObserver<Message.WhoIsMaster>() {
                @Override
                public void onNext(Message.WhoIsMaster value) {
                    drone.setDroneMaster(methodSupport.takeDroneFromId(value.getIdMaster()));
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

    public void asynchronousSetDroneInRechargingTrue(Drone drone, Drone master) {
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+master.getPortaAscolto()).usePlaintext().build();

            SendInRechargingGrpc.SendInRechargingStub stub = SendInRechargingGrpc.newStub(channel);
            Message.Recharging info = Message.Recharging.newBuilder().setId(drone.getId()).setRecharging(true).build();

            stub.inRecharging(info, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage ackMessage) {

                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    t.printStackTrace();
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

    public void asynchronousSetDroneInRechargingFalse(Drone drone, Drone master) {
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+master.getPortaAscolto()).usePlaintext().build();

            SendInRechargingGrpc.SendInRechargingStub stub = SendInRechargingGrpc.newStub(channel);
            Message.Recharging info = Message.Recharging.newBuilder().setId(drone.getId()).setRecharging(false).build();

            stub.inRecharging(info, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage ackMessage) {

                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("Error" + t.getMessage());
                    LOGGER.info("Error" + t.getCause());
                    LOGGER.info("Error" + t.getLocalizedMessage());
                    LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                    t.printStackTrace();
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

}
