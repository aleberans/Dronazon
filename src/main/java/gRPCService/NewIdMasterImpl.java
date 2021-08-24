package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.Message;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.example.grpc.SendUpdatedInfoToMasterGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.MqttClient;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NewIdMasterImpl extends NewIdMasterGrpc.NewIdMasterImplBase {

    private static List<Drone> drones;
    private static Drone drone;
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private final Object sync;
    private final QueueOrdini queueOrdini = new QueueOrdini();
    private final MqttClient client;
    private final Object election;
    private final ServerMethods serverMethods;
    private final MethodSupport methodSupport;


    public NewIdMasterImpl(List<Drone> drones, Drone drone, Object sync, MqttClient client,
                           Object election, MethodSupport methodSupport, ServerMethods serverMethods){
        this.drone = drone;
        this.drones = drones;
        this.sync = sync;
        this.client = client;
        this.election = election;
        this.methodSupport = methodSupport;
        this.serverMethods = serverMethods;
    }

    /**
     * Imposto lato ricezione del messaggio come master il drone che ha l'id del messaggio che arriva.
     * Se arriva al drone che non è designato ad essere master imposto il nuovo master,
     * mando la posizione e la batteria residua al master e inoltro il messaggio al drone successivo che farà esattamente la stessa cosa.
     * Altrimenti significa che il messaggio è tornato al drone designato come nuovo master
     * devo aggiornare la sua lista dei droni attivi
     */
    @Override
    public void sendNewIdMaster(IdMaster idMaster, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        drone.setDroneMaster(methodSupport.takeDroneFromId(drones, idMaster.getIdNewMaster()));
        drone.setInForwarding(true);

        if (idMaster.getIdNewMaster() != drone.getId()) {
            LOGGER.info("ID MASTER DOPO ELEZIONE: " + drone.getDroneMaster().getId());
            forwardNewIdMaster(idMaster);
            /*AsynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                    MethodSupport.takeDroneFromList(drone, drones).getPosizionePartenza(),
                    drone.getDroneMaster());*/

            asynchronousSendInfoAggiornateToNewMaster(drone);
        }
        else{
            //LOGGER.info("IL MESSAGGIO CON IL NUOVO MASTER È TORNATO AL MASTER");
            /*synchronized (drones) {
                drones = drones.stream().filter(d -> d.getPosizionePartenza() != null).collect(Collectors.toList());
            }*/
            drone.setInDelivery(false);
            drone.setInElection(false);
            synchronized (drones){
                methodSupport.getDroneFromList(drone.getId(), drones).setInElection(false);
            }
            synchronized (election){
                if (methodSupport.allDronesFreeFromElection(drones)) {
                    LOGGER.info("TUTTI I DRONI FUORI DALL'ELEZIONE, NOTIFICA IN MODO CHE POSSA ENTRARE UN NUOVO DRONE");
                    election.notify();
                }
            }
            LOGGER.info("ANELLO NON PIU IN ELEZIONE, POSSONO ENTRARE NUOVI DRONI");
            MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);
            NewIdMasterImpl.SendConsegnaThread sendConsegnaThread = new NewIdMasterImpl.SendConsegnaThread(drones, drone);
            sendConsegnaThread.start();
            NewIdMasterImpl.SendStatisticToServer sendStatisticToServer = new NewIdMasterImpl.SendStatisticToServer(drones, queueOrdini, serverMethods);
            sendStatisticToServer.start();

            synchronized (sync){
                sync.notify();
            }
        }
    }

    class SendConsegnaThread extends Thread {

        private final List<Drone> drones;
        private final Drone drone;

        public SendConsegnaThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
        }

        @Override
        public void run(){
            while (true) {
                try {
                    synchronized (sync){
                        LOGGER.info("DRONI PRIMA DEL WHILE: " + drones);
                        while (!methodSupport.thereIsDroneLibero(drones)) {
                            LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                            sync.wait();
                            LOGGER.info("SVEGLIATO SU SYNC");
                        }
                    }
                    asynchronousSendConsegna(drones, drone);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
        Drone d = methodSupport.takeDroneFromList(drone, drones);
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
                droneACuiConsegnare = cercaDroneCheConsegna(drones, ordine);
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

            //synchronized (drones) {
                //drones.get(drones.indexOf(methodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaAssegnata(true);
                methodSupport.takeDroneFromList(droneACuiConsegnare, drones).setConsegnaAssegnata(true);
            //}

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
                            while (!methodSupport.thereIsDroneLibero(drones)) {
                                LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                                sync.wait();
                                LOGGER.info("SVEGLIATO SU SYNC");
                            }
                        }
                        asynchronousSendConsegna(drones, d);
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

    public Drone cercaDroneCheConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {
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

    public String stampa(List<Drone> drones){
        StringBuilder s = new StringBuilder();
        for (Drone d: drones){
            s.append("ID: ").append(d.getId()).append("\n");
            s.append("POSIZIONE: ").append(d.getPosizionePartenza()).append("\n");
        }
        return s.toString();
    }

    static class SendStatisticToServer extends Thread{

        private final List<Drone> drones;
        private final QueueOrdini queueOrdini;
        private final ServerMethods serverMethods;

        public SendStatisticToServer(List<Drone> drones, QueueOrdini queueOrdini, ServerMethods serverMethods){
            this.drones = drones;
            this.queueOrdini = queueOrdini;
            this.serverMethods = serverMethods;
        }

        @Override
        public void run(){
            while(true){
                if (queueOrdini.size() != 0) {
                    serverMethods.sendStatistics(drones);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void asynchronousSendInfoAggiornateToNewMaster(Drone drone){
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + drone.getDroneMaster().getPortaAscolto()).usePlaintext().build();

            SendUpdatedInfoToMasterGrpc.SendUpdatedInfoToMasterStub stub = SendUpdatedInfoToMasterGrpc.newStub(channel);

            Info.Posizione newPosizione = Info.Posizione.newBuilder().setX(drone.getPosizionePartenza().x).setY(drone.getPosizionePartenza().y).build();

            Info info = Info.newBuilder().setId(drone.getId()).setPosizione(newPosizione).setBatteria(drone.getBatteria()).build();

            stub.updatedInfo(info, new StreamObserver<ackMessage>() {
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
            try {
                channel.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void forwardNewIdMaster(IdMaster idMaster){

        Drone successivo = methodSupport.takeDroneSuccessivo(drone);

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            NewIdMasterGrpc.NewIdMasterStub stub = NewIdMasterGrpc.newStub(channel);

            stub.sendNewIdMaster(idMaster, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {

                }

                @Override
                public void onError(Throwable t) {
                    channel.shutdown();
                    synchronized (drones) {
                        drones.remove(successivo);
                    }
                    forwardNewIdMaster(idMaster);
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

}
