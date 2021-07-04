package gRPCService;


import REST.beans.Drone;
import Support.MethodSupport;
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
    @Override
    public void sendInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

        //aggiorno la batteria, km percorsi e count del drone che ha effettuato la consegna nella lista
        MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());
        MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setKmPercorsiSingoloDrone(sendStat.getKmPercorsi());
        MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setCountConsegne(
                MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).getCountConsegne() + 1);
        MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBufferPM10(sendStat.getInquinamentoList());
        MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones)
                .setPosizionePartenza(
                        new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY())
                );

        LOGGER.info("LISTA DEL DRONE AGGIORNATA CON LE STAT");

        if (drones.contains(MethodSupport.takeDroneFromId(drones, sendStat.getIdDrone()))) {
            LOGGER.info("IL DRONE È ANCORA VIVO E IL MASTER HA RICEVUTO LE INFORMAZIONI\n" +
                            "SETTO IL DRONE " + sendStat.getIdDrone() + " LIBERO DI RICEVERE NUOVI ORDINI");

            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setConsegnaNonAssegnata(true);

            synchronized (sync){
                LOGGER.info("SVEGLIATO SYNC CHE DORMIVA SU CONSEGNA NON ASSEGNATA FALSE (OVVERO CHE IL DRONE È IN CONSEGNA)");
                sync.notifyAll();
            }
        }
        else
            LOGGER.info("IL DRONE È USCITO");

    }
}
