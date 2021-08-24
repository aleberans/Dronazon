package Support;

import DronazonPackage.DroneClient;
import DronazonPackage.QueueOrdini;
import REST.beans.Drone;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

public class Threads {

    public static class SendStatisticToServer extends Thread{

        private final List<Drone> drones;
        private final QueueOrdini queueOrdini;

        public SendStatisticToServer(List<Drone> drones, QueueOrdini queueOrdini){
            this.drones = drones;
            this.queueOrdini = queueOrdini;
        }

        @Override
        public void run(){
            while(true){
                if (queueOrdini.size() != 0) {
                    ServerMethods.sendStatistics(drones);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static class SendConsegnaThread extends Thread {

        private final List<Drone> drones;
        private final Drone drone;
        private final Object sync;
        private final QueueOrdini queueOrdini;
        private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
        private final MethodSupport methodSupport;
        private final AsynchronousMedthods asynchronousMedthods;

        public SendConsegnaThread(List<Drone> drones, Drone drone, Object sync, QueueOrdini queueOrdini) {
            this.drones = drones;
            this.drone = drone;
            this.sync = sync;
            this.queueOrdini = queueOrdini;
            methodSupport = new MethodSupport(drones);
            asynchronousMedthods = new AsynchronousMedthods(drones);
        }

        @Override
        public void run(){
            while (true) {
                try {
                    synchronized (sync){
                        while (!methodSupport.thereIsDroneLibero()) {
                            LOGGER.info("VAI IN WAIT POICHE' NON CI SONO DRONI DISPONIBILI");
                            sync.wait();
                            LOGGER.info("SVEGLIATO SU SYNC");
                        }
                    }
                    asynchronousMedthods.asynchronousSendConsegna(drone, queueOrdini, sync);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class PingeResultThread extends Thread{

        private final List<Drone> drones;
        private final Drone drone;
        private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
        private final AsynchronousMedthods asynchronousMedthods;

        public PingeResultThread(List<Drone> drones, Drone drone) {
            this.drones = drones;
            this.drone = drone;
            asynchronousMedthods = new AsynchronousMedthods(drones);
        }

        @Override
        public void run(){
            while(true){
                try {
                    asynchronousMedthods.asynchronousPingAlive(drone);
                    LOGGER.info("ID DEL DRONE: " + drone.getId() + "\n"
                            + "ID DEL MASTER CORRENTE: " + drone.getDroneMaster().getId() + "\n"
                            + "TOTALE CONSEGNE EFFETTUATE: " + drone.getCountConsegne() +  "\n"
                            + "TOTALE KM PERCORSI: "+ drone.getKmPercorsiSingoloDrone() + "\n"
                            + "P10 RILEVATO: " + drone.getBufferPM10() + "\n"
                            + "PERCENTUALE BATTERIA RESIDUA: " + drone.getBatteria() +    "\n"
                            + "LISTA DRONI ATTUALE: " + new MethodSupport(drones).getAllIdDroni() + "\n");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class StopThread extends Thread {

        private final Drone drone;
        private final List<Drone> drones;
        private final Object inDelivery;
        private final Object inForward;
        private final MqttClient client;
        private final QueueOrdini queueOrdini;
        private final Object sync;
        private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
        private final MethodSupport methodSupport;

        public StopThread(Drone drone, List<Drone> drones, Object inDelivery, Object inForward, MqttClient client, QueueOrdini queueOrdini, Object sync) {
            this.drone = drone;
            this.drones = drones;
            this.inDelivery = inDelivery;
            this.inForward = inForward;
            this.client = client;
            this.queueOrdini = queueOrdini;
            this.sync = sync;
            methodSupport = new MethodSupport(drones);
        }

        BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

        @Override
        public void run() {
            while (true) {
                try {
                    if (bf.readLine().equals("quit")) {
                        if (!drone.getIsMaster()) {
                            synchronized (inDelivery) {
                                while (drone.isInDelivery()) {
                                    LOGGER.info("IL DRONE È IL DELIVERY, WAIT...");
                                    inDelivery.wait();
                                }
                            }
                            LOGGER.info("IL DRONE HA FINITO LA DELIVERY, ESCO!");
                            synchronized (inForward) {
                                while (drone.isInForwarding()) {
                                    LOGGER.info("IL DRONE È IN FORWARDING, WAIT...");
                                    inForward.wait();
                                }
                            }
                            LOGGER.info("IL DRONE HA FINITO DI FARE FORWARDING, ESCO!");
                            ServerMethods.removeDroneServer(drone);
                            break;
                        } else {
                            client.disconnect();
                            LOGGER.info("IL DRONE MASTER È STATO QUITTATO, GESTISCO TUTTO PRIMA DI CHIUDERLO");
                            LOGGER.info("STATO DRONE: \n" +
                                    "DELIVERY: "  + drone.isInDelivery() + "\n" +
                                    "FORWARDING: " + drone.isInForwarding());
                            synchronized (inDelivery) {
                                while (drone.isInDelivery()) {
                                    LOGGER.info("IL DRONE È IL DELIVERY, WAIT...");
                                    inDelivery.wait();
                                }
                            }
                            synchronized (sync) {
                                while (queueOrdini.size() > 0 || !methodSupport.thereIsDroneLibero()) {
                                    LOGGER.info("CI SONO ANCORA CONSEGNE IN CODA DA GESTIRE E NON CI SONO DRONI O C'E' UN DRONE A CUI E' STATA DATA UNA CONSEGNA, WAIT...\n"
                                            + queueOrdini.size());
                                    sync.wait();
                                }
                            }
                            LOGGER.info("TUTTI GLI ORDINI SONO STATI CONSUMATI");
                            methodSupport.getDroneFromList(drone.getId()).setConsegnaAssegnata(false);
                            synchronized (sync) {
                                while (!methodSupport.allDroniLiberi()) {
                                    LOGGER.info("CI SONO ANCORA DRONI OCCUPATI NELLE CONSEGNE");
                                    sync.wait();
                                }
                            }
                            ServerMethods.removeDroneServer(drone);
                            ServerMethods.sendStatistics(drones);
                            break;
                        }
                    }
                }catch (MqttException | IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LOGGER.info("IL DRONE È USCITO IN MANIERA FORZATA!");
            System.exit(0);
        }
    }
}
