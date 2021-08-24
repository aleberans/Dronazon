package gRPCService;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.SendUpdatedInfoToMasterGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.List;

public class SendUpdatedInfoToMasterImpl extends SendUpdatedInfoToMasterGrpc.SendUpdatedInfoToMasterImplBase {

    private final List<Drone> drones;
    private final Drone drone;
    private final Object inForward;
    private final MethodSupport methodSupport;

    public SendUpdatedInfoToMasterImpl(List<Drone> drones, Drone drone,
                                       Object inForward, MethodSupport methodSupport){
        this.drones = drones;
        this.drone = drone;
        this.inForward = inForward;
        this.methodSupport = methodSupport;
    }

    @Override
    public void updatedInfo(Info info, StreamObserver<ackMessage> streamObserver){
        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        Point pos = new Point(info.getPosizione().getX(), info.getPosizione().getY());

        synchronized (drones) {
            methodSupport.getDroneFromList(info.getId(), drones).setPosizionePartenza(pos);
            methodSupport.getDroneFromList(info.getId(), drones).setBatteria(info.getBatteria());
            methodSupport.getDroneFromList(info.getId(), drones).setConsegnaAssegnata(false);
        }
        //SI METTE NON PIÙ IN FASE DI ELEZIONE E PUÒ COSI USCIRE
        drone.setInForwarding(false);
        if (!drone.isInForwarding()) {
            synchronized (inForward) {
                while (drone.isInForwarding()) {
                    inForward.notify();
                }
            }
        }
    }
}
