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

    public SendPositionToDroneMasterImpl(List<Drone> drones){
        this.drones = drones;
    }

    @Override
    public void sendPosition(SendPositionToMaster info, StreamObserver<ackMessage> streamObserver){
        updatePositionDrone(drones, info.getId(), new Point(info.getPos().getX(), info.getPos().getY()));

        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

    public static void updatePositionDrone(List<Drone> drones, int id, Point position){
        MethodSupport.findDrone(drones, MethodSupport.takeDroneFromId(drones, id)).setPosizionePartenza(position);
    }
}
