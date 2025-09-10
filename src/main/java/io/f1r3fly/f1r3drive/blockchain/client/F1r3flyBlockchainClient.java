package io.f1r3fly.f1r3drive.blockchain.client;

import casper.CasperMessage;
import casper.DeployServiceCommon;
import casper.ProposeServiceCommon;
import casper.v1.DeployServiceGrpc;
import casper.v1.DeployServiceV1;
import casper.v1.ProposeServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;
import io.f1r3fly.f1r3drive.fuse.FuseException;
import fr.acinq.secp256k1.Hex;
import fr.acinq.secp256k1.Secp256k1;
import io.f1r3fly.f1r3drive.errors.F1r3flyDeployError;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;
import servicemodelapi.ServiceErrorOuterClass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class F1r3flyBlockchainClient {
    public static final String RHOLANG = "rholang";
    public static final String METTA_LANGUAGE = "metta";

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyBlockchainClient.class);

    private static final Duration INIT_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private static final int RETRIES = 10;
    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE; // ~2 GB

    private final DeployServiceGrpc.DeployServiceFutureStub validatorDeployService;
    private final ProposeServiceGrpc.ProposeServiceFutureStub validatorProposeService;
    private final DeployServiceGrpc.DeployServiceFutureStub observerDeployService;

    public F1r3flyBlockchainClient(String validatorHost,
            int validatorPort,
            String observerHost,
            int observerPort) {
        super();

        Security.addProvider(new Blake2bProvider());

        ManagedChannel validatorChannel = ManagedChannelBuilder.forAddress(validatorHost, validatorPort).usePlaintext()
                .build();

        this.validatorDeployService = DeployServiceGrpc.newFutureStub(validatorChannel)
                .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
                .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
        this.validatorProposeService = ProposeServiceGrpc.newFutureStub(validatorChannel)
                .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
                .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);

        ManagedChannel observerChannel = ManagedChannelBuilder.forAddress(observerHost, observerPort).usePlaintext()
                .build();

        this.observerDeployService = DeployServiceGrpc.newFutureStub(observerChannel)
                .withMaxInboundMessageSize(MAX_MESSAGE_SIZE)
                .withMaxOutboundMessageSize(MAX_MESSAGE_SIZE);
    }

    // Cut down on verbosity of surfacing successes
    private <T> Uni<T> succeed(T t) {
        return Uni.createFrom().item(t);
    }

    // Cut down on verbosity of surfacing errors
    private <T> Uni<T> fail(String rho, ServiceErrorOuterClass.ServiceError error) {
        return Uni.createFrom().failure(new F1r3flyDeployError(rho, gatherErrors(error)));
    }

    private String gatherErrors(ServiceErrorOuterClass.ServiceError error) {
        ProtocolStringList messages = error.getMessagesList();
        return messages.stream().collect(Collectors.joining("\n"));
    }

    public DeployServiceCommon.BlockInfo getGenesisBlock() throws F1r3DriveError {
        DeployServiceV1.LastFinalizedBlockResponse response = null;
        try {
            response = observerDeployService
                    .lastFinalizedBlock(DeployServiceCommon.LastFinalizedBlockQuery.newBuilder().build()).get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Error retrieving last finalized block", e);
            throw new F1r3DriveError("Error retrieving last finalized block", e);
        }

        DeployServiceCommon.BlockInfo block = response.getBlockInfo();

        while (block.getBlockInfo().getBlockNumber() > 0) {
            try {
                block = observerDeployService.getBlock(
                        DeployServiceCommon.BlockQuery.newBuilder()
                                .setHash(block.getBlockInfo().getParentsHashList(0))
                                .build())
                        .get().getBlockInfo();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Error retrieving block information", e);
                throw new F1r3DriveError("Error retrieving block information", e);
            }
        }

        return block;
    }

    public DeployServiceCommon.BlockInfo getLastFinalizedBlockFromValidator() throws F1r3DriveError {
        try {
            DeployServiceV1.LastFinalizedBlockResponse response = validatorDeployService.lastFinalizedBlock(
                    DeployServiceCommon.LastFinalizedBlockQuery.newBuilder().build()).get();
            return response.getBlockInfo();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Error retrieving last finalized block from validator", e);
            throw new F1r3DriveError("Error retrieving last finalized block from validator", e);
        }
    }

    public DeployServiceCommon.BlockInfo getLastFinalizedBlockFromObserver() throws F1r3DriveError {
        try {
            DeployServiceV1.LastFinalizedBlockResponse response = observerDeployService.lastFinalizedBlock(
                    DeployServiceCommon.LastFinalizedBlockQuery.newBuilder().build()).get();
            return response.getBlockInfo();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Error retrieving last finalized block from observer", e);
            throw new F1r3DriveError("Error retrieving last finalized block from observer", e);
        }
    }

    public void waitForNodesSynchronization() throws F1r3DriveError {
        waitForNodesSynchronization(5000, 60000); // 5s interval, 60s timeout
    }

    public void waitForNodesSynchronization(long intervalMs, long timeoutMs) throws F1r3DriveError {
        LOGGER.info("Waiting for validator and observer to have the same last block...");

        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        while (System.currentTimeMillis() < endTime) {
            try {
                DeployServiceCommon.BlockInfo validatorBlock = getLastFinalizedBlockFromValidator();
                DeployServiceCommon.BlockInfo observerBlock = getLastFinalizedBlockFromObserver();

                long validatorBlockNumber = validatorBlock.getBlockInfo().getBlockNumber();
                long observerBlockNumber = observerBlock.getBlockInfo().getBlockNumber();

                LOGGER.debug("Validator last block: {}, Observer last block: {}",
                        validatorBlockNumber, observerBlockNumber);

                if (validatorBlockNumber == observerBlockNumber) {
                    LOGGER.info("Nodes synchronized at block number: {}", validatorBlockNumber);
                    return;
                }

                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new F1r3DriveError("Interrupted while waiting for node synchronization", e);
            }
        }

        throw new F1r3DriveError("Timeout waiting for nodes to synchronize after " + timeoutMs + "ms");
    }

    public RhoTypes.Expr exploratoryDeploy(String rhoCode) throws F1r3DriveError {
        try {
            LOGGER.debug("Exploratory deploy code {}", rhoCode);

            // Create query
            DeployServiceCommon.ExploratoryDeployQuery exploratoryDeploy =
                DeployServiceCommon.ExploratoryDeployQuery.newBuilder()
                .setTerm(rhoCode)
                .build();

            // Deploy with retry logic
            DeployServiceV1.ExploratoryDeployResponse deployResponse = observerDeployService.exploratoryDeploy(exploratoryDeploy).get();
                
            LOGGER.debug("Exploratory deploy code {}. Response {}", rhoCode, deployResponse);
            
            if (deployResponse.hasError()) {
                LOGGER.debug("Exploratory deploy code {}. Error response {}", rhoCode, deployResponse.getError());
                throw new F1r3DriveError("Error retrieving exploratory deploy: " + gatherErrors(deployResponse.getError()));
            }

            LOGGER.debug("Exploratory deploy code {}. Result {}", rhoCode, deployResponse.getResult());

            if (deployResponse.getResult().getPostBlockDataCount() == 0) {
                LOGGER.debug("Exploratory deploy code {}. No data found (empty PostBlockData list)", rhoCode);
                throw new F1r3DriveError("No data found at channel");
            }

            LOGGER.debug("Exploratory deploy code {}. PostBlockData count {}", rhoCode, deployResponse.getResult().getPostBlockDataCount());

            if (deployResponse.getResult().getPostBlockData(0).getExprsCount() == 0) {
                LOGGER.debug("Exploratory deploy code {}. No expressions found in PostBlockData", rhoCode);
                throw new F1r3DriveError("No expressions found in data");
            }

            return deployResponse.getResult().getPostBlockData(0).getExprs(0);
            
            
        } catch (Exception e) {
            LOGGER.warn("failed to deploy exploratory code", e);
            throw new F1r3DriveError("Error deploying exploratory code", e);
        }
    }

    public String deploy(String rhoCode, boolean useBiggerRhloPrice, String language, byte[] signingKey, long timestamp)
            throws F1r3flyDeployError {
        try {

            int maxRholangInLogs = 2000;
            LOGGER.debug("Rholang code {}",
                    rhoCode.length() > maxRholangInLogs ? rhoCode.substring(0, maxRholangInLogs) : rhoCode);

            long phloLimit = useBiggerRhloPrice ? 5_000_000_000L : 50_000L;

            LOGGER.trace("Language parameter is skipped for now: {}. Using default language: {}", language, RHOLANG);

            // Make deployment
            CasperMessage.DeployDataProto deployment = CasperMessage.DeployDataProto.newBuilder()
                    .setTerm(rhoCode)
                    .setTimestamp(timestamp)
                    .setPhloPrice(1)
                    .setPhloLimit(phloLimit)
                    .setShardId("root")
                    // .setLanguage(language)
                    .build();

            // Sign deployment
            CasperMessage.DeployDataProto signed = signDeploy(deployment, signingKey);

            // Deploy
            Uni<String> deployVolumeContract = Uni.createFrom().future(validatorDeployService.doDeploy(signed))
                    .flatMap(deployResponse -> {
                        // LOGGER.trace("Deploy Response {}", deployResponse);
                        if (deployResponse.hasError()) {
                            return this.<String>fail(rhoCode, deployResponse.getError());
                        } else {
                            return succeed(deployResponse.getResult());
                        }
                    })
                    .flatMap(deployResult -> {
                        String deployId = deployResult.substring(deployResult.indexOf("DeployId is: ") + 13,
                                deployResult.length());
                        return Uni.createFrom()
                                .future(validatorProposeService.propose(
                                        ProposeServiceCommon.ProposeQuery.newBuilder().setIsAsync(false).build()))
                                .flatMap(proposeResponse -> {
                                    // LOGGER.debug("Propose Response {}", proposeResponse);
                                    if (proposeResponse.hasError()) {
                                        return this.<String>fail(rhoCode, proposeResponse.getError());
                                    } else {
                                        return succeed(deployId);
                                    }
                                });
                    })
                    .flatMap(deployId -> {
                        ByteString b64 = ByteString.copyFrom(Hex.decode(deployId));
                        return Uni.createFrom()
                                .future(validatorDeployService.findDeploy(
                                        DeployServiceCommon.FindDeployQuery.newBuilder().setDeployId(b64).build()))
                                .flatMap(findResponse -> {
                                    // LOGGER.debug("Find Response {}", findResponse);
                                    if (findResponse.hasError()) {
                                        return this.<String>fail(rhoCode, findResponse.getError());
                                    } else {
                                        return succeed(findResponse.getBlockInfo().getBlockHash());
                                    }
                                });
                    })
                    .flatMap(blockHash -> {
                        LOGGER.debug("Block Hash {}", blockHash);
                        return Uni.createFrom()
                                .future(validatorDeployService.isFinalized(
                                        DeployServiceCommon.IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
                                .flatMap(isFinalizedResponse -> {
                                    LOGGER.debug("isFinalizedResponse {}", isFinalizedResponse);
                                    if (isFinalizedResponse.hasError() || !isFinalizedResponse.getIsFinalized()) {
                                        return fail(rhoCode, isFinalizedResponse.getError());
                                    } else {
                                        return succeed(blockHash);
                                    }
                                })
                                .onFailure().retry()
                                .withBackOff(INIT_DELAY, MAX_DELAY)
                                .atMost(RETRIES);
                    });

            // Drummer Hoff Fired It Off
            return deployVolumeContract.await().indefinitely();
        } catch (Exception e) {
            if (e instanceof F1r3flyDeployError) {
                throw (F1r3flyDeployError) e;
            } else {
                LOGGER.warn("failed to deploy Rho {}", rhoCode, e);
                throw new F1r3flyDeployError(rhoCode, "Failed to deploy", e);
            }
        }
    }

    private CasperMessage.DeployDataProto signDeploy(CasperMessage.DeployDataProto deploy, byte[] signingKey) {
        final MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
        } catch (NoSuchAlgorithmException e) {
            throw new FuseException("Can't load MessageDigest instance (BLAKE2_B_256)", e);
        }

        final Secp256k1 secp256k1 = Secp256k1.get();

        CasperMessage.DeployDataProto.Builder builder = CasperMessage.DeployDataProto.newBuilder();

        builder
                .setTerm(deploy.getTerm())
                .setTimestamp(deploy.getTimestamp())
                .setPhloPrice(deploy.getPhloPrice())
                .setPhloLimit(deploy.getPhloLimit())
                .setValidAfterBlockNumber(deploy.getValidAfterBlockNumber())
                .setShardId(deploy.getShardId());

        CasperMessage.DeployDataProto signed = builder.build();

        byte[] serial = signed.toByteArray();
        digest.update(serial);
        byte[] hashed = digest.digest();
        byte[] signature = secp256k1.compact2der(secp256k1.sign(hashed, signingKey));
        byte[] pubKey = secp256k1.pubkeyCreate(signingKey);

        CasperMessage.DeployDataProto.Builder outbound = signed.toBuilder();
        outbound
                .setSigAlgorithm("secp256k1")
                .setSig(ByteString.copyFrom(signature))
                .setDeployer(ByteString.copyFrom(pubKey));

        return outbound.build();
    }
}
