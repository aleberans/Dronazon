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
    private final MethodSupport methodSupport;

    public ReceiveInfoAfterConsegnaImpl(List<Drone> drones, Object sync){
        this.drones = drones;
        this.sync = sync;
        methodSupport = new MethodSupport(drones);
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

        methodSupport.getDroneFromList(sendStat.getIdDrone()).setBatteria(sendStat.getBetteriaResidua());
        methodSupport.getDroneFromList(sendStat.getIdDrone()).setKmPercorsiSingoloDrone(sendStat.getKmPercorsi());
        methodSupport.getDroneFromList(sendStat.getIdDrone()).setCountConsegne(
                methodSupport.getDroneFromList(sendStat.getIdDrone()).getCountConsegne() + 1);
        methodSupport.getDroneFromList(sendStat.getIdDrone()).setBufferPM10(sendStat.getInquinamentoList());

        methodSupport.getDroneFromList(sendStat.getIdDrone())
                .setPosizionePartenza(
                        new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY())
                );
        //LOGGER.info("LISTA DEL DRONE AGGIORNATA CON LE STAT");

        Drone drone = methodSupport.getDroneFromList(sendStat.getIdDrone());
        if ( !(drone.getIsMaster() && drone.getBatteria() < 20)) {
            methodSupport.getDroneFromList(sendStat.getIdDrone()).setConsegnaAssegnata(false);
            synchronized (sync) {
                LOGGER.info("SVEGLIA SYNC DOPO RICEZIONE INFO DAL DRONE AL MASTER");
                sync.notifyAll();
            }
        }
        /*if (drones.contains(MethodSupport.takeDroneFromId(drones, sendStat.getIdDrone()))) {
            synchronized (sync) {
                LOGGER.info("MASTER HA RICEVUTO LE INFO, SVEGLIA SU SYNC");
                sync.notifyAll();
            }
        }*/
    }
}
