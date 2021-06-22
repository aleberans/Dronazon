package gRPCService;
import REST.beans.Drone;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.List;

public class DronePresentationImpl extends DronePresentationImplBase{

    private List<Drone> drones;

    public DronePresentationImpl(List<Drone> drones){
        this.drones = drones;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        ackMessage message = ackMessage.newBuilder().setMessage("Avvenuto invio delle informazioni").build();

        drones.add(drone);
        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
