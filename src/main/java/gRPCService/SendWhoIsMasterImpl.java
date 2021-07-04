package gRPCService;

import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendWhoIsMasterGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.List;

public class SendWhoIsMasterImpl extends SendWhoIsMasterImplBase {

    private final Drone drone;

    public SendWhoIsMasterImpl(Drone drone){
        this.drone = drone;
    }

    @Override
    public void master(WhoMaster master, StreamObserver<WhoIsMaster> streamObserver) {
        Drone droneMaster = drone.getDroneMaster();

        WhoIsMaster idMaster = WhoIsMaster.newBuilder().setIdMaster(droneMaster.getId()).build();
        streamObserver.onNext(idMaster);
        streamObserver.onCompleted();
    }

}
