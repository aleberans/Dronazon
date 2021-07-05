package Support;

import REST.beans.Drone;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MethodSupport {

    public static String getAllIdDroni(List<Drone> drones){
        synchronized (drones) {
            StringBuilder id = new StringBuilder();
            for (Drone d : drones) {
                id.append(d.getId()).append(", ");
            }
            return id.toString();
        }
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        synchronized (drones) {
            for (Drone d : drones) {
                if (d.getId() == id)
                    return d;
            }
            return null;
        }
    }

    public static Drone findDrone(List<Drone> drones, Drone drone){
        synchronized (drones) {
            Drone dro = null;
            for (Drone d : drones) {
                if (d.getId() == drone.getId())
                    dro = d;
            }
            return dro;
        }
    }

    public static Drone takeDroneFromList(Drone drone, List<Drone> drones){
        synchronized (drones){
            return drones.get(drones.indexOf(MethodSupport.findDrone(drones, drone)));
        }
    }

    /**
     * @param drones
     * @return
     * Scorre la lista dei droni e trova se c'è almeno un drone libero di effettuare una cosegna
     */
    public static boolean thereIsDroneLibero(List<Drone> drones){
        synchronized(drones) {
            for (Drone d : drones) {
                if (!d.consegnaAssegnata()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean allDroniLiberi(List<Drone> drones){
        synchronized(drones) {
            for (Drone d : drones) {
                if (d.consegnaAssegnata())
                    return false;
            }
            return true;
        }
    }

    public static Drone takeDroneSuccessivo(Drone drone, List<Drone> drones){
        synchronized(drones) {
            int pos = drones.indexOf(MethodSupport.findDrone(drones, drone));

            return drones.get((pos + 1) % drones.size());
        }
    }

    public static Drone getDroneFromList(int id, List<Drone> drones){
        synchronized(drones) {
            return drones.get(drones.indexOf(MethodSupport.takeDroneFromId(drones, id)));
        }
    }

    public static List<Drone> updatePositionPartenzaDrone(List<Drone> drones, Drone drone){
        Random rnd = new Random();
        Point posizionePartenza = new Point(rnd.nextInt(10), rnd.nextInt(10));
        synchronized (drones) {
            drones.get(drones.indexOf(MethodSupport.findDrone(drones, drone))).setPosizionePartenza(posizionePartenza);
            drone.setPosizionePartenza(posizionePartenza);
        }
        return drones;
    }

}
