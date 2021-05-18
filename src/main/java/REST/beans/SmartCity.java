package REST.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class SmartCity {

    private static SmartCity instance;

    private ArrayList<Drone> smartCity;

    private SmartCity(){smartCity = new ArrayList<>();}

    //singleton
    public synchronized static SmartCity getInstance(){
        if(instance==null)
            instance = new SmartCity();
        return instance;
    }

    public synchronized List<Drone> getSmartCity(){
        return new ArrayList<>(smartCity);
    }
    
    public String stampaSmartCity(){
        
        StringBuilder result = new StringBuilder();
        
        for (Drone drone: smartCity) {
            result.append("id: ").append(drone.getId()).append("\n")
                    .append("porta di ascolto: ").append(drone.getPortaAscolto()).append("\n")
                    .append("indirizzo IP: ").append(drone.getIndirizzoIpDrone()).append("\n\n");
        }
        return result.toString();
    }

    public synchronized void setSmartCity(ArrayList<Drone> smartCity){this.smartCity = smartCity;}

    public synchronized ArrayList<Drone> addDrone(Drone drone) {
        if (checkEqualId(drone))
            throw new IllegalArgumentException("Il drone inserito esiste già!");
        Random rnd = new Random();
        drone.setPosizionePartenza(new Posizione(rnd.nextInt(10), rnd.nextInt(10)));
        smartCity.add(drone);
        return smartCity;
    }

    public synchronized void deleteDrone(Drone drone){
        if (!checkEqualId(drone))
            throw new IllegalArgumentException("Il drone che si vuole eliminare non esiste!");
        smartCity.remove(drone);}

    public boolean checkEqualId(Drone drone){
        boolean sem = false;
        for (Drone d : smartCity) {
            if (d.getId().equals(drone.getId())) {
                sem = true;
                break;
            }
        }
        return sem;
    }
    

}
