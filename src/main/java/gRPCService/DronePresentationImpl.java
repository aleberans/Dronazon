package gRPCService;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.SendInfoDrone;
import com.example.grpc.Message.ackMessage;
import io.grpc.stub.StreamObserver;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class DronePresentationImpl extends DronePresentationImplBase{

    private final List<Drone> drones;
    private final Logger LOGGER = Logger.getLogger(DronePresentationImpl .class.getSimpleName());
    private final Object election;
    private final MethodSupport methodSupport;

    public DronePresentationImpl(List<Drone> drones, Object election, MethodSupport methodSupport){
        this.drones = drones;
        this.election = election;
        this.methodSupport = methodSupport;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        synchronized (drones){
            drones.add(drone);
            //Riordino la lista dopo aver aggiunto il drone che si è inserito
            drones.sort(Comparator.comparingInt(Drone::getId));
        }

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
