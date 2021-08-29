package gRPCService;

import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.RechargeGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.List;

public class RechargeImpl extends RechargeImplBase {

    private final List<Drone> drones;
    private final Drone drone;
    private final DroneRechargingQueue droneRechargingQueue;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;

    public RechargeImpl(List<Drone> drones, Drone drone, DroneRechargingQueue droneRechargingQueue,
                        MethodSupport methodSupport, AsynchronousMedthods asynchronousMedthods) {
        this.drones = drones;
        this.drone = drone;
        this.droneRechargingQueue = droneRechargingQueue;
        this.methodSupport = methodSupport;
        this.asynchronousMedthods = asynchronousMedthods;
    }

    @Override
    public void checkForRecharge(MessageRecharge messageRecharge, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();


        if (!drone.isInRecharging() && !drone.getWantRecharge()) {
            asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()));
        }
        else if (drone.isInRecharging()){
            droneRechargingQueue.add(messageRecharge);
        }
        else if (drone.getWantRecharge() && !drone.isRecharged()){
            MessageRecharge messageCurrentDrone = droneRechargingQueue.takeDroneMessageRecharge(drone);
            if (messageRecharge.getTimestamp().compareTo(messageCurrentDrone.getTimestamp()) < 0) {
                asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()));
            }
            else
                droneRechargingQueue.add(messageRecharge);
        }

    }
}
