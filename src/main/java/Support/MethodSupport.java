package Support;

import REST.beans.Drone;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MethodSupport {

    private final List<Drone> drones;

    public MethodSupport(List<Drone> drones){
        this.drones= drones;
    }

    public String getAllIdDroni(){
        synchronized (drones) {
            StringBuilder id = new StringBuilder();
            for (Drone d : drones) {
                id.append(d.getId()).append(", ").append(" ");
            }
            return id.toString();
        }
    }

    public Drone takeDroneFromId(int id){
        synchronized (drones) {
            for (Drone d : drones) {
                if (d.getId() == id)
                    return d;
            }
            return null;
        }
    }

    public Drone findDrone(Drone drone){
        synchronized (drones) {
            Drone dro = null;
            for (Drone d : drones) {
                if (d.getId() == drone.getId())
                    dro = d;
            }
            return dro;
        }
    }

    public Drone takeDroneFromList(Drone drone){
        synchronized (drones){
            return drones.get(drones.indexOf(findDrone(drone)));
        }
    }

    public boolean thereIsDroneLibero(){
        synchronized(drones) {
            if (drones.size() == 1 && (drones.get(0).getIsMaster() && drones.get(0).getBatteria() < 20))
                return false;
            for (Drone d : drones) {
                if (!d.consegnaAssegnata()) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean allDroniLiberi(){
        synchronized(drones) {
            for (Drone d : drones) {
                if (d.consegnaAssegnata())
                    return false;
            }
            return true;
        }
    }

    public Drone takeDroneSuccessivo(Drone drone){
        synchronized(drones) {
            int pos = drones.indexOf(findDrone(drone));
            return drones.get((pos + 1) % drones.size());
        }
    }

    public Drone getDroneFromList(int id){
        synchronized(drones) {
            return drones.get(drones.indexOf(takeDroneFromId(id)));
        }
    }

    public void updatePositionPartenzaDrone(Drone drone){
        Random rnd = new Random();
        Point posizionePartenza = new Point(rnd.nextInt(10), rnd.nextInt(10));
        synchronized (drones) {
            drones.get(drones.indexOf(findDrone(drone))).setPosizionePartenza(posizionePartenza);
            drone.setPosizionePartenza(posizionePartenza);
        }
    }

    public List<Drone> takeFreeDrone() {
        List<Drone> supp = new ArrayList<>();
        synchronized (drones) {
            for (Drone d : drones) {
                if (!d.isInRecharging() && !d.consegnaAssegnata())
                    supp.add(d);
            }
        }
        return supp;
    }
}
