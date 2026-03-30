package io.f1r3fly.f1r3drive.blockchain.client.grcp.listener;

import casper.ExternalCommunicationServiceCommon;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.stub.StreamObserver;
import casper.v1.ExternalCommunicationServiceGrpc;
import casper.v1.ExternalCommunicationServiceV1.UpdateNotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class F1r3flyDriveServer {

    private static final Logger logger = LoggerFactory.getLogger(F1r3flyDriveServer.class.getName());

    private final Server server;

    private F1r3flyDriveServer(Server server) {
        this.server = server;
    }

    public static F1r3flyDriveServer create(int port, UpdateNotificationHandler updateNotificationHandler) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(port)
            .addService(new ExternalCommunicationServiceImpl(updateNotificationHandler))
            .build();

        F1r3flyDriveServer f1r3flyDriveServer = new F1r3flyDriveServer(server);

        // add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("Shutting down gRPC server since JVM is shutting down");
            f1r3flyDriveServer.shutdownGracefully();
        }));

        return f1r3flyDriveServer;
    }

    public void start() throws IOException, InterruptedException {
        server.start();
        logger.info("Server started, listening on {}", server.getPort());
    }

    public void shutdownGracefully() {
        if (server.isShutdown()) {
            return;
        }

        server.shutdown();
        try {
            server.awaitTermination();
            logger.debug("Server shut down");
        } catch (InterruptedException e) {
            logger.error("Error while shutting down server", e);
            server.shutdownNow();
        }
    }

    static class ExternalCommunicationServiceImpl extends ExternalCommunicationServiceGrpc.ExternalCommunicationServiceImplBase {

        private final UpdateNotificationHandler updateNotificationHandler;

        public ExternalCommunicationServiceImpl(UpdateNotificationHandler updateNotificationHandler) {
            this.updateNotificationHandler = updateNotificationHandler;
        }

        @Override
        public void sendNotification(ExternalCommunicationServiceCommon.UpdateNotification request, StreamObserver<UpdateNotificationResponse> responseObserver) {
            try {
                UpdateNotificationResponse response = updateNotificationHandler.handle(request);

                // Send response
                responseObserver.onNext(response);
            } catch (Exception e) {
                e.printStackTrace();
                responseObserver.onError(e);
            } finally {
                responseObserver.onCompleted();
            }
        }
    }
}

