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

    public ReceiveInfoAfterConsegnaImpl(List<Drone> drones, Object sync, MethodSupport methodSupport){
        this.drones = drones;
        this.sync = sync;
        this.methodSupport = methodSupport;
    }

    @Override
    public void receiveInfoDopoConsegna(SendStat sendStat, StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();

        Drone drone = methodSupport.getDroneFromList(sendStat.getIdDrone(), drones);

        //aggiorno la batteria, km percorsi e count del drone che ha effettuato la consegna nella lista
        synchronized (drones) {
            methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBatteria(sendStat.getBetteriaResidua());
            methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setKmPercorsiSingoloDrone(sendStat.getKmPercorsi());
            methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setCountConsegne(
                    methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).getCountConsegne() + 1);
            methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setBufferPM10(sendStat.getInquinamentoList());

            methodSupport.getDroneFromList(sendStat.getIdDrone(), drones)
                    .setPosizionePartenza(
                            new Point(sendStat.getPosizioneArrivo().getX(), sendStat.getPosizioneArrivo().getY())
                    );
            //LOGGER.info("LISTA DEL DRONE AGGIORNATA CON LE STAT");

            if ( !(drone.getIsMaster() && drone.getBatteria() <= 20))
                methodSupport.getDroneFromList(sendStat.getIdDrone(), drones).setConsegnaAssegnata(false);

            if (!drone.isInRecharging()) {
                synchronized (sync) {
                    LOGGER.info("SVEGLIA SYNC DOPO RICEZIONE INFO DAL DRONE: " + sendStat.getIdDrone() + " AL MASTER");
                    sync.notifyAll();
                }
            }
        }
    }
}
