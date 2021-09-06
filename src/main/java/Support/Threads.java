package Support;

import REST.beans.Drone;

import java.util.List;
import java.util.logging.Logger;

public class Threads {

    private final MethodSupport methodSupport;
    private final Object sync;
    private static final Logger LOGGER = Logger.getLogger(AsynchronousMedthods.class.getSimpleName());

    public Threads(MethodSupport methodSupport, Object sync){

        this.methodSupport = methodSupport;
        this.sync = sync;
    }

    public class SendConsegnaThread extends Thread {

        private final List<Drone> drones;
        private final Drone drone;

        public SendConsegnaThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
        }

        @Override
        public void run(){
            while (true) {
                try {
                    synchronized (sync){
                        while (!methodSupport.thereIsDroneLibero(drones)) {
                            LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI\n " +
                                    "STATO RETE: " + drones);
                            sync.wait();
                            LOGGER.info("SVEGLIATO SU SYNC");
                        }
                    }
                    asynchronousSendConsegna(drones, drone);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
