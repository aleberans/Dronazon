package gRPCService;

import REST.beans.Drone;
import com.example.grpc.AnswerRechargeGrpc;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

public class AnswerRechargeImpl extends AnswerRechargeGrpc.AnswerRechargeImplBase {

    private final Drone drone;

    public AnswerRechargeImpl(Drone drone){
        this.drone = drone;
    }

    @Override
    public void okRecharge(Answer answer, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        

    }
}
