package gRPCService;

import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.ReceiveWhoIsMasterGrpc.*;
import io.grpc.stub.StreamObserver;

public class ReceiveWhoIsMasterImpl extends ReceiveWhoIsMasterImplBase {

    private final Drone drone;

    public ReceiveWhoIsMasterImpl(Drone drone){
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
