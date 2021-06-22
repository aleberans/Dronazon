package gRPCService;

import com.example.grpc.Message.*;
import com.example.grpc.SendConsegnaToDroneGrpc.*;
import io.grpc.stub.StreamObserver;

public class SendConsegnaToDroneImpl extends SendConsegnaToDroneImplBase {

    @Override
    public void sendConsegna(Consegna consegna, StreamObserver<ackMessage> streamObserver){

    }
}
