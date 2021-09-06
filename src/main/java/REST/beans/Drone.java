package REST.beans;

import org.codehaus.jackson.annotate.JsonIgnore;

import javax.xml.bind.annotation.XmlRootElement;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class Drone implements Comparable<Drone>{

    private int id;
    private int portaAscolto;
    private String indirizzoIpDrone;
    @JsonIgnore private Point posizionePartenza;
    @JsonIgnore private boolean isMaster;
    @JsonIgnore private int batteria = 100;
    @JsonIgnore private boolean isInElection = false;
    @JsonIgnore private Drone droneMaster;
    @JsonIgnore private boolean consegnaAssegnata = false;
    @JsonIgnore private double kmPercorsiSingoloDrone = 0;
    @JsonIgnore private int countConsegne = 0;
    @JsonIgnore private boolean isInDelivery = false;
    @JsonIgnore private boolean isInForwarding = false;
    @JsonIgnore private boolean isInRecharging = false;
    @JsonIgnore private boolean wantRecharge = false;
    @JsonIgnore private boolean recharged = false;
    @JsonIgnore private List<Double> bufferPM10 = new ArrayList<>();


    public Drone(){}

    public Drone(int id, int portaAscolto, String indirizzoIpDrone){
        this.id = id;
        this.portaAscolto = portaAscolto;
        this.indirizzoIpDrone = indirizzoIpDrone;
    }

    public synchronized boolean isRecharged() {
        return recharged;
    }

    public synchronized void setRecharged(boolean recharged) {
        this.recharged = recharged;
    }

    public synchronized boolean isInForwarding() {
        return isInForwarding;
    }

    public synchronized void setInForwarding(boolean isInForwarding) {
        this.isInForwarding = isInForwarding;
        if (!isInForwarding)
            notify();
    }

    public synchronized void setInDelivery(boolean isInDelivery){
        this.isInDelivery = isInDelivery;
        if (!isInDelivery)
            notify();
    }

    public synchronized boolean isInElection() {
        return isInElection;
    }

    public synchronized void setInElection(boolean inElection) {
        isInElection = inElection;
        if (!isInElection)
            notify();
    }

    public synchronized void setInRecharging(boolean inRecharging) {
        isInRecharging = inRecharging;
    }

    public synchronized boolean isInRecharging() {
        return isInRecharging;
    }

    public synchronized void setWantRecharging(boolean wantRecharge) {
        this.wantRecharge = wantRecharge;
    }

    public synchronized boolean getWantRecharge() {
        return wantRecharge;
    }

    public boolean isInDelivery() {
        return isInDelivery;
    }

    public List<Double> getBufferPM10(){
        return bufferPM10;
    }

    public void setBufferPM10(List<Double> bufferPM10){
        this.bufferPM10 = bufferPM10;
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
                ", consegnaAssegnata =" + consegnaAssegnata +
                ", kmPercorsiSingoloDrone=" + kmPercorsiSingoloDrone +
                ", countConsegne=" + countConsegne +
                ", isInDelivery=" + isInDelivery +
                ", isInForwarding=" + isInForwarding +
                ", isInRecharging= " + isInRecharging +
                ", wantRecharge=" + wantRecharge +
                ", recharged=" + recharged +
                '}';
    }

    public boolean consegnaAssegnata() {
        return consegnaAssegnata;
    }

    public void setConsegnaAssegnata(boolean consegnaAssegnata) {
        this.consegnaAssegnata = consegnaAssegnata;
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

    @Override
    public int compareTo(Drone o) {
        if (batteria > o.getBatteria()) return 1;
        else if (batteria == o.getBatteria() && id > o.getId()) return 1;
        else return -1;
    }
}

