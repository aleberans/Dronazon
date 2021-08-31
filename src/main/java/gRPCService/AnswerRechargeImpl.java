package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.AnswerRechargeGrpc;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class AnswerRechargeImpl extends AnswerRechargeGrpc.AnswerRechargeImplBase {

    private final List<Drone> drones;
    private final HashMap<Drone, String> dronesMap;
    private final Drone drone;
    private final Object recharge;
    private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private final MethodSupport methodSupport;

    public AnswerRechargeImpl(List<Drone> drones, Drone drone, Object recharge, MethodSupport methodSupport, AsynchronousMedthods asynchronousMedthods, DroneRechargingQueue droneRechargingQueue){
        this.drones = drones;
        this.drone = drone;
        this.methodSupport = methodSupport;
        this.dronesMap = new HashMap<>();
        this.recharge = recharge;
    }

    @Override
    public void okRecharge(Answer answer, StreamObserver<ackMessage> streamObserver) {

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        dronesMap.put(methodSupport.takeDroneFromId(drones, answer.getId()), answer.getAnswer());
        LOGGER.info("Mappa:" + dronesMap);

    }

}
