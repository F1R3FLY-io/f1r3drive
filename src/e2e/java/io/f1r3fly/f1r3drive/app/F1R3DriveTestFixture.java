package io.f1r3fly.f1r3drive.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import generic.FinderSyncExtensionServiceOuterClass;
import io.f1r3fly.f1r3drive.app.linux.fuse.F1r3DriveFuse;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.finderextensions.client.FinderSyncExtensionServiceClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Testcontainers
public class F1R3DriveTestFixture {
    protected static final int GRPC_PORT = 40402;
    protected static final int PROTOCOL_PORT = 40400;
    protected static final int DISCOVERY_PORT = 40404;
    protected static final String MAX_BLOCK_LIMIT = "1000";
    protected static final int MAX_MESSAGE_SIZE = 1024 * 1024 * 1024; // ~1G
    protected static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    protected static final String validatorPrivateKey = "5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657"; // Bootstrap
    protected static final Path MOUNT_POINT = new File("/tmp/f1r3drive/").toPath();
    protected static final File MOUNT_POINT_FILE = MOUNT_POINT.toFile();

    protected static final String REV_WALLET_1 = "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA"; // Validator_1
    protected static final String PRIVATE_KEY_1 = "357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9"; // Validator_1
    protected static final File LOCKED_WALLET_DIR_1 = new File(MOUNT_POINT_FILE, "LOCKED-REMOTE-REV-" + REV_WALLET_1);
    protected static final File UNLOCKED_WALLET_DIR_1 = new File(MOUNT_POINT_FILE, REV_WALLET_1);

    protected static final String REV_WALLET_2 = "1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8"; // AutoProposer
    protected static final String PRIVATE_KEY_2 = "61e594124ca6af84a5468d98b34a4f3431ef39c54c6cf07fe6fbf8b079ef64f6"; // AutoProposer
    protected static final File LOCKED_WALLET_DIR_2 = new File(MOUNT_POINT_FILE, "LOCKED-REMOTE-REV-" + REV_WALLET_2);
    protected static final File UNLOCKED_WALLET_DIR_2 = new File(MOUNT_POINT_FILE, REV_WALLET_2);

    public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse(
        "f1r3flyindustries/f1r3fly-scala-node:latest");

    protected static GenericContainer<?> f1r3flyBoot;
    protected static String f1r3flyBootAddress;
    protected static GenericContainer<?> f1r3flyObserver;
    protected static Network network;

    protected static final Logger log = (Logger) LoggerFactory.getLogger(F1R3DriveTestFixture.class);
    protected static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
    protected static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    protected static F1r3DriveFuse f1r3DriveFuse;
    protected static F1r3flyBlockchainClient f1R3FlyBlockchainClient;

    protected static AutoProposer autoProposer;

