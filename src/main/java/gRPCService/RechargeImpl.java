package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.RechargeGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class RechargeImpl extends RechargeImplBase {

    private final List<Drone> drones;
    private final Drone drone;
    private final DroneRechargingQueue droneRechargingQueue;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;
    private final Logger LOGGER = Logger.getLogger(DroneClient .class.getSimpleName());
    private String timeStampCurrentDrone;
    private final Object ricarica;

    public RechargeImpl(List<Drone> drones, Drone drone, DroneRechargingQueue droneRechargingQueue,
                        MethodSupport methodSupport, AsynchronousMedthods asynchronousMedthods, HashMap<Drone, String> dronesMap) {
        this.drones = drones;
        this.drone = drone;
        this.droneRechargingQueue = droneRechargingQueue;
        this.methodSupport = methodSupport;
        this.asynchronousMedthods = asynchronousMedthods;
        timeStampCurrentDrone = null;
        ricarica= new Object();
    }

    @Override
    public void checkForRecharge(MessageRecharge messageRecharge, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        if (drone.getId() == messageRecharge.getId()){
            asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
            timeStampCurrentDrone = messageRecharge.getTimestamp();
        }
        else {
            if (!drone.isInRecharging() && !drone.getWantRecharge()) {
                asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
            } else if (drone.isInRecharging()) {
                droneRechargingQueue.add(messageRecharge);
            } else if (drone.getWantRecharge() && !drone.isRecharged()) {
                LOGGER.info("timeStampCurrentDrone: " + timeStampCurrentDrone);
                if (messageRecharge.getTimestamp().compareTo(timeStampCurrentDrone) < 0) {
                    asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
                } else {
                    droneRechargingQueue.add(messageRecharge);
                }
            }
        }
    }

}
