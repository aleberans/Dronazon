package gRPCService;

import Support.LogFormatterBlue;
import Support.MethodSupport;
import com.example.grpc.Message.*;
import com.example.grpc.SendPositionToDroneMasterGrpc;
import io.grpc.stub.StreamObserver;

import java.awt.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class SendPositionToDroneMasterImpl extends SendPositionToDroneMasterGrpc.SendPositionToDroneMasterImplBase {

    private final MethodSupport methodSupport;
    private final Object sync;
    private final Logger LOGGER = Logger.getLogger(DronePresentationImpl .class.getSimpleName());

    public SendPositionToDroneMasterImpl(MethodSupport methodSupport, Object sync){
        this.methodSupport = methodSupport;
        this.sync = sync;
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        LogFormatterBlue formatter = new LogFormatterBlue();
        handler.setFormatter(formatter);
        LOGGER.addHandler(handler);
    }

    @Override
    public void sendPosition(SendPositionToMaster info, StreamObserver<ackMessage> streamObserver){
        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();

        updatePositionDrone(info.getId(), new Point(info.getPos().getX(), info.getPos().getY()));

        synchronized (sync){
            LOGGER.info("RICEVUTE INFORMAZIONI SULLA POSIZIONE DEL DRONE");
            sync.notifyAll();
        }
    }

    public void updatePositionDrone( int id, Point position){
        methodSupport.findDrone(methodSupport.takeDroneFromId(id)).setPosizionePartenza(position);
    }


}
