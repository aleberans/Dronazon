package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendUpdatedInfoToMasterGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.security.acl.LastOwnerException;
import java.util.List;
import java.util.logging.Logger;

public class SendUpdatedInfoToMasterImpl extends SendUpdatedInfoToMasterGrpc.SendUpdatedInfoToMasterImplBase {

    private List<Drone> drones;
    private Drone drone;

    public SendUpdatedInfoToMasterImpl(List<Drone> drones, Drone drone){
        this.drones = drones;
        this.drone = drone;
    }

    public void updatedInfo(Info info, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();


        Point pos = new Point(info.getPosizione().getX(), info.getPosizione().getY());

        Drone dr = takeDroneFromId(drones, drone.getId());
        Drone d = findDrone(drones, dr);

        d.setPosizionePartenza(pos);
        d.setBatteria(info.getBatteria());
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        for (Drone d: drones){
            if (d.getId()==id)
                return d;
        }
        return null;
    }

    public static Drone findDrone(List<Drone> drones, Drone drone){

        for (Drone d: drones){
            if (d.getId() == drone.getId())
                return d;
        }
        return drone;
    }
}
