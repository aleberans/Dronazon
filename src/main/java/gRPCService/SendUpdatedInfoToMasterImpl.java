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
    private final Object sync;

    public SendUpdatedInfoToMasterImpl(List<Drone> drones, Drone drone, Object sync){
        this.drones = drones;
        this.drone = drone;
        this.sync = sync;
    }

    @Override
    public void updatedInfo(Info info, StreamObserver<ackMessage> streamObserver){
        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        Point pos = new Point(info.getPosizione().getX(), info.getPosizione().getY());

        MethodSupport.getDroneFromList(info.getId(), drones).setPosizionePartenza(pos);
        MethodSupport.getDroneFromList(info.getId(), drones).setBatteria(info.getBatteria());
        MethodSupport.getDroneFromList(info.getId(), drones).setConsegnaNonAssegnata(true);

        synchronized (sync){
            sync.notifyAll();
        }
    }
}
