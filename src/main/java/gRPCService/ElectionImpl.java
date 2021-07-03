package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import REST.beans.Statistic;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import Support.MqttMethods;
import Support.ServerMethods;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.ElectionGrpc.*;
import com.example.grpc.Message;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendConsegnaToDroneGrpc;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import javafx.util.Pair;
import org.eclipse.paho.client.mqttv3.*;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ElectionImpl extends ElectionImplBase {

    private final Drone drone;
    private final List<Drone> drones;
    private static final Logger LOGGER = Logger.getLogger(SendConsegnaToDroneImpl.class.getSimpleName());
    private static final String broker = "tcp://localhost:1883";
    private static final String clientId = MqttClient.generateClientId();
    private static MqttClient client = null;
    private static final QueueOrdini queueOrdini = new QueueOrdini();
    private static final Gson gson = new Gson();
    private static Object sync;

    static {
        try {
            client = new MqttClient(broker, clientId);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public ElectionImpl(Drone drone, List<Drone> drones, Object sync ){
        this.drone = drone;
        this.drones = drones;
        ElectionImpl.sync = sync;
    }

    @Override
    public void sendElection(ElectionMessage electionMessage,  StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

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
            electionCompleted(drone, currentIdMaster, drones);
            MqttMethods.subTopic("dronazon/smartcity/orders/", client, clientId, queueOrdini);
            SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
            sendConsegnaThread.start();
            drone.setIsMaster(true);
            SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones);
            sendStatisticToServer.start();
            LOGGER.info("ELEZIONE FINITA, PARTE LA TRASMISSIONE DEL NUOVO MASTER CON ID: " + currentIdMaster);
        }

    }

    static class SendConsegnaThread extends Thread {

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
                    drones.remove(successivo);
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

    private void electionCompleted(Drone drone, int newId, List<Drone> drones){
        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);

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

    public static Drone findDroneToConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {

        Drone drone = null;
        ArrayList<Drone> lista = new ArrayList<>();
        ArrayList<Pair<Drone, Double>> coppieDistanza = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieBatteria = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieIdMaggiore = new ArrayList<>();

        if (!MethodSupport.thereIsDroneLibero(drones)){
            //LOGGER.info("IN WAIT");
            synchronized (sync){
                sync.wait();
            }
        }

        for (Drone d: drones){
            if (d.consegnaNonAssegnata()){
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

    public static void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
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
                droneACuiConsegnare = test(drones, ordine);
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
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, droneACuiConsegnare))).setConsegnaNonAssegnata(false);

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

    public static Drone test(List<Drone> drones, Ordine ordine) throws InterruptedException {

        if (!MethodSupport.thereIsDroneLibero(drones)){
            LOGGER.info("IN WAIT");
            synchronized (sync){
                sync.wait();
            }
        }
        List<Drone> droni = new ArrayList<>(drones);

        droni.sort(Comparator.comparing(Drone::getBatteria)
                .thenComparing(Drone::getId));
        droni.sort(Collections.reverseOrder());

        //TOLGO IL MASTER SE HA MENO DEL 15% PERCHE DEVE USCIRE
        droni.removeIf(d -> d.getIsMaster() & d.getBatteria() < 15);

        return droni.stream().filter(Drone::consegnaNonAssegnata)
                .min(Comparator.comparing(drone -> drone.getPosizionePartenza().distance(ordine.getPuntoRitiro())))
                .orElse(null);
    }

}
