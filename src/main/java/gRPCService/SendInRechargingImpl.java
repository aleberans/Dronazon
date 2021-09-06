package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.SendInRechargingGrpc.*;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.logging.Logger;

public class SendInRechargingImpl extends SendInRechargingImplBase {

    private final List<Drone> droni;
    private final MethodSupport methodSupport;
    private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());

    public SendInRechargingImpl(List<Drone> droni, MethodSupport methodSupport){

        this.droni = droni;
        this.methodSupport = methodSupport;
    }

    @Override
    public void inRecharging(Recharging recharging, StreamObserver<ackMessage> streamObserver){

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        if (recharging.getRecharging()) {
            methodSupport.takeDroneFromId(droni, recharging.getId()).setInRecharging(true);
            LOGGER.info("DRONE: " + recharging.getId() + " IMPOSTATO IN RICARICA");
        }
        else {
            methodSupport.takeDroneFromId(droni, recharging.getId()).setInRecharging(false);
            LOGGER.info("DRONE: " + recharging.getId() + " IMPOSTATO NON IN RICARICA");

        }
    }
}
