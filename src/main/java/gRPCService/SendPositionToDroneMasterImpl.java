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
    private static MethodSupport methodSupport = null;

    public SendPositionToDroneMasterImpl(List<Drone> drones){
        this.drones = drones;
        methodSupport = new MethodSupport(drones);
    }

    @Override
    public void sendPosition(SendPositionToMaster info, StreamObserver<ackMessage> streamObserver){
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
        synchronized (drones) {
            updatePositionDrone(info.getId(), new Point(info.getPos().getX(), info.getPos().getY()));
        }
    }

    public static void updatePositionDrone(int id, Point position){
        methodSupport.findDrone(methodSupport.takeDroneFromId(id)).setPosizionePartenza(position);
    }
}
