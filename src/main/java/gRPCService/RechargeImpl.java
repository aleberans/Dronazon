package gRPCService;

import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.RechargeGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.List;

public class RechargeImpl extends RechargeImplBase {

    private final List<Drone> drones;
    private final Drone drone;

    public RechargeImpl(List<Drone> drones, Drone drone) {
        this.drones = drones;
        this.drone = drone;
    }

    @Override
    public void checkForRecharge(MessageRecharge messageRecharge, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();


    }
}
