package gRPCService;

import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendPositionToDroneMasterGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.List;

public class SendPositionToDroneMasterImpl extends SendPositionToDroneMasterGrpc.SendPositionToDroneMasterImplBase {

    private List<Drone> drones;

    public SendPositionToDroneMasterImpl(List<Drone> drones){
        this.drones = drones;
    }

    @Override
    public void sendPosition(SendPositionToMaster info, StreamObserver<ackMessage> streamObserver){

        Point pos = new Point(info.getPos().getX(), info.getPos().getY());
        updatePositionDrone(drones, info.getId(), pos);

        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

    public static void updatePositionDrone(List<Drone> drones, int id, Point position){
        Drone drone = takeDroneFromId(drones, id);
        Drone d = findDrone(drones, drone);
        d.setPosizionePartenza(position);
    }

    public static Drone findDrone(List<Drone> drones, Drone drone){

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
}
