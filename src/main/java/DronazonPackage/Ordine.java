package DronazonPackage;

import REST.beans.Posizione;
import java.util.Random;

public class Ordine{

    private final int id;
    private final Posizione puntoRitiro;
    private final Posizione puntoConsegna;

    public Ordine(){
        Random rnd = new Random();
        id = rnd.nextInt(1000);
        puntoRitiro = new Posizione(rnd.nextInt(10), rnd.nextInt(10));
        puntoConsegna = new Posizione(rnd.nextInt(10), rnd.nextInt(10));
    }

    public Ordine(int id, Posizione puntoRitiro, Posizione puntoConsegna){
        this.id = id;
        this.puntoConsegna = puntoConsegna;
        this.puntoRitiro = puntoRitiro;
    }

    public Posizione getPuntoConsegna() {
        return puntoConsegna;
    }

    public Posizione getPuntoRitiro() {
        return puntoRitiro;
    }

    @Override
    public String toString() {
        return "Ordine{" +
                "id=" + id +
                ", puntoRitiro=" + puntoRitiro +
                ", puntoConsegna=" + puntoConsegna +
                '}';
    }
}
