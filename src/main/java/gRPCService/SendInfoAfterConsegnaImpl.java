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
    private double KmPercorsiTotali;
    private static final Logger LOGGER = Logger.getLogger(SendInfoAfterConsegnaImpl.class.getSimpleName());
    private final Object sync;

    public SendInfoAfterConsegnaImpl(List<Drone> drones, double KmPercorsiTotali, Object sync){
        this.drones = drones;
        this.KmPercorsiTotali = KmPercorsiTotali;
        this.sync = sync;
    }

    /**
     * @param sendStat
     * @param streamObserver
     * Gestisce le informazioni che riceve dai droni che hanno eseguito una consegna aggiornando le informazioni nella lista dei droni
     */
    public void sendInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        KmPercorsiTotali = KmPercorsiTotali + sendStat.getKmPercorsi();

        //LOGGER.info("KMPercorsi:"+sendStat.getKmPercorsi());
        getDrone(sendStat.getIdDrone(), drones).setOccupato(false);

        synchronized (sync){
            sync.notify();
            LOGGER.info("SVEGLIATO NON PIÙ OCCUAPTO");
        }

        //aggiorno la batteria residua del drone che ha effettuato la consegna nella lista
        getDrone(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());

        //aggiorno la posizione del drone nella lista di droni
        Point pos = new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY());
        drones.get(drones.indexOf(takeDroneFromId(drones, sendStat.getIdDrone())))
                .setPosizionePartenza(pos);

        //LOGGER.info("NUOVA POSIZIONE"+ drones.get(drones.indexOf(takeDroneFromId(drones, sendStat.getIdDrone()))).getPosizionePartenza());

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