package gRPCService;
import DronazonPackage.DroneClient;
import REST.beans.Drone;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.security.acl.LastOwnerException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class DronePresentationImpl extends DronePresentationImplBase{

    private final List<Drone> drones;
    private final Object sync;
    private final Logger LOGGER = Logger.getLogger(DronePresentationImpl .class.getSimpleName());

    public DronePresentationImpl(List<Drone> drones, Object sync){
        this.drones = drones;
        this.sync = sync;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        synchronized (drones) {
            drones.add(drone);
            //Riordino la lista dopo aver aggiunto il drone che si è inserito
            drones.sort(Comparator.comparingInt(Drone::getId));
            synchronized (sync){
                LOGGER.info("DRONE AGGIUNTO SVEGLIA SU SYNC");
                sync.notifyAll();
            }
        }
        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
