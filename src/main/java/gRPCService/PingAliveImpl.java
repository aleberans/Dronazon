package gRPCService;

import com.example.grpc.Message.*;
import com.example.grpc.PingAliveGrpc;
import io.grpc.stub.StreamObserver;

public class
PingAliveImpl extends PingAliveGrpc.PingAliveImplBase {

    @Override
    public void ping(PingMessage pingMessage, StreamObserver<PingMessage> streamObserver){

        PingMessage message = PingMessage.newBuilder().build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }
}
