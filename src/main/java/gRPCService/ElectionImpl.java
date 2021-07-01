package gRPCService;

import com.example.grpc.ElectionGrpc.*;
import com.example.grpc.Message.*;
import io.grpc.stub.StreamObserver;

public class ElectionImpl extends ElectionImplBase {

    @Override
    public void sendElection(ElectionMessage electionMessage, StreamObserver<ackMessage> streamObserver){

        ackMessage message = ackMessage.newBuilder().setMessage("").build();

        streamObserver.onNext(message);
        streamObserver.onCompleted();
    }
}
