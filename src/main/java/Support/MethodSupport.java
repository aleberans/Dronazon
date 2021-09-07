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

    public String getAllIdDroni(List<Drone> droni){
        synchronized (drones) {
            StringBuilder id = new StringBuilder();
            for (Drone d : droni) {
                id.append(d.getId()).append(", ").append(" ");
            }
            return id.toString();
        }
    }

    public Drone takeDroneFromId(List<Drone> droni, int id){
        synchronized (drones) {
            for (Drone d : droni) {
                if (d.getId() == id)
                    return d;
            }
            return null;
        }
    }

    public Drone findDrone(List<Drone> droni, Drone drone){
        synchronized (drones) {
            Drone dro = null;
            for (Drone d : droni) {
                if (d.getId() == drone.getId())
                    dro = d;
            }
            return dro;
        }
    }

    public Drone takeDroneFromList(Drone drone, List<Drone> droni){
        synchronized (drones){
            return droni.get(droni.indexOf(findDrone(droni, drone)));
        }
    }

    public boolean thereIsDroneLibero(List<Drone> droni){
        synchronized(drones) {
            if (droni.size() == 1 && (droni.get(0).getIsMaster() && droni.get(0).getBatteria() < 20))
                return false;
            for (Drone d : droni) {
                if (!d.consegnaAssegnata()) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean allDroniLiberi(List<Drone> droni){
        synchronized(drones) {
            for (Drone d : droni) {
                if (d.consegnaAssegnata())
                    return false;
            }
            return true;
        }
    }

    public Drone takeDroneSuccessivo(Drone drone, List<Drone> droni){
        synchronized(drones) {
            int pos = droni.indexOf(findDrone(droni, drone));
            return droni.get((pos + 1) % droni.size());
        }
    }

    public Drone getDroneFromList(int id, List<Drone> droni){
        synchronized(drones) {
            return droni.get(droni.indexOf(takeDroneFromId(droni, id)));
        }
    }

    public void updatePositionPartenzaDrone(List<Drone> droni, Drone drone){
        Random rnd = new Random();
        Point posizionePartenza = new Point(rnd.nextInt(10), rnd.nextInt(10));
        synchronized (drones) {
            droni.get(droni.indexOf(findDrone(droni, drone))).setPosizionePartenza(posizionePartenza);
            drone.setPosizionePartenza(posizionePartenza);
        }
    }

    public boolean allDronesFreeFromElection(List<Drone> droni){
        synchronized (drones){
            for (Drone d: droni){
                if (d.isInElection())
                    return false;
            }
            return true;
        }
    }

    public List<Drone> takeFreeDrone(List<Drone> drones) {
        List<Drone> supp = new ArrayList<>();

        for (Drone d: drones){
            if (!d.isInRecharging() && !d.consegnaAssegnata())
                supp.add(d);
        }
        return supp;
    }
}
