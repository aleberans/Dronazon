package gRPCService;
import REST.beans.Drone;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.util.List;

public class DronePresentationImpl extends DronePresentationImplBase{

    private List<Drone> drones;

    public DronePresentationImpl(List<Drone> drones){
        this.drones = drones;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        System.out.println(info);
        ackMessage message = ackMessage.newBuilder().setMessage("Avvenuta ricezione informazioni nuovo noodo\n").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
