package gRPCService;

import com.example.grpc.Message.*;
import com.example.grpc.SendWhoIsMasterGrpc.*;
import io.grpc.stub.StreamObserver;

public class SendWhoIsMasterImpl extends SendWhoIsMasterImplBase {


    @Override
    public void master(WhoMaster master, StreamObserver<ackMessage> streamObserver) {

        ackMessage message = ackMessage.newBuilder().setMessage("Avvenuto invio delle informazioni").build();
        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }
}
