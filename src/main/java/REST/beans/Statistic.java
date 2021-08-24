package REST.beans;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Statistic {

    private String timestamp;
    private double numeroConsegne;
    private double kmPercorsi;
    private double inquinamento;
    private double batteriaResidua;

    public Statistic(){}

    public Statistic(String timestamp, double numeroConsegne, double kmPercorsi, double inquinamento, double batteriaResidua){

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

    public double getNumeroConsegne() {return numeroConsegne;}
    public void setNumeroConsegne(double numeroConsegne) {this.numeroConsegne = numeroConsegne;}

    public double getKmPercorsi() {return kmPercorsi;
    }

    public void setKmPercorsi(double kmPercorsi) {
        this.kmPercorsi = kmPercorsi;
    }

    public double getInquinamento() {
        return inquinamento;
    }

    public void setInquinamento(double inquinamento) {
        this.inquinamento = inquinamento;
    }

    public double getBatteriaResidua() {
        return batteriaResidua;
    }

    public void setBatteriaResidua(double batteriaResidua) {
        this.batteriaResidua = batteriaResidua;
    }
}
