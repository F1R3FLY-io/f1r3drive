package io.f1r3fly.f1r3drive.app;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.errors.F1r3flyDeployError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoProposer simulates auto-propose behavior in a blockchain shard by doing dummy deployments
 * and proposes every 5 seconds. This is used for testing purposes to simulate production shard
 * auto-propose behavior.
 */
public class AutoProposer extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoProposer.class);
    private static final long DEPLOY_INTERVAL_MS = 5000; // 5 seconds

    private final F1r3flyBlockchainClient blockchainClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final byte[] autoproposerPrivateKey;

    private int deployCounter = 0;

    public AutoProposer(String validatorHost, int validatorPort, String validatorPrivateKey) {
        super("AutoProposer");
        setDaemon(true); // Don't prevent JVM shutdown

        // Create blockchain client with manual propose enabled for auto-propose behavior
        this.blockchainClient = new F1r3flyBlockchainClient(
            validatorHost, validatorPort,
            validatorHost, validatorPort, // Use validator as observer for simplicity
            true // Enable manual propose
        );

        // Create a dummy signing key for auto-propose deployments
        this.autoproposerPrivateKey = Hex.decode(validatorPrivateKey);

        LOGGER.info("AutoProposer initialized for {}:{}", validatorHost, validatorPort);
    }

    @Override
    public void run() {
        LOGGER.info("AutoProposer started - will do dummy deploy and propose every {} ms", DEPLOY_INTERVAL_MS);
        running.set(true);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                doDummyDeployAndPropose();
                Thread.sleep(DEPLOY_INTERVAL_MS);
            } catch (InterruptedException e) {
                LOGGER.info("AutoProposer interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warn("Error in AutoProposer dummy deploy/propose cycle: {}", e.getMessage());
                try {
                    Thread.sleep(1000); // Wait a bit before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        stopped.set(true);
        LOGGER.info("AutoProposer stopped");
    }

    private void doDummyDeployAndPropose() {
        try {
            deployCounter++;
            long timestamp = System.currentTimeMillis();

            // Create dummy Rho code - just a simple operation that won't interfere with tests
            String dummyRhoCode = String.format("@\"autopropose-dummy-%d\"!(%d)", deployCounter, timestamp);

            LOGGER.debug("AutoProposer doing dummy deploy #{}: {}", deployCounter, dummyRhoCode);

            // Use the blockchain client to deploy and propose
            blockchainClient.deploy(dummyRhoCode, false, F1r3flyBlockchainClient.RHOLANG, autoproposerPrivateKey, timestamp);

            LOGGER.debug("AutoProposer deploy #{} and propose completed successfully", deployCounter);

        } catch (F1r3flyDeployError e) {
            LOGGER.warn("AutoProposer deploy #{} failed: {}", deployCounter, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("AutoProposer error during dummy deploy/propose #{}: {}", deployCounter, e.getMessage(), e);
        }
    }

    /**
     * Stops the AutoProposer gracefully
     */
    public void shutdown() {
        LOGGER.info("Stopping AutoProposer...");
        running.set(false);
        interrupt();

        // Wait for it to actually stop
        try {
            join(10000); // Wait up to 10 seconds
            if (isAlive()) {
                LOGGER.warn("AutoProposer did not stop gracefully within 10 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
