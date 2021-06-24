package DronazonPackage;

import java.util.ArrayList;

public class QueueOrdini {

    private final ArrayList<Ordine> ordini;

    public QueueOrdini() {
        ordini = new ArrayList<>();
    }

    public synchronized void add(Ordine ordine){
        ordini.add(ordine);
        notify();
    }

    public synchronized void remove(Ordine ordine){
        ordini.remove(ordine);
    }

    public synchronized Ordine consume() throws InterruptedException {
        while (ordini.size() <=0)
            wait();

        return ordini.get(0);
    }

    public synchronized int size(){
        return ordini.size();
    }

    @Override
    public String toString() {
        return "QueueOrdini{" +
                "ordini=" + ordini +
                '}';
    }
}
