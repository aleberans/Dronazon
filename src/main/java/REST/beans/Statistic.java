package REST.beans;

import javax.xml.bind.annotation.XmlRootElement;
import java.sql.Timestamp;

@XmlRootElement
public class Statistic {

    private String timestamp;
    private int numeroConsegne;
    private double kmPercorsi;
    private int inquinamento;
    private int batteriaResidua;

    public Statistic(){}

    public Statistic(String timestamp, int numeroConsegne, double kmPercorsi, int inquinamento, int batteriaResidua){

        this.timestamp = timestamp;
        this.numeroConsegne = numeroConsegne;
        this.kmPercorsi = kmPercorsi;
        this.inquinamento = inquinamento;
        this.batteriaResidua = batteriaResidua;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getNumeroConsegne() {return numeroConsegne;}
    public void setNumeroConsegne(int numeroConsegne) {this.numeroConsegne = numeroConsegne;}

    public double getKmPercorsi() {return kmPercorsi;
    }

    public void setKmPercorsi(int kmPercorsi) {
        this.kmPercorsi = kmPercorsi;
    }

    public int getInquinamento() {
        return inquinamento;
    }

    public void setInquinamento(int inquinamento) {
        this.inquinamento = inquinamento;
    }

    public int getBatteriaResidua() {
        return batteriaResidua;
    }

    public void setBatteriaResidua(int batteriaResidua) {
        this.batteriaResidua = batteriaResidua;
    }
}
