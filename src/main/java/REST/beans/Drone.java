package REST.beans;


import DronazonPackage.DroneClient;
import org.codehaus.jackson.annotate.JsonIgnore;

import javax.xml.bind.annotation.XmlRootElement;
import java.awt.*;
import java.util.ArrayList;

@XmlRootElement
public class Drone {

    private int id;
    private int portaAscolto;
    private String indirizzoIpDrone;
    @JsonIgnore private Point posizionePartenza;
    @JsonIgnore private boolean isMaster;
    @JsonIgnore private int batteria = 100;
    @JsonIgnore private Drone droneMaster;
    @JsonIgnore private boolean isOccupato = false;
    @JsonIgnore private double kmPercorsiSingoloDrone = 0;
    @JsonIgnore private int countConsegne = 0;

    public Drone(){}

    public Drone(int id, int portaAscolto, String indirizzoIpDrone){
        this.id = id;
        this.portaAscolto = portaAscolto;
        this.indirizzoIpDrone = indirizzoIpDrone;
    }

    public int getId(){return this.id;}
    public void setId(int id){this.id = id;}

    public int getPortaAscolto(){return this.portaAscolto;}
    public void setPortaAscolto(int portaAscolto){ this.portaAscolto = portaAscolto;}

    public String getIndirizzoIpDrone(){return this.indirizzoIpDrone;}
    public void setIndirizzoIpDrone(String indirizzoServerAmministratore){this.indirizzoIpDrone = indirizzoServerAmministratore;}

    public void setPosizionePartenza(Point posizionePartenza) {
        this.posizionePartenza = posizionePartenza;
    }
    public Point getPosizionePartenza() {
        return this.posizionePartenza;
    }

    public boolean getIsMaster() {
        return isMaster;
    }

    public void setIsMaster(boolean master) {
        isMaster = master;
    }

    public void setBatteria(int batteria) {
        this.batteria = batteria;
    }

    public int getBatteria() {
        return batteria;
    }

    public void setDroneMaster(Drone master) {
        droneMaster = master;
    }

    public Drone getDroneMaster() {
        return droneMaster;
    }

    @Override
    public String toString() {
        return "Drone{" +
                "id=" + id +
                ", portaAscolto=" + portaAscolto +
                ", indirizzoIpDrone='" + indirizzoIpDrone + '\'' +
                ", posizionePartenza=" + posizionePartenza +
                ", isMaster=" + isMaster +
                ", batteria=" + batteria +
                ", droneMaster=" + droneMaster +
                ", isOccupato=" + isOccupato +
                '}';
    }

    public boolean isOccupato() {
        return isOccupato;
    }

    public void setOccupato(boolean occupato) {
        isOccupato = occupato;
    }

    public int getCountConsegne() {
        return countConsegne;
    }

    public void setCountConsegne(int countConsegne) {
        this.countConsegne = countConsegne;
    }

    public double getKmPercorsiSingoloDrone() {
        return kmPercorsiSingoloDrone;
    }

    public void setKmPercorsiSingoloDrone(double kmPercorsiSingoloDrone) {
        this.kmPercorsiSingoloDrone = kmPercorsiSingoloDrone;
    }
}

