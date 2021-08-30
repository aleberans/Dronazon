package gRPCService;

import DronazonPackage.DroneClient;
import DronazonPackage.DroneRechargingQueue;
import REST.beans.Drone;
import Support.AsynchronousMedthods;
import Support.MethodSupport;
import com.example.grpc.AnswerRechargeGrpc;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class AnswerRechargeImpl extends AnswerRechargeGrpc.AnswerRechargeImplBase {

    private final List<Drone> drones;
    private final HashMap<Drone, String> dronesMap;
    private final Drone drone;
    private final Object recharge;
    private final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());
    private final MethodSupport methodSupport;
    private final AsynchronousMedthods asynchronousMedthods;
    private final DroneRechargingQueue droneRechargingQueue;

    public AnswerRechargeImpl(List<Drone> drones, Drone drone, Object recharge, MethodSupport methodSupport, AsynchronousMedthods asynchronousMedthods, DroneRechargingQueue droneRechargingQueue){
        this.drones = drones;
        this.drone = drone;
        this.methodSupport = methodSupport;
        this.asynchronousMedthods = asynchronousMedthods;
        this.droneRechargingQueue = droneRechargingQueue;
        this.dronesMap = new HashMap<>();
        this.recharge = recharge;
    }

    @Override
    public void okRecharge(Answer answer, StreamObserver<ackMessage> streamObserver) {

        streamObserver.onNext(ackMessage.newBuilder().setMessage("").build());
        streamObserver.onCompleted();

        dronesMap.put(methodSupport.takeDroneFromId(drones, answer.getId()), answer.getAnswer());
        LOGGER.info("Mappa:" + dronesMap);

        synchronized (recharge) {
            while (!checkRecharge(drones)) {
                try {
                    LOGGER.info("VA IN WAIT SU RECHARGE");
                    recharge.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        LOGGER.info("DRONE IN RICARICA...");
        methodSupport.takeDroneFromList(drone, drones).setConsegnaAssegnata(true);
        drone.setInRecharging(true);
        doRecharge(drone);
        LOGGER.info("RICARICA DRONE EFFETTUATA");
        drone.setInRecharging(false);
        LOGGER.info("MANDO OK AGLI ALTRI DRONI IN ATTESA");
        asynchronousMedthods.asynchronousSendOkAfterCompleteRecharge(droneRechargingQueue);
        LOGGER.info("CODA: " + droneRechargingQueue);
        droneRechargingQueue.cleanQueue();
        LOGGER.info("CODA POST: " + droneRechargingQueue);
    }

    public void doRecharge(Drone drone) {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        drone.setPosizionePartenza(new Point(0,0));
        drone.setBatteria(100);
        asynchronousMedthods.asynchronousSendInfoAggiornateToNewMaster(drone);
    }

    public boolean checkRecharge(List<Drone> drones){
        boolean check = false;
        for (Drone drone : dronesMap.keySet()){
            LOGGER.info("DRONE: " + drone.getId());
            check = drones.contains(drone);
        }
        return check;
    }
}
