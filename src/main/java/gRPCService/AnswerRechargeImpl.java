package gRPCService;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.AnswerRechargeGrpc;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;

public class AnswerRechargeImpl extends AnswerRechargeGrpc.AnswerRechargeImplBase {

    private final List<Drone> drones;
    private final HashMap<Drone, String> dronesMap;
    private final MethodSupport methodSupport;
    private final Object recharge;

    public AnswerRechargeImpl(List<Drone> drones, HashMap<Drone, String> dronesMap, MethodSupport methodSupport, Object recharge){
        this.drones = drones;
        this.methodSupport = methodSupport;
        this.dronesMap = dronesMap;
        this.recharge = recharge;
    }

    @Override
    public void okRecharge(Answer answer, StreamObserver<ackMessage> streamObserver) {

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        dronesMap.put(methodSupport.takeDroneFromId(answer.getId()), answer.getAnswer());

        synchronized (recharge) {
            if (dronesMap.size() == drones.size()){
                recharge.notifyAll();
            }
        }
    }
}
