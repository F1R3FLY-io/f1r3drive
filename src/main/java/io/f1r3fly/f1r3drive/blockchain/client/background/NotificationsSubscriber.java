package io.f1r3fly.f1r3drive.blockchain.client.background;

import io.f1r3fly.f1r3drive.errors.F1r3DriveError;

import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import rhoapi.RhoTypes;

import java.util.List;

public class NotificationsSubscriber {

    public static void subscribe(F1r3flyBlockchainClient f1r3flyAPI, DeployDispatcher deployDispatcher, String clientHost, int clientPort, String mountName, String revAddress, byte[] signingKey) {
        boolean isFirstSubscription = true;
        try {
            String rholangCode = RholangExpressionConstructor.readFromChannel("@/" + mountName + "/clients");
            RhoTypes.Expr result = f1r3flyAPI.exploratoryDeploy(rholangCode);
            isFirstSubscription = false; // if it succeeds, there's data
        } catch (F1r3DriveError e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No data found") || msg.contains("No expressions found")) {
                isFirstSubscription = true;
            } else {
                throw e;
            }
        }

        String rhoExpression = isFirstSubscription ?
            RholangExpressionConstructor.createFirstSubscription(clientHost, clientPort, mountName) :
            RholangExpressionConstructor.appendSubscription(clientHost, clientPort, mountName);

        enqueueDeployment(deployDispatcher, rhoExpression, revAddress, signingKey);
    }

    private static void enqueueDeployment(DeployDispatcher deployDispatcher, String rholangExpression, String revAddress, byte[] signingKey) {
        deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(rholangExpression, false, F1r3flyBlockchainClient.RHOLANG, revAddress, signingKey, System.currentTimeMillis()));
    }

    public static void unsubscribe(DeployDispatcher deployDispatcher, String clientHost, int clientPort, String mountName, String revAddress, byte[] signingKey) {
        enqueueDeployment(deployDispatcher, RholangExpressionConstructor.removeSubscription(clientHost, clientPort, mountName), revAddress, signingKey);
    }

}
