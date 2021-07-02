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
import javafx.util.Pair;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AsynchronousMedthods {

    private static final String LOCALHOST = "localhost";
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());


    public static void asynchronousPingAlive(Drone drone, List<Drone> drones) throws InterruptedException {

        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);

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
                        asynchronousStartElection(drones, drone);
                    }
                    else
                        drones.remove(successivo);
                    LOGGER.info("IL DRONE SUCCESSIVO È MORTO, CI SI È ACCORTI TRAMITE PING" + drones);
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

    public static void asynchronousStartElection(List<Drone> drones, Drone drone){
        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST+":"+successivo.getPortaAscolto()).usePlaintext().build();

            ElectionGrpc.ElectionStub stub = ElectionGrpc.newStub(channel);

            Message.ElectionMessage electionMessage = Message.ElectionMessage.newBuilder()
                    .setIdCurrentMaster(drone.getId()).build();

            stub.sendElection(electionMessage, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdownNow();
                    //drones.remove(successivo);
                    //asynchronousStartElection(drones, drone);
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

    public static void asynchronousSendPositionToMaster(int id, Point posizione, Drone master) {

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

    public static void asynchronousSendDroneInformation(Drone drone, List<Drone> drones) {

        //trovo la lista di droni a cui mandare il messaggio escludendo il drone che chiama il metodo asynchronousSendDroneInformation
        Drone d = MethodSupport.findDrone(drones, drone);
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

    public static void asynchronousSendWhoIsMaster(List<Drone> drones, Drone drone) {
        Drone succ = MethodSupport.takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(LOCALHOST + ":"+ succ.getPortaAscolto()).usePlaintext().build();

            SendWhoIsMasterGrpc.SendWhoIsMasterStub stub = SendWhoIsMasterGrpc.newStub(channel);

            Message.WhoMaster info = Message.WhoMaster.newBuilder().build();
            stub.master(info, new StreamObserver<Message.WhoIsMaster>() {
                @Override
                public void onNext(Message.WhoIsMaster value) {
                    drone.setDroneMaster(MethodSupport.takeDroneFromId(drones, value.getIdMaster()));
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

    public static void asynchronousSendConsegna(List<Drone> drones, Drone drone, QueueOrdini queueOrdini, Object sync) throws InterruptedException {
        Drone d = MethodSupport.takeDroneFromList(drone, drones);
        Ordine ordine = queueOrdini.consume();

        Drone successivo = MethodSupport.takeDroneSuccessivo(d, drones);
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
                droneACuiConsegnare = findDroneToConsegna(drones, ordine, sync);
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
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, droneACuiConsegnare))).setOccupato(true);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            synchronized (queueOrdini){
                if (queueOrdini.size() == 0)
                    //LOGGER.info("CODA COMPLETAMENTE SVUOTATA");
                    queueOrdini.notify();
            }
            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                    //LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        channel.shutdownNow();
                        /*LOGGER.info("LO STATO DELLA LISTA È: " + getAllIdDroni(drones) +
                                "\n IL DRONE CHE STA PROVANDO A FARE LA CONSEGNA È: " + d.getId() +
                                "\n IL DRONE SUCCESSIVO A LUI È: " + takeDroneSuccessivo(d, drones).getId());*/
                        drones.remove(MethodSupport.takeDroneSuccessivo(d, drones));
                        //LOGGER.info("STATO LISTA DOPO RIMOZIONE: " + getAllIdDroni(drones));
                        asynchronousSendConsegna(drones, d, queueOrdini, sync);
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

    public static Drone findDroneToConsegna(List<Drone> drones, Ordine ordine, Object sync) throws InterruptedException {

        Drone drone = null;
        ArrayList<Drone> lista = new ArrayList<>();
        ArrayList<Pair<Drone, Double>> coppieDistanza = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieBatteria = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieIdMaggiore = new ArrayList<>();
        int count2 = 0;
        int count = 0;

        if (!MethodSupport.thereIsDroneLibero(drones)){
            //LOGGER.info("IN WAIT");
            synchronized (sync){
                sync.wait();
            }
        }

        for (Drone d: drones){
            if (!d.isOccupato()){
                lista.add(d);
            }
        }

        //creo le varie liste
        for (Drone d: lista){
            coppieDistanza.add(new Pair<>(d, d.getPosizionePartenza().distance(ordine.getPuntoRitiro())));
            coppieBatteria.add(new Pair<>(d, d.getBatteria()));
            coppieIdMaggiore.add(new Pair<>(d, d.getId()));
        }

        Optional<Pair<Drone, Double>> droneMinDistance = coppieDistanza.stream()
                .min(Comparator.comparing(Pair::getValue));

        Optional<Pair<Drone, Integer>> droneMaxBatteria = coppieBatteria.stream()
                .max(Comparator.comparing(Pair::getValue));

        Optional<Pair<Drone, Integer>> droneMaxId = coppieIdMaggiore.stream()
                .max(Comparator.comparing(Pair::getValue));



        Pair<Drone, Double> droneDistanzaMinima = droneMinDistance.orElse(null);
        drone = droneDistanzaMinima.getKey();
        //LOGGER.info("SOLO UN DRONE CON DISTANZA MINIMA");
        return drone;
    }
}
