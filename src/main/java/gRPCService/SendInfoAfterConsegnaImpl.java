package gRPCService;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendInfoAfterConsegnaGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SendInfoAfterConsegnaImpl extends SendInfoAfterConsegnaGrpc.SendInfoAfterConsegnaImplBase {

    private final List<Drone> drones;
    private final ArrayList<Double> arrayListKmPercorsi;
    private static final Logger LOGGER = Logger.getLogger(SendInfoAfterConsegnaImpl.class.getSimpleName());
    private final Object sync;

    public SendInfoAfterConsegnaImpl(List<Drone> drones,ArrayList<Double> arrayListKmPercorsi, Object sync){
        this.drones = drones;
        this.arrayListKmPercorsi = arrayListKmPercorsi;
        this.sync = sync;
    }

    public void sendInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        arrayListKmPercorsi.add(sendStat.getKmPercorsi());
        //drones.get(drones.indexOf(takeDroneFromId(drones, sendStat.getIdDrone()))).setOccupato(false);

        LOGGER.info("KMPercorsi:"+sendStat.getKmPercorsi());
        getDrone(sendStat.getIdDrone(), drones).setOccupato(false);

        synchronized (sync){
            sync.notify();
        }

        getDrone(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());

        //aggiorno la posizione del drone nella lista di droni
        Point pos = new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY());
        drones.get(drones.indexOf(takeDroneFromId(drones, sendStat.getIdDrone())))
                .setPosizionePartenza(pos);


    }

    private static Drone getDrone(int id, List<Drone> drones){
        return drones.get(drones.indexOf(takeDroneFromId(drones, id)));
    }

    public static Drone takeDroneFromId(List<Drone> drones, int id){
        for (Drone d: drones){
            if (d.getId()==id)
                return d;
        }
        return null;
    }
}
