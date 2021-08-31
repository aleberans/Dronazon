package DronazonPackage;

import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.Message.*;

import java.util.ArrayList;
import java.util.List;

public class DroneRechargingQueue {

    private final List<MessageRecharge> codaRicarica;
    private final MethodSupport methodSupport;
    private final List<Drone> drones;

    public DroneRechargingQueue(MethodSupport methodSupport, List<Drone> drones) {
        this.methodSupport = methodSupport;
        this.drones = drones;
        codaRicarica = new ArrayList<>();
    }

    public synchronized void add(MessageRecharge messageRecharge){
        codaRicarica.add(messageRecharge);
        notify();
    }

    public synchronized MessageRecharge takeMessageFromDrone(Drone drone){
        for (MessageRecharge messageRecharge : codaRicarica) {
            if (messageRecharge.getId() == drone.getId())
                return messageRecharge;
        }
        return null;
    }

    public synchronized List<Drone> takeDronesFromQueueInDrones(){
        List<Drone> ids = new ArrayList<>();

        for (MessageRecharge msg : codaRicarica){
            ids.add(methodSupport.takeDroneFromId(drones, msg.getId()));
        }
        return ids;
    }

    public synchronized void cleanQueue(){
        codaRicarica.clear();
    }

    @Override
    public String toString() {
        return "DroneRechargingQueue{" +
                "codaRicarica=" + codaRicarica +
                ", drones=" + drones +
                '}';
    }
}
