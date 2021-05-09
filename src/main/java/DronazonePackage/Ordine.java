package DronazonePackage;


import java.awt.*;
import java.util.Random;
import java.util.TimerTask;

public class Ordine extends TimerTask {

    private int id;
    private Point puntoRitiro;
    private Point puntoConsegna;
    Random rnd = new Random();

    public Ordine(){
        id = rnd.nextInt(1000);
        puntoRitiro = new Point(rnd.nextInt(10), rnd.nextInt(10));
        puntoConsegna = new Point(rnd.nextInt(10), rnd.nextInt(10));
    }

    @Override
    public void run() {
        Ordine ordine = new Ordine();
        System.out.println("do"+ordine.puntoRitiro);
    }
}
