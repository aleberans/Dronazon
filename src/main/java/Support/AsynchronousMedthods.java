package Support;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import com.example.grpc.*;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AsynchronousMedthods {

    private static final String LOCALHOST = "localhost";
    private static final Logger LOGGER = Logger.getLogger(AsynchronousMedthods.class.getSimpleName());
    private static MethodSupport methodSupport;
    private final List<Drone> drones;

    public AsynchronousMedthods(List<Drone> drones){
        this.drones = drones;
        methodSupport = new MethodSupport(drones);
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
                    if (drone.getDroneMaster() == successivo){
                        LOGGER.info("ELEZIONE INDETTA TRAMITE PING");
                        drones.remove(successivo);
                        drone.setInElection(true);
                        methodSupport.getDroneFromList(drone.getId()).setInElection(true);
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

    public void asynchronousStartElection(Drone drone){
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
                        drone.setInElection(true);
                        methodSupport.getDroneFromList(drone.getId()).setInElection(true);
                    }
                    asynchronousStartElection(drone);
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

    public void asynchronousSendConsegna( Drone drone, QueueOrdini queueOrdini, Object sync) throws InterruptedException {
        Drone d = methodSupport.takeDroneFromList(drone);
        Ordine ordine = queueOrdini.consume();

        Drone successivo = methodSupport.takeDroneSuccessivo(d);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();
            SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

            Message.Consegna.Posizione posizioneRitiro = Message.Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoRitiro().x)
                    .setY(ordine.getPuntoRitiro().y)
                    .build();

            Message.Consegna.Posizione posizioneConsegna = Message.Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoConsegna().x)
                    .setY(ordine.getPuntoConsegna().y)
                    .build();

            Drone droneACuiConsegnare = null;
            try {
                droneACuiConsegnare = cercaDroneCheConsegna(ordine);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Message.Consegna consegna = Message.Consegna.newBuilder()
                    .setIdConsegna(ordine.getId())
                    .setPuntoRitiro(posizioneRitiro)
                    .setPuntoConsegna(posizioneConsegna)
                    .setIdDrone(droneACuiConsegnare.getId())
                    .build();

            //aggiorno la lista mettendo il drone che deve ricevere la consegna come occupato
            synchronized (drones) {
                drones.get(drones.indexOf(methodSupport.findDrone(droneACuiConsegnare))).setConsegnaAssegnata(true);
            }

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                    //LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        LOGGER.info("DURANTE L'INVIO DELL'ORDINE IL SUCCESSIVO È MORTO, LO ELIMINO E RIPROVO MANDANDO LA CONSEGNA AL SUCCESSIVO DEL SUCCESSIVO");
                        channel.shutdownNow();
                        synchronized (drones) {
                            drones.remove(methodSupport.takeDroneSuccessivo(d));
                        }
                        synchronized (sync){
                            LOGGER.info("DRONI PRIMA DEL WHILE: " + drones);
                            while (!methodSupport.thereIsDroneLibero()) {
                                LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                                sync.wait();
                                LOGGER.info("SVEGLIATO SU SYNC");
                            }
                        }
                        asynchronousSendConsegna(d, queueOrdini, sync);
                    } catch (InterruptedException e) {
                        try {
                            e.printStackTrace();
                            LOGGER.info("Error" + t.getMessage());
                            LOGGER.info("Error" + t.getCause());
                            LOGGER.info("Error" + t.getLocalizedMessage());
                            LOGGER.info("Error" + Arrays.toString(t.getStackTrace()));
                            channel.awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                        }
                    }
                }
                public void onCompleted() {
                    channel.shutdownNow();
                }
            });
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public Drone cercaDroneCheConsegna(Ordine ordine) throws InterruptedException {
        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        //TOLGO IL MASTER SE HA MENO DEL 15% PERCHE DEVE USCIRE
        droni.removeIf(d -> (d.getIsMaster() && d.getBatteria() < 20));

        //LOGGER.info("DRONI SENZA CONSEGNA: " + stampa(drones));
        return droni.stream().filter(d -> !d.consegnaAssegnata())
                .min(Comparator.comparing(dr -> dr.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }
}
