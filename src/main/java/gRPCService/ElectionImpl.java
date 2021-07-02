package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import REST.beans.Statistic;
import com.example.grpc.ElectionGrpc;
import com.example.grpc.ElectionGrpc.*;
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
        this.sync = sync;
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
        else{
            electionCompleted(drone, currentIdMaster, drones);
            subTopic("dronazon/smartcity/orders/", client);
            SendConsegnaThread sendConsegnaThread = new SendConsegnaThread(drones, drone);
            sendConsegnaThread.start();
            drone.setIsMaster(true);
            SendStatisticToServer sendStatisticToServer = new SendStatisticToServer(drones);
            sendStatisticToServer.start();

        }

            LOGGER.info("ELEZIONE FINITA, PARTE LA TRASMISSIONE DEL NUOVO MASTER CON ID: " + currentIdMaster);
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

    public static Drone takeDroneFromList(Drone drone, List<Drone> drones){
        return drones.get(drones.indexOf(findDrone(drones, drone)));
    }

    private static boolean thereIsDroneLibero(List<Drone> drones){
        for(Drone d: drones){
            if (!d.isOccupato()) {
                //LOGGER.info("TROVATO DRONE");
                return true;
            }
        }
        return false;
    }

    private static Drone findDroneToConsegna(List<Drone> drones, Ordine ordine) throws InterruptedException {

        Drone drone = null;
        ArrayList<Drone> lista = new ArrayList<>();
        ArrayList<Pair<Drone, Double>> coppieDistanza = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieBatteria = new ArrayList<>();
        ArrayList<Pair<Drone, Integer>> coppieIdMaggiore = new ArrayList<>();
        int count2 = 0;
        int count = 0;

        if (!thereIsDroneLibero(drones)){
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

    private static void asynchronousSendConsegna(List<Drone> drones, Drone drone) throws InterruptedException {
        Drone d = takeDroneFromList(drone, drones);
        Ordine ordine = queueOrdini.consume();

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + takeDroneSuccessivo(d, drones).getPortaAscolto()).usePlaintext().build();
            SendConsegnaToDroneGrpc.SendConsegnaToDroneStub stub = SendConsegnaToDroneGrpc.newStub(channel);

            Consegna.Posizione posizioneRitiro = Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoRitiro().x)
                    .setY(ordine.getPuntoRitiro().y)
                    .build();

            Consegna.Posizione posizioneConsegna = Consegna.Posizione.newBuilder()
                    .setX(ordine.getPuntoConsegna().x)
                    .setY(ordine.getPuntoConsegna().y)
                    .build();

            Drone droneACuiConsegnare = null;
            try {
                droneACuiConsegnare = findDroneToConsegna(drones, ordine);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Consegna consegna = Consegna.newBuilder()
                    .setIdConsegna(ordine.getId())
                    .setPuntoRitiro(posizioneRitiro)
                    .setPuntoConsegna(posizioneConsegna)
                    .setIdDrone(droneACuiConsegnare.getId())
                    .build();

            //aggiorno la lista mettendo il drone che deve ricevere la consegna come occupato
            drones.get(drones.indexOf(findDrone(drones, droneACuiConsegnare))).setOccupato(true);

            //tolgo la consegna dalla coda delle consegne
            queueOrdini.remove(ordine);

            synchronized (queueOrdini){
                if (queueOrdini.size() == 0)
                    //LOGGER.info("CODA COMPLETAMENTE SVUOTATA");
                    queueOrdini.notify();
            }
            stub.sendConsegna(consegna, new StreamObserver<ackMessage>() {
                @Override
                public void onNext(ackMessage value) {
                    //LOGGER.info(value.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        channel.shutdownNow();
                        /*LOGGER.info("LO STATO DELLA LISTA È: " + getAllIdDroni(drones) +
                                "\n IL DRONE CHE STA PROVANDO A FARE LA CONSEGNA È: " + d.getId() +
                                "\n IL DRONE SUCCESSIVO A LUI È: " + takeDroneSuccessivo(d, drones).getId());*/
                        drones.remove(takeDroneSuccessivo(d, drones));
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

    static class SendStatisticToServer extends Thread{

        private final List<Drone> drones;

        public SendStatisticToServer(List<Drone> drones){
            this.drones = drones;
        }

        @Override
        public void run(){
            while(true){
                sendStatistics(drones);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String sendStatistics(List<Drone> drones){
        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        int mediaCountConsegne = 0;
        double mediaKmPercorsi = 0.0;
        int mediaInquinamento = 0;
        int mediaBatteriaResidua = 0;
        int countDroniAttivi = 0;

        mediaCountConsegne = drones.stream().map(Drone::getCountConsegne).reduce(0, Integer::sum);
        mediaBatteriaResidua = drones.stream().map(Drone::getBatteria).reduce(0, Integer::sum);
        mediaKmPercorsi = drones.stream().map(Drone::getKmPercorsiSingoloDrone).reduce(0.0, Double::sum);
        countDroniAttivi = (int) drones.stream().map(Drone::getId).count();

        Statistic statistic = new Statistic(ts.toString(),  mediaCountConsegne/countDroniAttivi,
                mediaKmPercorsi / countDroniAttivi,
                mediaInquinamento,
                mediaBatteriaResidua/countDroniAttivi);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        return "Output from Server .... \n" + response.getEntity(String.class);
    }


    private static void subTopic(String topic, MqttClient client) {
        int qos = 0;
        try {
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.connect();

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.info(clientId + " Connection lost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    String receivedMessage = new String(message.getPayload());
                    /*LOGGER.info(clientId +" Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "\n");*/

                    Ordine ordine = gson.fromJson(receivedMessage, Ordine.class);

                    queueOrdini.add(ordine);
                    //LOGGER.info("ordini:" + queueOrdini);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            //LOGGER.info(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            client.subscribe(topic,qos);
            LOGGER.info(clientId + " Subscribed to topics : " + topic);

        } catch (MqttException me) {
            LOGGER.info("reason " + me.getReasonCode());
            LOGGER.info("msg " + me.getMessage());
            LOGGER.info("loc " + me.getLocalizedMessage());
            LOGGER.info("cause " + me.getCause());
            LOGGER.info("excep " + me);
            me.printStackTrace();

        }

    }

    private void forwardElection(Drone drone, int updateIdMAster, List<Drone> drones){

        Drone successivo = takeDroneSuccessivo(drone, drones);
        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + successivo.getPortaAscolto()).usePlaintext().build();

            ElectionStub stub = ElectionGrpc.newStub(channel);

            ElectionMessage newElectionMessage = ElectionMessage.newBuilder()
                    .setIdCurrentMaster(updateIdMAster).build();

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
