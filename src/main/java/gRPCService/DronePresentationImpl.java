package gRPCService;

import REST.beans.Drone;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class DronePresentationImpl extends DronePresentationImplBase{

    private final List<Drone> drones;
    private final Logger LOGGER = Logger.getLogger(DronePresentationImpl .class.getSimpleName());
    private final Drone currentDrone;

    public DronePresentationImpl(List<Drone> drones, Drone currentDrone){
        this.drones = drones;
        this.currentDrone = currentDrone;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<isInElection> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        isInElection message;
        if (currentDrone.isInElection())
            message = isInElection.newBuilder().setInElection(true).build();
        else
            message = isInElection.newBuilder().setInElection(false).build();

        synchronized (drones){
            drones.add(drone);
            //Riordino la lista dopo aver aggiunto il drone che si è inserito
            drones.sort(Comparator.comparingInt(Drone::getId));
        }

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
