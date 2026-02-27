package io.f1r3fly.f1r3drive.blockchain.rholang;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.util.*;
import java.util.stream.Collectors;

public class RholangExpressionConstructor {

    private static final String LIST_DELIMITER = ",";


    private static final String TYPE = "type";
    private static final String DIR_TYPE = "d";
    private static final String FILE_TYPE = "f";

    private static final String FIRST_CHUNK = "firstChunk";
    private static final String CHILDREN = "children";
    private static final String LAST_UPDATED = "lastUpdated";
    private static final String OTHER_CHUNKS = "otherChunks";

    /**
     * Represents a file or a folder
     *
     * @param type        "f" for file, "d" for directory
     * @param lastUpdated timestamp of the last update
     * @param firstChunk content of the file; null for a folder
     * @param children    list of children; null for a file
     * @param otherChunks map of sub channels; null for a folder
     */
    public record ChannelData(String type, long lastUpdated, byte[] firstChunk, Set<String> children, Map<Integer, String> otherChunks) {
        public boolean isFile() {
            return type.equals(FILE_TYPE);
        }

        public boolean isDir() {
            return type.equals(DIR_TYPE);
        }
    }

    public static String checkBalanceRho(String addr) {
        return new StringBuilder()
            .append("new return, rl(`rho:registry:lookup`), RevVaultCh, vaultCh in { ")
            .append("  rl!(`rho:rchain:revVault`, *RevVaultCh) | ")
            .append("  for (@(_, RevVault) <- RevVaultCh) { ")
            .append("    @RevVault!(\"findOrCreate\", \"")
            .append(addr) // insert balance address
            .append("\", *vaultCh) | ")
            .append("    for (@maybeVault <- vaultCh) { ")
            .append("      match maybeVault { ")
            .append("        (true, vault) => @vault!(\"balance\", *return) ")
            .append("        (false, err) => return!(err) ")
            .append("      } ")
            .append("    } ")
            .append("  } ")
            .append("}")
            .toString();
    }

