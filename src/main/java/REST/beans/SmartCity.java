package REST.beans;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class SmartCity {

    private static SmartCity instance;

    @XmlElement(name = "drone")
    private List<Drone> smartCity;

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

    public synchronized void setSmartCity(List<Drone> smartCity){this.smartCity = smartCity;}

    public synchronized void addDrone(Drone drone){
        if (checkEqualId(drone))
            throw new IllegalArgumentException("Il drone inserito esiste già!");
        smartCity.add(drone);}

    public synchronized void deleteDrone(Drone drone){
        if (!checkEqualId(drone))
            throw new IllegalArgumentException("Il drone che si vuole eliminare non esiste!");
        smartCity.remove(drone);}

    public boolean checkEqualId(Drone drone){
        boolean sem = false;
        for (Drone d : smartCity) {
            if (d.getId() == drone.getId()) {
                sem = true;
                break;
            }
        }
        return sem;
    }
    

}
