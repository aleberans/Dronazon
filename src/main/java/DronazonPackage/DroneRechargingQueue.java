package DronazonPackage;

import REST.beans.Drone;
import com.example.grpc.Message.*;

import java.util.ArrayList;

public class DroneRechargingQueue {

    private final ArrayList<MessageRecharge> codaRicarica;

    public DroneRechargingQueue() {
        codaRicarica = new ArrayList<>();
    }

    public synchronized void add(MessageRecharge messageRecharge){
        codaRicarica.add(messageRecharge);
        notify();
    }

    public synchronized MessageRecharge takeDroneMessageRecharge(Drone drone){
        for (MessageRecharge messageRecharge : codaRicarica) {
            if (messageRecharge.getId() == drone.getId())
                return messageRecharge;
        }
        return null;
    }

    public synchronized void remove(MessageRecharge messageRecharge){codaRicarica.remove(messageRecharge);
    }

    public synchronized MessageRecharge consume() throws InterruptedException {
        while (codaRicarica.isEmpty())
            wait();

        return codaRicarica.get(0);
    }

    public synchronized int size(){
        return codaRicarica.size();
    }
}