    @BeforeEach
    void setupContainers() throws InterruptedException {
        deleteDirectories();

        listAppender.start();
        log.addAppender(listAppender);

        // Create a network for containers to communicate
        network = Network.newNetwork();

        String bootAlias = "f1r3fly-boot";
        String observerAlias = "f1r3fly-observer";
        f1r3flyBoot = new GenericContainer<>(F1R3FLY_IMAGE)
            // Bootstrap-specific configuration (ceremony master)
            .withFileSystemBind("local-shard/conf/bootstrap-ceremony.conf", "/var/lib/rnode/rnode.conf", BindMode.READ_ONLY)
            .withFileSystemBind("local-shard/genesis/wallets.txt", "/var/lib/rnode/genesis/wallets.txt", BindMode.READ_ONLY)
            .withFileSystemBind("local-shard/genesis/singleton-bonds.txt", "/var/lib/rnode/genesis/bonds.txt", BindMode.READ_ONLY)
            .withFileSystemBind("local-shard/conf/logback.xml", "/var/lib/rnode/logback.xml", BindMode.READ_ONLY)
            // Node-specific volumes
            .withFileSystemBind("local-shard/data/bootstrap", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withFileSystemBind("local-shard/certs/bootstrap/node.certificate.pem", "/var/lib/rnode/node.certificate.pem", BindMode.READ_ONLY)
            .withFileSystemBind("local-shard/certs/bootstrap/node.key.pem", "/var/lib/rnode/node.key.pem", BindMode.READ_ONLY)
            .withExposedPorts(GRPC_PORT, PROTOCOL_PORT, DISCOVERY_PORT)
            .withCommand("run -s --no-upnp --allow-private-addresses"
                + " --host " + bootAlias
                + " --api-max-blocks-limit " + MAX_BLOCK_LIMIT
                + " --api-grpc-max-recv-message-size " + MAX_MESSAGE_SIZE
                + " --required-signatures 0"
                + " --synchrony-constraint-threshold=0.0 --validator-private-key " + validatorPrivateKey)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx1g")
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withNetwork(network)
            .withNetworkAliases(bootAlias)
            .withStartupTimeout(STARTUP_TIMEOUT);

        f1r3flyBoot.start(); // Manually start the container

        // Use container network alias for container-to-container communication
        f1r3flyBootAddress = "rnode://1e780e5dfbe0a3d9470a2b414f502d59402e09c2@" + bootAlias + "?protocol="
            + PROTOCOL_PORT + "&discovery=" + DISCOVERY_PORT;

        log.info("Using bootstrap address: {}", f1r3flyBootAddress);

        f1r3flyObserver = new GenericContainer<>(F1R3FLY_IMAGE)
            .withFileSystemBind("local-shard/conf/logback.xml", "/var/lib/rnode/logback.xml", BindMode.READ_ONLY)
            .withFileSystemBind("local-shard/data/observer/", "/var/lib/rnode/", BindMode.READ_WRITE)
            .withExposedPorts(GRPC_PORT)
            .withCommand("run -b " + f1r3flyBootAddress + " --allow-private-addresses --no-upnp" +
                " --host " + observerAlias +
                " --approve-duration 10seconds --approve-interval 10seconds" +
                " --fork-choice-check-if-stale-interval 30seconds --fork-choice-stale-threshold 30seconds")
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx1g")
            .waitingFor(Wait.forListeningPorts(GRPC_PORT))
            .withNetwork(network)
            .withNetworkAliases(observerAlias)
            .withStartupTimeout(STARTUP_TIMEOUT);

        log.info("Starting observer with bootstrap address: {}", f1r3flyBootAddress);
        f1r3flyObserver.start();

        // commented b/c save the java heap memory
        // f1r3flyBoot.followOutput(logConsumer);

        // Wait for both containers' GRPC ports to be available
        waitForPortToOpen("localhost", f1r3flyBoot.getMappedPort(GRPC_PORT), STARTUP_TIMEOUT);
        waitForPortToOpen("localhost", f1r3flyObserver.getMappedPort(GRPC_PORT), STARTUP_TIMEOUT);
    }

    void mountF1r3Drive(boolean manualPropose) throws InterruptedException {
        new File("/tmp/cipher.key").delete(); // remove key file if exists

        AESCipher.init("/tmp/cipher.key"); // file doesn't exist, so new key will be generated there
        f1R3FlyBlockchainClient = new F1r3flyBlockchainClient(
            "localhost", f1r3flyBoot.getMappedPort(GRPC_PORT),
            "localhost", f1r3flyObserver.getMappedPort(GRPC_PORT),
            manualPropose); // Enable manual propose for tests to test the full flow
        f1r3DriveFuse = new F1r3DriveFuse(f1R3FlyBlockchainClient);

        forceUmountAndCleanup(); // cleanup before mount

        // Add delay before mounting to ensure previous test cleanup is complete
        Thread.sleep(1000);

        f1r3DriveFuse.mount(MOUNT_POINT);

        // Add delay after mounting to ensure mount is stable
        Thread.sleep(1000);
    }

    void startAutoProposer() {
        autoProposer = new AutoProposer(
            "localhost", f1r3flyBoot.getMappedPort(GRPC_PORT),
            PRIVATE_KEY_2);

        autoProposer.start();
    }

    @AfterEach
    void stopAutoProposer() {
        if (autoProposer != null) {
            autoProposer.shutdown();
            autoProposer = null;
        }
    }

    @AfterEach
    void unmountF1r3Drive() throws InterruptedException {
        try {
            if (f1r3DriveFuse != null) {
                forceUmountAndCleanup();
                // Add delay after cleanup to ensure complete unmount
                Thread.sleep(2000);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            // ignore
        }
    }

    @AfterEach
    void tearDownContainers() {
        if (f1r3flyBoot != null) {
            f1r3flyBoot.stop();
            f1r3flyBoot.close();
        }
        if (f1r3flyObserver != null) {
            f1r3flyObserver.stop();
            f1r3flyObserver.close();
        }

        if (network != null) {
            network.close();
        }

        deleteDirectories();

        listAppender.stop();
    }

    protected static void cleanDataDirectory(String destination, List<String> excludeList) {
        try {
            // if test fails, try to cleanup the data folder of the node manually
            // cd data && rm -rf blockstorage dagstorage eval rspace casperbuffer
            // deploystorage rnode.log && cd
            cleanDirectoryExcept(destination, excludeList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void cleanDirectoryExcept(String directoryPath, List<String> excludeList) throws IOException {
        File directory = new File(directoryPath);
        Path dirPath = directory.toPath();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                Path filePath = file.toPath();
                String relativePath = dirPath.relativize(filePath).toString();

                if (!excludeList.contains(relativePath)) {
                    if (file.isDirectory()) {
                        FileUtils.deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        }
    }

    protected static void deleteDirectory(File directory) {
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void deleteDirectories() {
        deleteDirectory(MOUNT_POINT_FILE);
        deleteDirectory(new File("local-shard/data"));
    }

    protected static void forceUmountAndCleanup() {
        boolean unmountAttempted = false;
        
        try { // try graceful unmount first
            if (f1r3DriveFuse != null) {
                f1r3DriveFuse.umount();
                unmountAttempted = true;
                Thread.sleep(1000); // Wait for unmount to complete
            }

            // Only attempt force unmount if graceful unmount was attempted but may have failed
            if (unmountAttempted) {
                try {
                    // Check if still mounted before attempting force unmount
                    Process checkMount = new ProcessBuilder("mount").start();
                    checkMount.waitFor();
                    // If we reach here without exception, check if our mount point is still in the output
                    // For simplicity in tests, we'll skip the detailed check and just attempt force unmount
                    log.debug("Attempting force unmount as safety measure");
                    Process unmountProcess = new ProcessBuilder("umount", MOUNT_POINT.toString()).start();
                    int exitCode = unmountProcess.waitFor();
                    if (exitCode != 0) {
                        log.warn("Force unmount via system command reported failure with exit code: {}", exitCode);
                    }
                } catch (Exception e) {
                    // Ignore errors from force unmount - this is expected if already unmounted
                    log.debug("Force unmount failed (expected if already unmounted): {}", e.getMessage());
                }
            }

            // Clean up mount point directory more carefully
            File mountPointFile = MOUNT_POINT.toFile();
            if (mountPointFile.exists()) {
                // If directory exists and is not empty, try to clean it first
                if (mountPointFile.isDirectory()) {
                    File[] children = mountPointFile.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            try {
                                if (child.isDirectory()) {
                                    // Recursively delete subdirectories
                                    deleteDirectoryRecursively(child);
                                } else {
                                    child.delete();
                                }
                            } catch (Exception e) {
                                log.warn("Failed to delete child file/directory: {}", child.getAbsolutePath(), e);
                            }
                        }
                    }
                }
                // Now try to delete the mount point directory itself
                if (!mountPointFile.delete()) {
                    log.warn("Failed to delete mount point directory: {}", mountPointFile.getAbsolutePath());
                }
            }

            // Ensure mount point directory is recreated clean
            if (!mountPointFile.mkdirs()) {
                // mkdirs() returns false if directory already exists, so check if it exists
                if (!mountPointFile.exists()) {
                    log.error("Failed to create mount point directory: {}", mountPointFile.getAbsolutePath());
                    throw new RuntimeException("Failed to create mount point directory");
                } else {
                    log.debug("Mount point directory already exists: {}", mountPointFile.getAbsolutePath());
                }
            } else {
                log.debug("Created mount point directory: {}", mountPointFile.getAbsolutePath());
            }

        } catch (Throwable e) {
            log.error("Error during forceUmountAndCleanup", e);
        }
    }

    private static void deleteDirectoryRecursively(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectoryRecursively(child);
                } else {
                    child.delete();
                }
            }
        }
        directory.delete();
    }

    protected static void waitOnBackgroundDeployments() {
        if (f1r3DriveFuse == null)
            throw new IllegalStateException("f1r3drive is not initialized");

        if (f1R3FlyBlockchainClient == null)
            throw new IllegalStateException("f1R3FlyBlockchainClient is not initialized");

        f1r3DriveFuse.waitOnBackgroundThread();

        try {
            // Wait for validator and observer to have the same last block
            f1R3FlyBlockchainClient.waitForNodesSynchronization();
        } catch (io.f1r3fly.f1r3drive.errors.F1r3DriveError e) {
            throw new RuntimeException("Error waiting for nodes synchronization", e);
        }
    }

    protected static void remount() {
        f1r3DriveFuse.umount();
        forceUmountAndCleanup();
        try {
            f1r3DriveFuse.mount(MOUNT_POINT); // should pass: fetch the filesystem back
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void simulateUnlockWalletDirectoryAction(String revAddress, String privateKey)
        throws FinderSyncExtensionServiceClient.WalletUnlockException {
        try (FinderSyncExtensionServiceClient client = new FinderSyncExtensionServiceClient("localhost", 54000)) {
            client.unlockWalletDirectory(revAddress, privateKey);
        }
    }

    protected static void simulateChangeTokenAction(String tokenPath)
        throws FinderSyncExtensionServiceClient.ActionSubmissionException {
        try (FinderSyncExtensionServiceClient client = new FinderSyncExtensionServiceClient("localhost", 54000)) {
            client.submitAction(FinderSyncExtensionServiceOuterClass.MenuActionType.CHANGE, tokenPath);
        }
    }

    private static void waitForPortToOpen(String host, int port, Duration timeout) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        log.info("Waiting for port {}:{} to become available...", host, port);

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                log.info("Port {}:{} is now available", host, port);
                return;
            } catch (IOException e) {
                // Port not yet available, continue waiting
                Thread.sleep(1000);
            }
        }

        throw new RuntimeException("Timeout waiting for port " + host + ":" + port + " to become available");
    }
}