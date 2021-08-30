package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.RechargeGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.logging.Logger;

public class RechargeImpl extends RechargeImplBase {

    private final List<Drone> drones;
    private final Drone drone;
    private final DroneRechargingQueue droneRechargingQueue;
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;
    private final Object recharge;
    private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());

    public RechargeImpl(List<Drone> drones, Drone drone, DroneRechargingQueue droneRechargingQueue,
                        MethodSupport methodSupport, AsynchronousMedthods asynchronousMedthods, Object recharge) {
        this.drones = drones;
        this.drone = drone;
        this.droneRechargingQueue = droneRechargingQueue;
        this.methodSupport = methodSupport;
        this.asynchronousMedthods = asynchronousMedthods;
        this.recharge = recharge;
    }

    @Override
    public void checkForRecharge(MessageRecharge messageRecharge, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        LOGGER.info("STATO BOOLEAN: \nisInRecharging= " + drone.isInRecharging() + "\n"+
                "wantRecharge= " + drone.getWantRecharge() + "\n" +
                "ID: " + drone.getId());

        if (drone.getId() != messageRecharge.getId()) {
            if (!drone.isInRecharging() && !drone.getWantRecharge()) {
                LOGGER.info("ENTRA NEL 1");
                asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
                synchronized (recharge) {
                    recharge.notifyAll();
                }
            } else if (drone.isInRecharging()) {
                LOGGER.info("ENTRA NEL 2");
                droneRechargingQueue.add(messageRecharge);
            } else if (drone.getWantRecharge() && !drone.isRecharged()) {
                LOGGER.info("ENTRA NEL 3");
                MessageRecharge messageCurrentDrone = droneRechargingQueue.takeMessageFromDrone(drone);
                if (messageRecharge.getTimestamp().compareTo(messageCurrentDrone.getTimestamp()) < 0) {
                    LOGGER.info("ENTRA NEL 4");
                    asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
                    synchronized (recharge) {
                        recharge.notifyAll();
                    }
                } else {
                    LOGGER.info("ENTRA NEL 5");
                    droneRechargingQueue.add(messageRecharge);
                }
            }
        }
        else{
            asynchronousMedthods.asynchronousAnswerToRequestOfRecharge(methodSupport.takeDroneFromId(drones, messageRecharge.getId()), drone);
            synchronized (recharge) {
                recharge.notifyAll();
            }
        }
    }
}
