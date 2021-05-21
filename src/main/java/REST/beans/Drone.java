package REST.beans;


import DronazonPackage.DroneClient;
import org.codehaus.jackson.annotate.JsonIgnore;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Drone {

    private String id;
    private String portaAscolto;
    private String indirizzoIpDrone;
    private Posizione posizionePartenza;
    @JsonIgnore private Drone nextDrone;
    @JsonIgnore private boolean isMaster;

    public Drone(){}

    public Drone(String id, String portaAscolto, String indirizzoIpDrone){
        this.id = id;
        this.portaAscolto = portaAscolto;
        this.indirizzoIpDrone = indirizzoIpDrone;
    }

    public String getId(){return this.id;}
    public void setId(String id){this.id = id;}

    public String getPortaAscolto(){return this.portaAscolto;}
    public void setPortaAscolto(String portaAscolto){ this.portaAscolto = portaAscolto;}

    public String getIndirizzoIpDrone(){return this.indirizzoIpDrone;}
    public void setIndirizzoIpDrone(String indirizzoServerAmministratore){this.indirizzoIpDrone = indirizzoServerAmministratore;}

    public void setPosizionePartenza(Posizione posizionePartenza) {
        this.posizionePartenza = posizionePartenza;
    }
    public Posizione getPosizionePartenza() {
        return this.posizionePartenza;
    }

    public Drone getNextDrone() {
        return nextDrone;
    }

    public void setNextDrone(Drone nextDrone) {
        this.nextDrone = nextDrone;
    }

    public boolean getIsMaster() {
        return isMaster;
    }

    public void setIsMaster(boolean master) {
        isMaster = master;
    }
}

