package io.f1r3fly.f1r3drive.blockchain.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import casper.CasperMessage;
import casper.DeployServiceCommon;
import casper.v1.DeployServiceGrpc;
import casper.v1.DeployServiceV1;
import casper.v1.ProposeServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageFinalityTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void writeDurabilityAndRetryUseValidatorEvenWhenObserverIsUnavailable(boolean retry)
      throws Exception {
    AtomicInteger submissions = new AtomicInteger();
    AtomicInteger finalityChecks = new AtomicInteger();
    AtomicInteger observerCalls = new AtomicInteger();
    DeployServiceGrpc.DeployServiceImplBase validator =
        new DeployServiceGrpc.DeployServiceImplBase() {
          @Override
          public void doDeploy(
              CasperMessage.DeployDataProto request,
              StreamObserver<DeployServiceV1.DeployResponse> response) {
            submissions.incrementAndGet();
            assertEquals(
                ByteString.copyFrom(new byte[] {(byte) 0xab, (byte) 0xcd}), request.getSig());
            response.onNext(
                DeployServiceV1.DeployResponse.newBuilder().setResult("DeployId is: abcd").build());
            response.onCompleted();
          }

          @Override
          public void findDeploy(
              DeployServiceCommon.FindDeployQuery request,
              StreamObserver<DeployServiceV1.FindDeployResponse> response) {
            response.onNext(
                DeployServiceV1.FindDeployResponse.newBuilder()
                    .setBlockInfo(
                        DeployServiceCommon.LightBlockInfo.newBuilder().setBlockHash("1234"))
                    .build());
            response.onCompleted();
          }

          @Override
          public void isFinalized(
              DeployServiceCommon.IsFinalizedQuery request,
              StreamObserver<DeployServiceV1.IsFinalizedResponse> response) {
            assertEquals("1234", request.getHash());
            response.onNext(
                DeployServiceV1.IsFinalizedResponse.newBuilder()
                    .setIsFinalized(finalityChecks.incrementAndGet() > 1)
                    .build());
            response.onCompleted();
          }
        };
    DeployServiceGrpc.DeployServiceImplBase observer =
        new DeployServiceGrpc.DeployServiceImplBase() {
          @Override
          public void findDeploy(
              DeployServiceCommon.FindDeployQuery request,
              StreamObserver<DeployServiceV1.FindDeployResponse> response) {
            observerCalls.incrementAndGet();
            response.onError(Status.UNAVAILABLE.asRuntimeException());
          }

          @Override
          public void isFinalized(
              DeployServiceCommon.IsFinalizedQuery request,
              StreamObserver<DeployServiceV1.IsFinalizedResponse> response) {
            observerCalls.incrementAndGet();
            response.onError(Status.UNAVAILABLE.asRuntimeException());
          }
        };
    Server validatorServer = ServerBuilder.forPort(0).addService(validator).build().start();
    Server observerServer = ServerBuilder.forPort(0).addService(observer).build().start();
    ManagedChannel validatorChannel =
        ManagedChannelBuilder.forAddress("localhost", validatorServer.getPort())
            .usePlaintext()
            .build();
    ManagedChannel observerChannel =
        ManagedChannelBuilder.forAddress("localhost", observerServer.getPort())
            .usePlaintext()
            .build();
    try {
      F1r3flyBlockchainClient client =
          new F1r3flyBlockchainClient(
              DeployServiceGrpc.newBlockingStub(validatorChannel),
              ProposeServiceGrpc.newBlockingStub(validatorChannel),
              DeployServiceGrpc.newBlockingStub(observerChannel),
              true);
      var signed =
          CasperMessage.DeployDataProto.newBuilder()
              .setSig(ByteString.copyFrom(new byte[] {(byte) 0xab, (byte) 0xcd}))
              .build();
      client.deploy(new F1r3flyBlockchainClient.PreparedDeployment("@0!(0)", signed), retry);
      assertEquals(retry ? 0 : 1, submissions.get());
      assertTrue(finalityChecks.get() >= 2, "Inclusion alone must not acknowledge durability");
      assertEquals(0, observerCalls.get());
    } finally {
      validatorChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      observerChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      validatorServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      observerServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
