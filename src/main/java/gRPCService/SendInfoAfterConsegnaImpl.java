package gRPCService;


import REST.beans.Drone;
import com.example.grpc.Message.*;
import com.example.grpc.SendInfoAfterConsegnaGrpc;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import java.util.logging.Logger;

public class SendInfoAfterConsegnaImpl extends SendInfoAfterConsegnaGrpc.SendInfoAfterConsegnaImplBase {

    private final List<Drone> drones;
    private static final Logger LOGGER = Logger.getLogger(SendInfoAfterConsegnaImpl.class.getSimpleName());
    private final Object sync;

    public SendInfoAfterConsegnaImpl(List<Drone> drones, Object sync){
        this.drones = drones;
        this.sync = sync;
    }

    /**
     * @param sendStat
     * @param streamObserver
     * Gestisce le informazioni che riceve dai droni che hanno eseguito una consegna aggiornando le informazioni nella lista dei droni
     */
    public void sendInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        if (drones.contains(takeDroneFromId(drones, sendStat.getIdDrone()))) {
            /*LOGGER.info("IL DRONE È ANCORA VIVO E IL MASTER HA RICEVUTO LE INFORMAZIONI\n" +
                            "SETTO IL DRONE " + sendStat.getIdDrone() + " LIBERO DI RICEVE NUOVI ORDINI");*/
            getDroneFromList(sendStat.getIdDrone(), drones).setOccupato(false);
            synchronized (sync){
                sync.notify();
                //LOGGER.info("DRONE SVEGLIATO, NON PIÙ OCCUPATO");
            }
        }
        else
            LOGGER.info("IL DRONE È USCITO");


        //aggiorno la batteria, km percorsi e count del drone che ha effettuato la consegna nella lista
        getDroneFromList(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());
        getDroneFromList(sendStat.getIdDrone(), drones).setKmPercorsiSingoloDrone(sendStat.getKmPercorsi());
        getDroneFromList(sendStat.getIdDrone(), drones).setCountConsegne(
                getDroneFromList(sendStat.getIdDrone(), drones).getCountConsegne() + 1);
        LOGGER.info("LISTA DEL DRONE AGGIORNATA CON LE STAT");

        //aggiorno la posizione del drone nella lista di droni
        Point pos = new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY());
        getDroneFromList(sendStat.getIdDrone(), drones).setPosizionePartenza(pos);


        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }

    private static Drone getDroneFromList(int id, List<Drone> drones){
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