    //** Creates a chanel with a file */
    public static String sendEmptyFileIntoNewChanel(String channelName, long lastUpdated) {
        // output looks like: @"path"!({"type":"f","firstChunk":[]}, "otherChunks":{}, "lastUpdated":123})
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!({\"")
            .append(TYPE)
            .append("\":\"")
            .append(FILE_TYPE)
            .append("\",\"")
            .append(FIRST_CHUNK)
            .append("\":[],\"")
            .append(OTHER_CHUNKS)
            .append("\":{},\"")
            .append(LAST_UPDATED)
            .append("\":")
            .append(lastUpdated)
            .append("})")
            .toString();
    }

    public static String sendDirectoryIntoNewChannel(String channelName, Set<String> children, long lastUpdated) {
        // output looks like: @"path"!({"type":"d","children":["a","b"],"lastUpdated":123})
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!({\"")
            .append(TYPE)
            .append("\":\"")
            .append(DIR_TYPE)
            .append("\",\"")
            .append(CHILDREN)
            .append("\":")
            .append(set2String(children))
            .append(",\"")
            .append(LAST_UPDATED)
            .append("\":")
            .append(lastUpdated)
            .append("})")
            .toString();
    }

    //** Consumes a value from a chanel */
    public static String forgetChanel(String chanel) {
        // output looks like for(@v <- @"path"){Nil}
        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("Nil")
            .append("}")
            .toString();
    }

    //** Updates a children field a chanel data */
    public static String updateChildren(String chanel, Set<String> newChildren, long lastUpdated) {
        return new StringBuffer()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(lastUpdated)
            .append(").set(\"")
            .append(CHILDREN)
            .append("\",")
            .append(set2String(newChildren))
            .append("))")
            .append("}")
            .toString();
    }

    //** Atomic Rename: Consume from old, send to new, and update parent children list */
    public static String atomicRename(String oldChanel, String newChanel, String parentChanel, Set<String> newChildren, long lastUpdated) {
        // output looks like:
        // for(@v <- @"oldPath"; @p <- @"parentPath"){
        //      @"newPath"!(v) |
        //      @"parentPath"!(p.set("children", ["new","list"]).set("lastUpdated", 123))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(oldChanel)
            .append("\"; @p <- @\"")
            .append(parentChanel)
            .append("\"){")
            .append("@\"")
            .append(newChanel)
            .append("\"!(v) | @\"")
            .append(parentChanel)
            .append("\"!(p.set(\"")
            .append(CHILDREN)
            .append("\",")
            .append(set2String(newChildren))
            .append(").set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(lastUpdated)
            .append("))}")
            .toString();
    }

    //** Consume a value from old chanel and send to a new one */
    public static String renameChanel(String oldChanel, String newChanel, long lastUpdated) {
        // output looks like:
        // for(@v <- @"oldPath"){
        //      @"newPath"!(v)
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(oldChanel)
            .append("\"){")
            .append("@\"")
            .append(newChanel)
            .append("\"!(v)")
            .append("}")
            .toString();
    }

    //** Atomic Delete: Consume from channel and update parent children list */
    public static String atomicDelete(String chanel, String parentChanel, Set<String> newChildren, long lastUpdated) {
        // output looks like:
        // for(@v <- @"path"; @p <- @"parentPath"){
        //      @"parentPath"!(p.set("children", ["new","list"]).set("lastUpdated", 123))
        // }
        return new StringBuilder()
            .append("for(@_ <- @\"")
            .append(chanel)
            .append("\"; @p <- @\"")
            .append(parentChanel)
            .append("\"){")
            .append("@\"")
            .append(parentChanel)
            .append("\"!(p.set(\"")
            .append(CHILDREN)
            .append("\",")
            .append(set2String(newChildren))
            .append(").set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(lastUpdated)
            .append("))}")
            .toString();
    }

    //** Consume a value from a channel and send to an appended value */
    public static String updateFileContent(String chanel, byte[] newChunk) {
        // SURGICAL GUARD: Ensure chunk is within safe gRPC limits (max 4MB hex string -> 2MB bytes)
        if (newChunk.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Chunk size exceeds safe 2MB limit for Rholang transmission");
        }
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("firstChunk", "base16encodedChunk".hexToBytes()))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(FIRST_CHUNK)
            .append("\",\"")
            .append(Hex.encodeHexString(newChunk))
            .append("\".hexToBytes()))}")
            .toString();
    }

    public static String updateOtherChunksMap(String chanel, Map<Integer, String> otherChunks) {
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("otherChunks", {1:"subChannel"}))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(OTHER_CHUNKS)
            .append("\",{")
            .append(
                otherChunks.entrySet().stream()
                    .map(e -> e.getKey() + ": " + string2RholngString(e.getValue()))
                    .collect(Collectors.joining(LIST_DELIMITER))
            )
            .append("}))}")
            .toString();
    }

    public static String sendFileContentChunk(String channel, byte[] chunk) {
        // output looks like:
        // @"channel"!("base16EncodedChunk".hexToBytes())

        return new StringBuilder()
            .append("@\"")
            .append(channel)
            .append("\"!(\"")
            .append(Hex.encodeHexString(chunk))
            .append("\".hexToBytes())")
            .toString();
    }

    @NotNull
    public static String string2RholngString(String stringValue) {
        // wraps a string with quotes

        // same as "\"" + stringValue + "\""
        return new StringBuilder()
            .append("\"")
            .append(stringValue)
            .append("\"")
            .toString();
    }

    public static String set2String(Set<String> values) {
        return new StringBuilder()
            .append("[")
            .append(String.join(LIST_DELIMITER,  values.stream().map(RholangExpressionConstructor::string2RholngString).collect(Collectors.toList())))
            .append("]")
            .toString();
    }

    static String map2String(Map<String, String> emap) {
        return new StringBuilder()
            .append("{")
            .append(
                emap.entrySet().stream()
                    .map(e -> string2RholngString(e.getKey()) + ": " + string2RholngString(e.getValue()))
                    .collect(Collectors.joining(LIST_DELIMITER))
            )
            .append("}")
            .toString();
    }



    /**
     * Processes a list of key-value pairs and converts them into ChannelData
     * 
     * @param keyValues List of key-value pairs from RhoTypes
     * @return ChannelData extracted from the key-value pairs
     * @throws IllegalArgumentException if required data is missing or invalid
     */
    private static @NotNull ChannelData buildChannelDataFromKeyValues(@NotNull List<RhoTypes.KeyValuePair> keyValues) 
            throws IllegalArgumentException {
        String type = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(TYPE))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGString())
            .orElseThrow(() -> new IllegalArgumentException("No type in channel data"));

        long lastUpdatedRaw = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(LAST_UPDATED))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGInt())
            .orElseThrow(() -> new IllegalArgumentException("No lastUpdated in channel data"));

        // AUTO-CALIBRATION: If value is in seconds (legacy), convert to milliseconds
        long lastUpdated = lastUpdatedRaw < 10000000000L ? lastUpdatedRaw * 1000 : lastUpdatedRaw;

        byte[] content = null;
        Set<String> children = null;
        Map<Integer, String> otherChunks = null;

        if (type.equals(FILE_TYPE)) {

            content = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(FIRST_CHUNK))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getGByteArray().toByteArray())
                .orElseThrow(() -> new IllegalArgumentException("No value in file data"));

            otherChunks = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(OTHER_CHUNKS))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getEMapBody().getKvsList().stream()
                    .collect(Collectors.toMap(
                        k -> (int) k.getKey().getExprs(0).getGInt(),
                        v -> v.getValue().getExprs(0).getGString()
                    )))
                .orElseThrow(() -> new IllegalArgumentException("No otherChunks in file data"));

        } else if (type.equals(DIR_TYPE)) {

            children = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(CHILDREN))
                .findFirst()
                .map(kv ->
                    kv.getValue().getExprs(0).getEListBody().getPsList().stream()
                        .map(p -> p.getExprs(p.getExprsCount() - 1).getGString())
                        .collect(Collectors.toSet()))
                .orElseThrow(() -> new IllegalArgumentException("No children in channel data"));

        } else {

            throw new IllegalArgumentException("Unknown type: " + type);

        }

        return new ChannelData(type, lastUpdated, content, children, otherChunks);
    }
    
    /**
     * Constructs a Rholang expression for transferring REV tokens between addresses
     * 
     * @param revAddrFrom source REV address
     * @param revAddrTo destination REV address
     * @param amount amount to transfer
     * @return Rholang expression for REV transfer
     * 
     * Output looks like:
     * new rl(`rho:registry:lookup`), RevVaultCh in {
     *   rl!(`rho:rchain:revVault`, *RevVaultCh) |
     *   for (@(_, RevVault) <- RevVaultCh) {
     *     new vaultCh, vaultTo, revVaultkeyCh, deployerId(`rho:rchain:deployerId`), deployId(`rho:rchain:deployId`) in {
     *       match ("1111uwQS3sRCQm36VkJPFQVVNWYbXQmp2EfP3c3JvcQkKJK6QNqZh", "1111uwRc5pUBYUT4ERVsmpPj1TD1cpQvQpSCVJwAhzp1Cpt8hXuVR", 100) {
     *         (revAddrFrom, revAddrTo, amount) => {
     *           @RevVault!("findOrCreate", revAddrFrom, *vaultCh) |
     *           @RevVault!("findOrCreate", revAddrTo, *vaultTo) |
     *           @RevVault!("deployerAuthKey", *deployerId, *revVaultkeyCh) |
     *           for (@vault <- vaultCh; key <- revVaultkeyCh; _ <- vaultTo) {
     *             match vault {
     *               (true, vault) => {
     *                 new resultCh in {
     *                   @vault!("transfer", revAddrTo, amount, *key, *resultCh) |
     *                   for (@result <- resultCh) {
     *                     match result {
     *                       (true , _  ) => deployId!((true, "Transfer successful (not yet finalized)."))
     *                       (false, err) => deployId!((false, err))
     *                     }
     *                   }
     *                 }
     *               }
     *               err => {
     *                 deployId!((false, "REV vault cannot be found or created."))
     *               }
     *             }
     *           }
     *         }
     *       }
     *     }
     *   }
     * }
     */
    public static String transfer(String revAddrFrom, String revAddrTo, long amount) {
        return new StringBuilder()
            .append("new rl(`rho:registry:lookup`), RevVaultCh in {")
            .append("rl!(`rho:rchain:revVault`, *RevVaultCh) | ")
            .append("for (@(_, RevVault) <- RevVaultCh) {")
            .append("new vaultCh, vaultTo, revVaultkeyCh, ")
            .append("deployerId(`rho:rchain:deployerId`), ")
            .append("deployId(`rho:rchain:deployId`) ")
            .append("in {")
            .append("match (\"")
            .append(revAddrFrom)
            .append("\", \"")
            .append(revAddrTo)
            .append("\", ")
            .append(amount)
            .append(") {")
            .append("(revAddrFrom, revAddrTo, amount) => {")
            .append("@RevVault!(\"findOrCreate\", revAddrFrom, *vaultCh) | ")
            .append("@RevVault!(\"findOrCreate\", revAddrTo, *vaultTo) | ")
            .append("@RevVault!(\"deployerAuthKey\", *deployerId, *revVaultkeyCh) | ")
            .append("for (@vault <- vaultCh; key <- revVaultkeyCh; _ <- vaultTo) {")
            .append("match vault {")
            .append("(true, vault) => {")
            .append("new resultCh in {")
            .append("@vault!(\"transfer\", revAddrTo, amount, *key, *resultCh) | ")
            .append("for (@result <- resultCh) {")
            .append("match result {")
            .append("(true, _) => deployId!((true, \"Transfer successful (not yet finalized).\"))")
            .append("(false, err) => deployId!((false, err))")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("err => {")
            .append("deployId!((false, \"REV vault cannot be found or created.\"))")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}}")
            .toString();
    }


    public static String readFromChannel(String channelName) {
        // output looks like: new return in { for (@v <<- @"path"){ return!(v) } }
        return new StringBuilder()
            .append("new return in {")
            .append("for (@v <= @\"")
            .append(channelName)
            .append("\"){")
            .append("return!(v)")
            .append("}")
            .append("}")
            .toString();
    }

    /**
     * Extract bytes from RhoTypes.Expr (equivalent to parseBytes but for exploratory deploy results)
     * 
     * @param expr The exploratory deploy result expression
     * @return Byte array extracted from the expression
     */
    public static @NotNull byte[] parseExploratoryDeployBytes(@NotNull RhoTypes.Expr expr) {
        if (!expr.hasGByteArray()) {
            throw new IllegalArgumentException("Expression does not contain byte array");
        }
        return expr.getGByteArray().toByteArray();
    }
    
    /**
     * Parse the result of an exploratory deploy directly into ChannelData
     * 
     * @param expr The result of an exploratory deploy
     * @return ChannelData parsed from the exploratory deploy result
     */
    public static @NotNull ChannelData parseExploratoryDeployResult(@NotNull RhoTypes.Expr expr) throws IllegalArgumentException {
        if (expr == null || !expr.hasEMapBody()) {
            throw new IllegalArgumentException("Invalid exploratory deploy result: not an EMap");
        }
        
        List<RhoTypes.KeyValuePair> keyValues = expr.getEMapBody().getKvsList();
        
        return buildChannelDataFromKeyValues(keyValues);
    }
}
