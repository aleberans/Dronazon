package REST.beans;


import DronazonPackage.DroneClient;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Drone {

    private String id;
    private String portaAscolto;
    private String indirizzoIpDrone;
    private Posizione posizionePartenza;

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

}

