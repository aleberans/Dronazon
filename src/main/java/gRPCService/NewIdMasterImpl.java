package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.Message;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.MqttClient;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NewIdMasterImpl extends NewIdMasterGrpc.NewIdMasterImplBase {

    private final List<Drone> drones;
    private final Drone drone;
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private final Object sync;
    private final QueueOrdini queueOrdini = new QueueOrdini();
    private final MqttClient client;
    private final Object election;
    private final ServerMethods serverMethods;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;


    public NewIdMasterImpl(List<Drone> drones, Drone drone, Object sync, MqttClient client,
                           Object election, MethodSupport methodSupport, ServerMethods serverMethods,
                           AsynchronousMedthods asynchronousMedthods){
        this.drone = drone;
        this.drones = drones;
        this.sync = sync;
        this.client = client;
        this.election = election;
        this.methodSupport = methodSupport;
        this.serverMethods = serverMethods;
        this.asynchronousMedthods = asynchronousMedthods;
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

        drone.setDroneMaster(methodSupport.takeDroneFromId(idMaster.getIdNewMaster()));
        drone.setInForwarding(true);
        drone.setInElection(false);

        synchronized (election){
                //LOGGER.info("TUTTI I DRONI SONO FUORI DALL'ELEZIONE, POSSONO ENTRARE NUOVI DRONI");
                election.notifyAll();
        }

        if (idMaster.getIdNewMaster() != drone.getId()) {
            LOGGER.info("ID MASTER DOPO ELEZIONE: " + drone.getDroneMaster().getId());
            forwardNewIdMaster(idMaster);
            asynchronousMedthods.asynchronousSendInfoAggiornateToNewMaster(drone);
        }
        else{
            //LOGGER.info("IL MESSAGGIO CON IL NUOVO MASTER È TORNATO AL MASTER");
            drone.setInDelivery(false);
            drone.setInForwarding(false);
            //LOGGER.info("ANELLO NON PIU IN ELEZIONE, POSSONO ENTRARE NUOVI DRONI");

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
                        while (methodSupport.takeFreeDrone().size() == 0) {
                            LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI\n");
                            sync.wait();
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
                synchronized (sync){
                    while(droneACuiConsegnare == null){
                        LOGGER.info("NON HO TROVATO DRONI, VADO IN ATTESA...");
                        sync.wait();
                        droneACuiConsegnare = cercaDroneCheConsegna(ordine);
                    }
                }
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

            methodSupport.takeDroneFromList(droneACuiConsegnare).setConsegnaAssegnata(true);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            stub.sendConsegna(consegna, new StreamObserver<Message.ackMessage>() {
                @Override
                public void onNext(Message.ackMessage value) {
                    //LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.info("DURANTE L'INVIO DELL'ORDINE IL SUCCESSIVO È MORTO, LO ELIMINO E RIPROVO MANDANDO LA CONSEGNA AL SUCCESSIVO DEL SUCCESSIVO");
                    channel.shutdownNow();
                    synchronized (drones) {
                        drones.remove(methodSupport.takeDroneSuccessivo(d));
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

        //scarto i droni che hanno una posizione nulla. Significa che non sono piu attivi
        synchronized (drones) {
                droni = droni.stream().filter(d -> d.getPosizionePartenza() != null).collect(Collectors.toList());
        }

        return droni.stream()
                .filter(d -> !d.isInRecharging())
                .filter(d -> !d.consegnaAssegnata())
                .min(Comparator.comparing(dr -> dr.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }

    public String stampaInfo(List<Drone> droni){
        StringBuilder info = new StringBuilder();
        for (Drone drone: droni){
            info.append("\nID: ").append(drone.getId()).append(" ConsegnaAssegnata: ").append(drone.consegnaAssegnata()).append("\n");
        }
        return info.toString();
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
                    serverMethods.sendStatistics();
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
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
