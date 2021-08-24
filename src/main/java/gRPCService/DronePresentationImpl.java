package gRPCService;
import DronazonPackage.DroneClient;
import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.DronePresentationGrpc.DronePresentationImplBase;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.lang.reflect.Method;
import java.security.acl.LastOwnerException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class DronePresentationImpl extends DronePresentationImplBase{

    private final List<Drone> drones;
    private final Object sync;
    private final Logger LOGGER = Logger.getLogger(DronePresentationImpl .class.getSimpleName());
    private final Object election;
    private final MethodSupport methodSupport;

    public DronePresentationImpl(List<Drone> drones, Object sync, Object election, MethodSupport methodSupport){
        this.drones = drones;
        this.sync = sync;
        this.election = election;
        this.methodSupport = methodSupport;
    }

    @Override
    public void presentation(SendInfoDrone info, StreamObserver<ackMessage> streamObserver){

        Drone drone = new Drone(info.getId(), info.getPortaAscolto(), info.getIndirizzoDrone());
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        synchronized (election) {
            while (!methodSupport.allDronesFreeFromElection(drones)){
                try {
                    LOGGER.info("ASPETTA A PRESENTARSI PERCHE' L'ANELLO STA FACENDO UN'ELEZIONE");
                    election.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        synchronized (drones){
            drones.add(drone);
            //Riordino la lista dopo aver aggiunto il drone che si è inserito
            drones.sort(Comparator.comparingInt(Drone::getId));
        }
        synchronized (sync){
            LOGGER.info("DRONE AGGIUNTO SVEGLIA SU SYNC");
            sync.notifyAll();
        }
        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

}
