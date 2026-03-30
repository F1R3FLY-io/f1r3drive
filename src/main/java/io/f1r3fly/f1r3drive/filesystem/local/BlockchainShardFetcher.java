package io.f1r3fly.f1r3drive.filesystem.local;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedFile;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockchainShardFetcher {
    private static final Logger logger = LoggerFactory.getLogger(BlockchainShardFetcher.class);

    public static Path fetchDirectoryFromShard(F1r3flyBlockchainClient f1R3FlyBlockchainClient, BlockchainContext blockchainContext, String absolutePath,
            String name, BlockchainDirectory parent) throws NoDataByPath {
        try {
            // Use exploratory deploy with rholang code to read from channel
            String rholangCode = RholangExpressionConstructor.readFromChannel(absolutePath);
            RhoTypes.Expr result = f1R3FlyBlockchainClient.exploratoryDeploy(rholangCode);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseExploratoryDeployResult(result);

            if (fileOrDir.isDir()) {
                FetchedDirectory dir = new FetchedDirectory(blockchainContext, name, parent, fileOrDir.lastUpdated());

                Set<Path> children = fileOrDir.children().stream().map((childName) -> {
                    try {
                        return fetchDirectoryFromShard(f1R3FlyBlockchainClient, blockchainContext,
                                absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                    } catch (NoDataByPath e) {
                        logger.error("Error fetching child directory from shard for path: {}",
                                absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, e);
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                dir.setChildren(children);
                return dir;

            } else {
                FetchedFile file = new FetchedFile(blockchainContext, PathUtils.getFileName(absolutePath),
                        parent, fileOrDir.lastUpdated());
                long offset = 0;
                offset = file.initFromBytes(fileOrDir.firstChunk(), offset);

                if (!fileOrDir.otherChunks().isEmpty()) {
                    Set<Integer> chunkNumbers = fileOrDir.otherChunks().keySet();
                    Integer[] sortedChunkNumbers = chunkNumbers.stream().sorted().toArray(Integer[]::new);

                    for (Integer chunkNumber : sortedChunkNumbers) {
                        String subChannel = fileOrDir.otherChunks().get(chunkNumber);
                        String subChannelRholangCode = RholangExpressionConstructor.readFromChannel(subChannel);
                        RhoTypes.Expr subChannelResult = f1R3FlyBlockchainClient.exploratoryDeploy(subChannelRholangCode);
                        byte[] data = RholangExpressionConstructor.parseExploratoryDeployBytes(subChannelResult);

                        offset = offset + file.initFromBytes(data, offset);
                    }
                }

                file.initSubChannels(fileOrDir.otherChunks());
                return file;
            }

        } catch (NoDataByPath e) {
            logger.info("No data found for path: {}", absolutePath);
            throw e;
        } catch (io.f1r3fly.f1r3drive.errors.F1r3DriveError e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No data found") || msg.contains("No expressions found")) {
                logger.info("No data found for path: {}", absolutePath);
                throw new NoDataByPath(absolutePath, "", e);
            }
            logger.error("Blockchain error while fetching directory from shard for path: {}", absolutePath, e);
            throw e;
        } catch (Throwable e) {
            logger.error("Error fetching directory from shard for path: {}", absolutePath, e);
            throw new RuntimeException("Failed to fetch directory data for " + absolutePath, e);
        }
    }
}
