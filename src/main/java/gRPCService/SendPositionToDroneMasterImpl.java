package gRPCService;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.SendPositionToDroneMasterGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.List;

public class SendPositionToDroneMasterImpl extends SendPositionToDroneMasterGrpc.SendPositionToDroneMasterImplBase {

    private final List<Drone> drones;
    private final MethodSupport methodSupport;

    public SendPositionToDroneMasterImpl(List<Drone> drones, MethodSupport methodSupport){
        this.drones = drones;
        this.methodSupport = methodSupport;
    }

    @Override
    public void sendPosition(SendPositionToMaster info, StreamObserver<ackMessage> streamObserver){
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
        synchronized (drones) {
            updatePositionDrone(drones, info.getId(), new Point(info.getPos().getX(), info.getPos().getY()));
        }
    }

    public void updatePositionDrone(List<Drone> drones, int id, Point position){
        methodSupport.findDrone(drones, methodSupport.takeDroneFromId(drones, id)).setPosizionePartenza(position);
    }
}
