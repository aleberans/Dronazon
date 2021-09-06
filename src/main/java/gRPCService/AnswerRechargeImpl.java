package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.AnswerRechargeGrpc;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class AnswerRechargeImpl extends AnswerRechargeGrpc.AnswerRechargeImplBase {

    private final List<Drone> drones;
    private final HashMap<Drone, String> dronesMap;
    private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
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

        dronesMap.put(methodSupport.takeDroneFromId(drones, answer.getId()), answer.getAnswer());
        LOGGER.info("ID DRONE AGGIUNTO NELLA MAPPA: " + methodSupport.takeDroneFromId(drones, answer.getId()).getId() +
                "\nSTATO MAPPA: " + dronesMap.keySet());
        synchronized (recharge) {
            if (dronesMap.size() == drones.size()){
                recharge.notifyAll();
                LOGGER.info("SVEGLIATO SU RECHARGE");
            }
        }
    }
}
