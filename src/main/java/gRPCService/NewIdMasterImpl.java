package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import Support.*;
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
    private final MethodSupport methodSupport;

    public NewIdMasterImpl(List<Drone> drones, Drone drone, Object sync, MqttClient client, Object election){
        NewIdMasterImpl.drone = drone;
        NewIdMasterImpl.drones = drones;
        this.sync = sync;
        this.client = client;
        this.election = election;
        methodSupport = new MethodSupport(drones);
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
            drone.setInDelivery(false);
            drone.setInElection(false);

            methodSupport.getDroneFromList(drone.getId()).setInElection(false);
            synchronized (election){
                if (methodSupport.allDronesFreeFromElection()) {
                    LOGGER.info("TUTTI I DRONI FUORI DALL'ELEZIONE, NOTIFICA IN MODO CHE POSSA ENTRARE UN NUOVO DRONE");
                    election.notify();
                }
            }
            LOGGER.info("ANELLO NON PIU IN ELEZIONE, POSSONO ENTRARE NUOVI DRONI");
            MqttMethods.subTopic("dronazon/smartcity/orders/", client, queueOrdini);
            Threads.SendConsegnaThread sendConsegnaThread = new Threads.SendConsegnaThread(drones, drone, sync, queueOrdini);
            sendConsegnaThread.start();
            Threads.SendStatisticToServer sendStatisticToServer = new Threads.SendStatisticToServer(drones, queueOrdini);
            sendStatisticToServer.start();

            synchronized (sync){
                sync.notify();
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
