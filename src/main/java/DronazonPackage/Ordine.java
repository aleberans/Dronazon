package DronazonPackage;


import REST.beans.Posizione;

import java.util.Random;
import java.util.TimerTask;

public class Ordine extends TimerTask {

    private final int id;
    private final Posizione puntoRitiro;
    private final Posizione puntoConsegna;
    Random rnd = new Random();

    public Ordine(){
        id = rnd.nextInt(1000);
        puntoRitiro = new Posizione(rnd.nextInt(10), rnd.nextInt(10));
        puntoConsegna = new Posizione(rnd.nextInt(10), rnd.nextInt(10));
    }

    @Override
    public void run() {
        Ordine ordine = new Ordine();
        System.out.println("do"+ordine.puntoRitiro);
    }
}
