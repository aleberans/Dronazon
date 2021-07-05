package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendUpdatedInfoToMasterGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NewIdMasterImpl extends NewIdMasterGrpc.NewIdMasterImplBase {

    private static List<Drone> drones;
    private static Drone drone;
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private final Object sync;

    public NewIdMasterImpl(List<Drone> drones, Drone drone, Object sync){
        this.drone = drone;
        this.drones = drones;
        this.sync = sync;
    }

    /**
     * Imposto lato ricezione del messaggio come master il drone che ha l'id del messaggio che arriva.
     * Se arriva al drone che non è designato ad essere master imposto il nuovo master,
     * mando la posizione e la batteria residura al master e inoltro il messaggio al drone successivo che farà esattamewnte la stessa cosa.
     * Altrimenti significa che il messaggio è tornato al drone designato come nuovo master
     * devo aggiornare la sua lista dei droni attivi
     * @param idMaster
     * @param streamObserver
     */
    @Override
    public void sendNewIdMaster(IdMaster idMaster, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        drone.setDroneMaster(MethodSupport.takeDroneFromId(drones, idMaster.getIdNewMaster()));
        if (idMaster.getIdNewMaster() != drone.getId()) {
            LOGGER.info("IL MASTER PRIMA DI IMPOSTARLO È: " + drone.getDroneMaster().getId() + "\n"
                    + ", ORA SETTO IL NUOVO MASTER CHE HA ID: " + MethodSupport.takeDroneFromId(drones, idMaster.getIdNewMaster()).getId());

            LOGGER.info("ID MASTER DOPO SETTAGGIO: " + drone.getDroneMaster().getId());
            forwardNewIdMaster(idMaster);
            AsynchronousMedthods.asynchronousSendPositionToMaster(drone.getId(),
                    MethodSupport.takeDroneFromList(drone, drones).getPosizionePartenza(),
                    drone.getDroneMaster());

            if (idMaster.getIdNewMaster() == drone.getId()) {
                LOGGER.info("IL MESSAGGIO CON IL NUOVO MASTER È TORNATO AL MASTER");
                drones = drones.stream().filter(d -> !d.consegnaAssegnata()).collect(Collectors.toList());

                synchronized (sync){
                    sync.notify();;
                }

            } else
                LOGGER.info("MESSAGGIO CON IL NUOVO MASTER INOLTRATO");

            asynchronousSendInfoAggiornateToNewMaster(drone);

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

        Drone successivo = MethodSupport.takeDroneSuccessivo(drone, drones);

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
