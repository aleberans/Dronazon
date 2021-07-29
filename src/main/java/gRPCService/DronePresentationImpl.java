package gRPCService;
import REST.beans.Drone;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class DronePresentationImpl extends DronePresentationImplBase{

    private final List<Drone> drones;

    public DronePresentationImpl(List<Drone> drones){
        this.drones = drones;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        synchronized (drones) {
            drones.add(drone);
            //Riordino la lista dopo aver aggiunto il drone che si è inserito
            drones.sort(Comparator.comparingInt(Drone::getId));
        }


        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
