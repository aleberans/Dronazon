package REST.beans;

import javafx.util.Pair;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Posizione {

    private int xPosizioneIniziale;
    private int yPosizioneIniziale;

    public Posizione(){}

    public Posizione(int x, int y){
        this.xPosizioneIniziale = x;
        this.yPosizioneIniziale = y;
    }

    public int getxPosizioneIniziale() {
        return xPosizioneIniziale;
    }

    public int getyPosizioneIniziale() {
        return yPosizioneIniziale;
    }

    public void setxPosizioneIniziale(int xPosizioneIniziale) {
        this.xPosizioneIniziale = xPosizioneIniziale;
    }

    public void setyPosizioneIniziale(int yPosizioneIniziale) {
        this.yPosizioneIniziale = yPosizioneIniziale;
    }


}
