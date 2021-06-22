package DronazonPackage;

import java.awt.*;
import java.util.Random;

public class Ordine{

    private final int id;
    private final Point puntoRitiro;
    private final Point puntoConsegna;

    public Ordine(){
        Random rnd = new Random();
        id = rnd.nextInt(1000);
        puntoRitiro = new Point(rnd.nextInt(10), rnd.nextInt(10));
        puntoConsegna = new Point(rnd.nextInt(10), rnd.nextInt(10));
    }


    public Point getPuntoConsegna() {
        return puntoConsegna;
    }

    public Point getPuntoRitiro() {
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
