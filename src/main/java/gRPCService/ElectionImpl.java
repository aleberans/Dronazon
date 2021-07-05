package gRPCService;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.ElectionGrpc.*;
import com.example.grpc.Message;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ElectionImpl extends ElectionImplBase {

    private final Drone drone;
    private final List<Drone> drones;
    private final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private final MqttClient client;
    private final QueueOrdini queueOrdini = new QueueOrdini();
    private final Object sync;
    private final Object newMasterSync = new Object();


    public ElectionImpl(Drone drone, List<Drone> drones, Object sync, MqttClient client){
        this.drone = drone;
        this.drones = drones;
        this.sync = sync;
        this.client= client;
    }

    @Override
    public void sendElection(ElectionMessage electionMessage,  StreamObserver<ackMessage> streamObserver) {

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

        //IL DRONE CHE RICEVE IL MESSAGGIO DI ELEZIONE SI METTE COME OCCUPATO E NON PUO USCIRE
        //SI LIBERA QUANDO L'ELEZIONE È FINITA E HA MANDATO LE INFO AGGIORNATE AL NUOVO MASTER
        drone.setInForwarding(true);

        int currentBatteriaResidua = electionMessage.getBatteriaResidua();
        int currentIdMaster = electionMessage.getIdCurrentMaster();


        if (currentBatteriaResidua < drone.getBatteria()) {
            forwardElection(drone, drone.getId(), drone.getBatteria(), drones);
            LOGGER.info("TROVATO DRONE CON BATTERIA MAGGIORE");
        }
        else if (currentBatteriaResidua > drone.getBatteria()) {
            forwardElection(drone, currentIdMaster, currentBatteriaResidua, drones);
            LOGGER.info("TROVATO DRONE CON BATTERIA MINORE");
        }
        else {
            LOGGER.info("TROVATO DRONE CON BATTERIA UGUALE");
            if (currentIdMaster < drone.getId()) {
                LOGGER.info("ID DEL DRONE È PIÙ GRANDE DELL'ID CHE STA GIRANDO COME MASTER");
                forwardElection(drone, drone.getId(), drone.getBatteria(), drones);
            } else if (currentIdMaster > drone.getId()) {
                forwardElection(drone, currentIdMaster, currentBatteriaResidua, drones);
                LOGGER.info("ID DEL DRONE È PIÙ PICCOLO DELL'ID CHE STA GIRANDO COME MASTER");
            }
        }
        //SE L'ID È UGUALE SIGNIFICA CHE IL MESSAGGIO HA FATTO TUTTO IL GIRO DELL'ANELLO ED È LUI IL MASTER
        if (currentIdMaster == drone.getId()){
            drone.setIsMaster(true);
            for (Drone d: drones){
                MethodSupport.getDroneFromList(d.getId(), drones).setConsegnaAssegnata(true);
            }
            MethodSupport.getDroneFromList(drone.getId(), drones).setConsegnaAssegnata(false);
            MethodSupport.getDroneFromList(drone.getId(), drones).setIsMaster(true);

            try {
                electionCompleted(drone, currentIdMaster, drones);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);
            SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
            sendConsegnaThread.start();
            SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones);
            sendStatisticToServer.start();
            LOGGER.info("ELEZIONE FINITA, PARTE LA TRASMISSIONE DEL NUOVO MASTER CON ID: " + currentIdMaster);
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
                    asynchronousSendConsegna(drones, drone);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class SendStatisticToServer extends Thread{

        private final List<Drone> drones;

        public SendStatisticToServer(List<Drone> drones){
            this.drones = drones;
        }

        @Override
        public void run(){
            while(true){
                ServerMethods.sendStatistics(drones);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void forwardElection(Drone drone, int updateIdMAster, int updatedBatteriaResidua, List<Drone> drones){

        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);
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
                    forwardElection(drone, updateIdMAster, updatedBatteriaResidua,drones);
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

    private void electionCompleted(Drone drone, int newId, List<Drone> drones) throws InterruptedException {

        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);



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

    public void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
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
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaAssegnata(true);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            if (queueOrdini.size() == 0){
                synchronized (queueOrdini) {
                    queueOrdini.notify();
                }
            }

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
                            drones.remove(MethodSupport.takeDroneSuccessivo(d, drones));
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

        while (!MethodSupport.thereIsDroneLibero(drones)) {
            LOGGER.info("IN WAIT");
            synchronized (sync) {
                sync.wait();
            }
        }

        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        //TOLGO IL MASTER SE HA MENO DEL 15% PERCHE DEVE USCIRE
        droni.removeIf(d -> (d.getIsMaster() && d.getBatteria() < 15));

        LOGGER.info("STATO DRONI DISPONIBILI: " + droni + "\n" +
                "NUMERO DRONI DISPONIBILE DOPO CONTROLLO: " + droni.stream().filter(d -> !d.consegnaAssegnata()).count());
        return droni.stream().filter(d -> !d.consegnaAssegnata())
                .min(Comparator.comparing(dr -> dr.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }
}
