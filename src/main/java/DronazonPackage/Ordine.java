package DronazonPackage;


import javafx.util.Pair;

import java.awt.*;
import java.util.Random;
import java.util.TimerTask;

public class Ordine extends TimerTask {

    private final int id;
    private final Pair<Integer, Integer> puntoRitiro;
    private final Pair<Integer, Integer> puntoConsegna;
    Random rnd = new Random();

    public Ordine(){
        id = rnd.nextInt(1000);
        puntoRitiro = new Pair<>(rnd.nextInt(10), rnd.nextInt(10));
        puntoConsegna = new Pair<>(rnd.nextInt(10), rnd.nextInt(10));
    }

    @Override
    public void run() {
        Ordine ordine = new Ordine();
        System.out.println("do"+ordine.puntoRitiro);
    }
}
