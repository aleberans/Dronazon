package gRPCService;


import REST.beans.Drone;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.ReceiveInfoAfterConsegnaGrpc;
import io.grpc.stub.StreamObserver;
import java.awt.*;
import java.util.List;
import java.util.logging.Logger;

public class ReceiveInfoAfterConsegnaImpl extends ReceiveInfoAfterConsegnaGrpc.ReceiveInfoAfterConsegnaImplBase {

    private final List<Drone> drones;
    private static final Logger LOGGER = Logger.getLogger(ReceiveInfoAfterConsegnaImpl.class.getSimpleName());
    private final Object sync;

    public ReceiveInfoAfterConsegnaImpl(List<Drone> drones, Object sync){
        this.drones = drones;
        this.sync = sync;
    }

    /**
     * @param sendStat
     * @param streamObserver
     * Gestisce le informazioni che riceve dai droni che hanno eseguito una consegna aggiornando le informazioni nella lista dei droni
     */
    @Override
    public void receiveInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

        //aggiorno la batteria, km percorsi e count del drone che ha effettuato la consegna nella lista
        synchronized (drones) {
            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());
            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setKmPercorsiSingoloDrone(sendStat.getKmPercorsi());
            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setCountConsegne(
                    MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).getCountConsegne() + 1);
            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBufferPM10(sendStat.getInquinamentoList());

            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones)
                    .setPosizionePartenza(
                            new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY())
                    );
            //LOGGER.info("LISTA DEL DRONE AGGIORNATA CON LE STAT");
            MethodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setConsegnaAssegnata(false);

        }

        if (drones.contains(MethodSupport.takeDroneFromId(drones, sendStat.getIdDrone()))) {
            synchronized (sync) {
                sync.notifyAll();
            }
        }
    }
}
