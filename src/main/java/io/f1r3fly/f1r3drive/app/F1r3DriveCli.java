package io.f1r3fly.f1r3drive.app;

import io.f1r3fly.f1r3drive.app.linux.fuse.F1r3DriveFuse;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "f1r3FUSE", mixinStandardHelpOptions = true, version = "f1r3FUSE 1.0",
    description = "A FUSE filesystem based on the F1r3fly blockchain.")
class F1r3DriveCli implements Callable<Integer> {

    private static final String[] MOUNT_OPTIONS = {
        // Linux-compatible FUSE mount options
                "-o", "fsname=f1r3drive",
                "-o", "noatime",
                "-s"  // Single-threaded mode for better FUSE2 compatibility and safety
    };

    @Option(names = {"-h", "--validator-host"}, description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost.")
    private String validatorHost = "localhost";

    @Option(names = {"-p", "--validator-port"}, description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402.")
    private int validatorPort = 40402;

    @Option(names = {"-oh", "--observer-host"}, description = "Host of the F1r3fly blockchain observer gRPC API to connect to. Defaults to localhost.")
    private String observerHost = "localhost";

    @Option(names = {"-op", "--observer-port"}, description = "Port of the F1r3fly blockchain observer gRPC API to connect to. Defaults to 40403.")
    private int observerPort = 40403;

    @Option(names = {"-ck", "--cipher-key-path"}, required = true, description = "Cipher key path. If file not found, a new key will be generated.")
    private String cipherKeyPath;

    @Parameters(index = "0", description = "The path at which to mount the filesystem.")
    private Path mountPoint;

    @Option(names = {"-ra", "--rev-address"}, description = "The rev address of the wallet to unlock.")
    private String revAddress;

    @Option(names = {"-pk", "--private-key"}, description = "The private key of the wallet to unlock.")
    private String privateKey;

    @Option(names = {"-mp", "--manual-propose"}, required = true, description = "Manual propose configuration. If true, will propose and wait for finalization. If false, will skip propose and finalization waiting.")
    private boolean manualPropose;

    @Option(names = {"-d", "--debug"}, description = "Enable FUSE debug mode for verbose logging of filesystem operations.")
    private boolean fuseDebug = false;

    private F1r3DriveFuse f1r3DriveFuse;


    @Override
    public Integer call() throws Exception {
        AESCipher.init(cipherKeyPath); // init singleton instance

        F1r3flyBlockchainClient f1R3FlyBlockchainClient = new F1r3flyBlockchainClient(
            validatorHost,
            validatorPort,
            observerHost,
            observerPort,
            manualPropose
        );

        f1r3DriveFuse = new F1r3DriveFuse(
            f1R3FlyBlockchainClient
        );

        // Add shutdown hook to ensure proper unmounting on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (f1r3DriveFuse != null) {
                try {
                    f1r3DriveFuse.umount();
                } catch (Exception e) {
                    System.err.println("Error during shutdown unmount: " + e.getMessage());
                }
            }
        }, "F1r3Drive-Shutdown"));

        // Mount filesystem - unmounting is handled by shutdown hook
        if (revAddress != null && privateKey != null) {
            f1r3DriveFuse.mountAndUnlockRootDirectory(mountPoint, true, fuseDebug, revAddress, privateKey, MOUNT_OPTIONS);
        } else {
            f1r3DriveFuse.mount(mountPoint, true, fuseDebug, MOUNT_OPTIONS);
        }
        return 0;
    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new F1r3DriveCli()).execute(args);
        System.exit(exitCode);
    }
}
