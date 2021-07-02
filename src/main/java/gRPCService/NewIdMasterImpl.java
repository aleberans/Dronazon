package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.NewIdMasterGrpc;
import com.example.grpc.SendPositionToDroneMasterGrpc;
import com.example.grpc.SendPositionToDroneMasterGrpc.*;
import com.example.grpc.SendUpdatedInfoToMasterGrpc;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NewIdMasterImpl extends NewIdMasterGrpc.NewIdMasterImplBase {

    private static List<Drone> drones;
    private static Drone drone;
    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());

    public NewIdMasterImpl(List<Drone> drones, Drone drone){
        this.drone = drone;
        this.drones = drones;
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
    public void sendNewIdMaster(IdMaster idMaster, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        drone.setDroneMaster(takeDroneFromId(drones, idMaster.getIdNewMaster()));
        if (idMaster.getIdNewMaster() != drone.getId()){
            LOGGER.info("IL MASTER PRIMA DI IMPOSTARLO È: " + drone.getDroneMaster().getId() + "\n"
                        + ", ORA SETTO IL NUOVO MASTER CHE HA ID: " + takeDroneFromId(drones, idMaster.getIdNewMaster()).getId());

            LOGGER.info("ID MASTER DOPO SETTAGGIO: " + drone.getDroneMaster().getId());
            forwardNewIdMaster(idMaster);
            asynchronousSendPositionToMaster(drone.getId(),
                    drones.get(drones.indexOf(findDrone(drones, drone))).getPosizionePartenza(),
                    drone.getDroneMaster());

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

    private void forwardNewIdMaster(IdMaster idMaster){
        Drone successivo = takeDroneSuccessivo(drone, drones);

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
                    drones.remove(successivo);
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

    private static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        int pos = drones.indexOf(findDrone(drones, drone));
        return drones.get( (pos+1)%drones.size());
    }

    private static Drone findDrone(List<Drone> drones, Drone drone){

        for (Drone d: drones){
            if (d.getId() == drone.getId())
                return d;
        }
        return drone;
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        for (Drone d: drones){
            if (d.getId()==id)
                return d;
        }
        return null;
    }

    /**
     * mando la posizione al drone master
     */
    private static void asynchronousSendPositionToMaster(int id, Point posizione, Drone master) {

        Context.current().fork().run( () -> {
            final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" +master.getPortaAscolto()).usePlaintext().build();
            SendPositionToDroneMasterStub stub = SendPositionToDroneMasterGrpc.newStub(channel);

            SendPositionToMaster.Posizione pos = SendPositionToMaster.Posizione.newBuilder().setX(posizione.x).setY(posizione.y).build();

            SendPositionToMaster position = SendPositionToMaster.newBuilder().setPos(pos).setId(id).build();

            stub.sendPosition(position, new StreamObserver<ackMessage>() {
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
