package REST.beans;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Drone {

    private String id;
    private String portaAscolto;
    private String indirizzoServerAmministratore;

    public Drone(){};

    public Drone(String id, String portaAscolto, String indirizzoServerAmministratore){
        this.id = id;
        this.portaAscolto = portaAscolto;
        this.indirizzoServerAmministratore = indirizzoServerAmministratore;
    }

    public String getId(){return this.id;}
    public void setId(String id){this.id = id;}

    public String getPortaAscolto(){return this.portaAscolto;}
    public void setPortaAscolto(String portaAscolto){ this.portaAscolto = portaAscolto;}

    public String getIndirizzoServerAmministratore(){return this.indirizzoServerAmministratore;}
    public void setIndirizzoServerAmministratore(String indirizzoServerAmministratore){this.indirizzoServerAmministratore = indirizzoServerAmministratore;}
}
