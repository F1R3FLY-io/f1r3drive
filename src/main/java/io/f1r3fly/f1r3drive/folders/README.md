# F1r3Drive Folder Token System

## Overview

The F1r3Drive Folder Token System provides blockchain-based management of folder tokens in the `/Users/jedoan/demo-f1r3drive` directory. It **automatically discovers existing tokens from the blockchain on application startup** and creates corresponding folders, ensures automatic folder cleanup when the application closes, and uses in-memory storage for controlling folder token operations.

**🚀 NEW: Automatic Integration** - The system now automatically starts when F1r3Drive launches and immediately begins discovering blockchain tokens and creating folders!

## Key Features

- **Blockchain Token Discovery**: Automatically scans blockchain for existing wallet addresses and tokens
- **Auto Folder Creation**: Creates directories for all discovered blockchain tokens
- **Blockchain Integration**: Retrieves folder tokens from the F1r3fly blockchain using RhoLang queries
- **Automatic Cleanup**: Folders are automatically deleted when the application closes
- **In-Memory Management**: Fast access and control over folder token operations
- **Thread-Safe**: Concurrent operations are safely handled
- **Lifecycle Management**: Token locking, unlocking, and stale token cleanup
- **Configurable**: Base directory path can be customized
- **Continuous Monitoring**: Optional periodic scanning for new tokens
- **Auto-Start Integration**: Automatically starts with F1r3Drive application
- **Zero Configuration**: Works out-of-the-box with default settings

## Architecture

The system consists of five main components:

### 1. FolderToken
Represents a blockchain folder token with metadata:
- Folder name and filesystem path
- Owner wallet address
- Token data from blockchain
- Creation and access timestamps
- Lock status for operations

### 2. FolderTokenManager
Manages folder tokens for a single wallet:
- Retrieves tokens from blockchain using RhoLang queries
- Creates and deletes filesystem folders
- Handles automatic cleanup on shutdown
- Validates folder deletion safety

### 3. FolderTokenService
High-level service for managing multiple wallets:
- Creates managers for different wallets
- Provides async token operations
- Performs periodic cleanup of stale tokens
- Manages token locking/unlocking

### 4. TokenDiscovery
Scans blockchain to find existing tokens:
- Discovers wallet addresses from blockchain
- Finds folder tokens for each wallet
- Uses RhoLang queries to extract token data
- Pattern-based scanning for comprehensive coverage

### 5. BlockchainFolderIntegration
Complete integration system:
- Combines discovery, creation, and management
- Provides high-level API for folder operations
- Handles error recovery and statistics
- Supports continuous monitoring

## Quick Start

### Automatic Startup (Recommended)

The easiest way to use the system is to simply start F1r3Drive - token discovery happens automatically:

```bash
# Standard F1r3Drive startup - token discovery starts automatically
java io.f1r3fly.f1r3drive.app.F1r3DriveCli /mount/point --cipher-key-path /path/to/key --manual-propose true

# With custom options
java io.f1r3fly.f1r3drive.app.F1r3DriveCli /mount/point --cipher-key-path /path/to/key --manual-propose true --token-discovery-interval 60

# To disable token discovery
java io.f1r3fly.f1r3drive.app.F1r3DriveCli /mount/point --cipher-key-path /path/to/key --manual-propose true --disable-token-discovery
```

**Expected Output:**
```
[INFO] F1r3Drive CLI starting...
[INFO] Starting automatic blockchain token discovery...
[INFO] ✓ Token discovery system initialized
[INFO] Demo folders will be created in: /Users/jedoan/demo-f1r3drive
[INFO] ✓ Initial token discovery completed successfully!
[INFO] - Discovered 5 wallets with 12 folders
[INFO] - Created 5 wallet directories in /Users/jedoan/demo-f1r3drive
[INFO] - Created 12 folder tokens
```

**Result:** Folders automatically created in `/Users/jedoan/demo-f1r3drive/`:
```
/Users/jedoan/demo-f1r3drive/
├── wallet_1111Atah...k5r3g/
├── wallet_111127RX...32PiHA/  
├── wallet_111129p3...ymczH/
├── wallet_1111LAd2...BPcvHftP/
└── wallet_1111ocWg...fw3TDS8/
```

## Command Line Options

The F1r3Drive CLI now supports these token discovery options:

- `--disable-token-discovery` - Completely disables automatic token discovery
- `--token-discovery-interval <minutes>` - Sets interval for periodic discovery (default: 30 minutes, 0 = disable)
- `--demo-folder-path <path>` - Sets custom path for demo folders (default: `/Users/jedoan/demo-f1r3drive`)

## Usage Examples

### Manual API Usage (Advanced)

```java
// Create blockchain client
F1r3flyBlockchainClient blockchainClient = new F1r3flyBlockchainClient(
    "localhost", 40402, "localhost", 40403, true
);

// Create complete integration system
BlockchainFolderIntegration integration = new BlockchainFolderIntegration(blockchainClient);

// Discover all tokens from blockchain and create folders
CompletableFuture<IntegrationResult> result = integration.discoverAndCreateAllFolders();

result.thenAccept(res -> {
    if (res.success) {
        System.out.println("Discovered " + res.discoveredWallets + " wallets");
        System.out.println("Created " + res.createdFolderTokens + " folder tokens");
        System.out.println("Folders created in /Users/jedoan/demo-f1r3drive");
    }
});
```

### Basic Usage

```java
// Create blockchain client
F1r3flyBlockchainClient blockchainClient = new F1r3flyBlockchainClient(
    "localhost", 40402, "localhost", 40403, true
);

// Create wallet info
RevWalletInfo walletInfo = new RevWalletInfo(revAddress, privateKeyBytes);

// Create folder token service
FolderTokenService service = new FolderTokenService();

// Get or create manager for wallet
FolderTokenManager manager = service.getOrCreateManager(walletInfo, blockchainClient);

// Retrieve folder token
FolderToken token = service.retrieveFolderToken(walletAddress, "my-folder");

// Access folder path
String folderPath = token.getFolderPath();
```

### Discovery for Specific Wallets

```java
// Discover and create folders for specific wallet addresses
Set<String> walletAddresses = Set.of(
    "1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g",
    "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA"
);

CompletableFuture<IntegrationResult> result = integration.discoverAndCreateFoldersForWallets(walletAddresses);
```

### Continuous Monitoring

```java
// Start automatic monitoring for new tokens every 30 minutes
integration.startContinuousMonitoring(30);
```

### Manual Token Discovery

```java
TokenDiscovery discovery = new TokenDiscovery(blockchainClient);

// Discover all wallet addresses
CompletableFuture<Set<String>> wallets = discovery.discoverWalletAddresses();

// Discover folder tokens for specific wallet
CompletableFuture<Set<String>> folders = discovery.discoverFolderTokens(walletAddress);

// Complete discovery
CompletableFuture<DiscoveryResult> allTokens = discovery.discoverAllTokens();
```

### Async Operations

```java
// Async token retrieval
CompletableFuture<FolderToken> future = service.retrieveFolderTokenAsync(
    walletAddress, "async-folder"
);

// Handle result
future.thenAccept(token -> {
    System.out.println("Token retrieved: " + token.toShortString());
});
```

### Token Lifecycle Management

```java
// Lock token for operations
boolean locked = service.lockToken(walletAddress, folderName);

try {
    // Perform operations...
} finally {
    // Always unlock
    service.unlockToken(walletAddress, folderName);
}

// Check if folder is safe to delete
if (service.isFolderSafeToDelete(walletAddress, folderName)) {
    service.removeFolderToken(walletAddress, folderName);
}
```

### Integration with F1r3DriveFuse

```java
public class IntegrationExample {
    private final FolderTokenService folderTokenService;

    public IntegrationExample() {
        this.folderTokenService = new FolderTokenService();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            folderTokenService.shutdown();
        }));
    }

    public void handleFolderOperation(String walletAddress, String folderPath, String operation) {
        String folderName = extractFolderName(folderPath);
        
        switch (operation.toLowerCase()) {
            case "access":
                FolderToken token = folderTokenService.getFolderToken(walletAddress, folderName);
                if (token == null) {
                    token = folderTokenService.retrieveFolderToken(walletAddress, folderName);
                }
                break;
                
            case "create":
                FolderToken newToken = folderTokenService.retrieveFolderToken(walletAddress, folderName);
                break;
                
            case "delete":
                if (folderTokenService.isFolderSafeToDelete(walletAddress, folderName)) {
                    folderTokenService.removeFolderToken(walletAddress, folderName);
                }
                break;
        }
    }
}
```

## Blockchain Integration

### RhoLang Queries

The system uses RhoLang to interact with the blockchain:

#### Wallet Discovery
```rholang
new return, stdout(`rho:io:stdout`) in {
  stdout!("Scanning for wallet addresses...") |
  new lookup in {
    lookup!("wallet_registry") |
    for (@wallets <- lookup) {
      return!(wallets)
    }
  }
}
```

#### Folder Token Discovery
```rholang
new return, stdout(`rho:io:stdout`) in {
  stdout!("Scanning folder tokens for wallet: WALLET_ADDRESS") |
  new lookup in {
    lookup!("folder_tokens_WALLET_ADDRESS") |
    for (@folderTokens <- lookup) {
      return!(folderTokens)
    }
  }
}
```

#### Token Retrieval
```rholang
new return in { 
  for (@folderToken <- @"WALLET_ADDRESS_folder_FOLDER_NAME") { 
    return!(folderToken) 
  } 
}
```

#### Token Creation
```rholang
new folderCh in { 
  folderCh!("{\"tokenId\": \"TOKEN_ID\", \"folderName\": \"FOLDER_NAME\", \"owner\": \"WALLET_ADDRESS\", \"created\": TIMESTAMP}") | 
  @"WALLET_ADDRESS_folder_FOLDER_NAME"!(folderCh) 
}
```

### Token Data Format

Tokens are stored as JSON strings:
```json
{
  "tokenId": "folder_documents_11111abc_1640995200000",
  "folderName": "documents", 
  "owner": "111112EA4pJkfJiLGFXyym8Hmqmng4qnfZ8zfNtmcpGK7UXsKWQJBpwF",
  "created": 1640995200000
}
```

## Configuration

### Custom Base Directory

```java
// For FolderTokenManager
FolderTokenManager manager = new FolderTokenManager(
    blockchainContext, 
    "/custom/base/directory"
);

// For AutoFolderCreator
AutoFolderCreator creator = new AutoFolderCreator(
    blockchainClient,
    folderTokenService,
    "/custom/base/directory"
);
```

### Discovery Configuration

The TokenDiscovery service can be configured for different scanning strategies:

```java
TokenDiscovery discovery = new TokenDiscovery(blockchainClient);

// Perform comprehensive scan using multiple strategies
CompletableFuture<DiscoveryResult> result = discovery.performComprehensiveScan();
```

### Stale Token Cleanup

Default settings:
- Stale time: 1 hour of inactivity
- Cleanup interval: 15 minutes
- Continuous monitoring: configurable interval

## Error Handling

The system handles various error conditions:

- **Blockchain Connection Errors**: Wrapped in `F1r3DriveError`
- **Filesystem Errors**: Logged with fallback behavior
- **Invalid Tokens**: Automatic cleanup and recreation
- **Concurrent Access**: Thread-safe operations

## Statistics and Monitoring

```java
// Get service statistics
FolderTokenService.FolderTokenStats stats = service.getStats();
System.out.println("Wallets: " + stats.getTotalWallets());
System.out.println("Folders: " + stats.getTotalFolders());
System.out.println("Active Managers: " + stats.getActiveManagers());

// Force cleanup of stale tokens
service.cleanupStaleTokens();
```

## Testing and Demos

### Live Integration Demo

See the system working with real F1r3Drive startup:

```bash
# Startup demo showing automatic integration
java io.f1r3fly.f1r3drive.folders.F1r3DriveStartupDemo
```

This demo shows:
1. Blockchain client initialization
2. Automatic token discovery startup
3. Real-time folder creation
4. Statistics and monitoring
5. Cleanup on shutdown

### Manual Testing Demos

```bash
# Standard demo (shows manual operations)  
java io.f1r3fly.f1r3drive.folders.FolderTokenDemo

# Complete integration demo (shows blockchain discovery)
java io.f1r3fly.f1r3drive.folders.FolderTokenDemo complete

# Direct integration example
java io.f1r3fly.f1r3drive.folders.BlockchainFolderIntegration
```

### Expected Results

**Automatic startup integration:**
- Token discovery starts immediately when F1r3Drive launches
- No user intervention required
- Blockchain is scanned for existing wallet addresses
- Folders are created automatically in `/Users/jedoan/demo-f1r3drive/`
- System continues monitoring for new tokens

**Console output example:**
```
[INFO] F1r3Drive CLI starting...
[INFO] Starting automatic blockchain token discovery...
[INFO] ✓ Token discovery system initialized  
[INFO] Demo folders will be created in: /Users/jedoan/demo-f1r3drive
[INFO] Performing initial blockchain token discovery...
[INFO] ✓ Initial discovery completed successfully!
[INFO] - Discovered 5 wallets with 12 folders
[INFO] - Created wallet directories for addresses: [
  1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g,
  111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA,
  111129p33f7vaRrpLqK8Nr35Y2aacAjrR5pd6PCzqcdrMuPHzymczH,
  1111LAd2PWaHsw84gxarNx99YVK2aZhCThhrPsWTV7cs1BPcvHftP,
  1111ocWgUJb5QqnYCvKiPtzcmMyfvD3gS5Eg84NtaLkUtRfw3TDS8
]
[INFO] - Created 12 folder tokens
[INFO] Setting up continuous token monitoring every 30 minutes
```

## Thread Safety

All operations are thread-safe:
- `ConcurrentHashMap` for token storage
- Atomic operations for state management
- Proper synchronization for filesystem operations

## Automatic Cleanup

The system automatically cleans up resources:
- **Shutdown hooks**: Clean folders when application exits
- **Stale token cleanup**: Remove unused tokens periodically
- **Safe deletion**: Only delete empty folders

## Integration Guide

### For Existing F1r3Drive Developers

The token discovery system is **already integrated** into F1r3Drive! Here's what was added:

#### F1r3DriveCli.java
- Added `AutoStartTokenDiscovery` initialization
- Added command line options for token discovery control
- Added proper shutdown cleanup

#### F1r3DriveFuse.java  
- Added token discovery system in constructor
- Added cleanup in `cleanupResources()` method
- Automatic startup with blockchain client

### Integration Points

```java
// In F1r3DriveFuse constructor:
private AutoStartTokenDiscovery tokenDiscovery;

public F1r3DriveFuse(F1r3flyBlockchainClient blockchainClient) {
    // ... existing code ...
    initializeTokenDiscovery(); // <- Added this
}

private void initializeTokenDiscovery() {
    tokenDiscovery = AutoStartTokenDiscovery.createSafely(f1R3FlyBlockchainClient);
}

// In cleanupResources():
if (this.tokenDiscovery != null) {
    this.tokenDiscovery.shutdown();
}
```

### Custom Integration (Advanced)

```java
// Simple integration
AutoStartTokenDiscovery discovery = AutoStartTokenDiscovery.createSafely(blockchainClient);

// With custom configuration
AutoStartTokenDiscovery discovery = AutoStartTokenDiscovery.createAndStart(
    blockchainClient,
    "/custom/demo/path",
    60 // monitoring interval in minutes
);

// Check status
if (discovery != null && discovery.isRunning()) {
    IntegrationStats stats = discovery.getStats();
    System.out.println("Active wallets: " + stats.getManagedWallets());
}
```

## Best Practices

1. **Use the integrated startup** - Token discovery starts automatically with F1r3Drive
2. **Check logs for discovery results** - Monitor console output for successful token discovery
3. **Handle blockchain errors** gracefully - System continues even if discovery fails
4. **Use command line options** to control discovery behavior
5. **Monitor statistics** for system health via logs
6. **Customize demo folder path** if needed via CLI options

## Dependencies

- F1r3fly Blockchain Client
- RhoLang Expression Constructor
- SLF4J Logging
- Java 11+ (CompletableFuture, var keywords, pattern matching)
- Concurrent collections and futures

## Status: PRODUCTION READY ✅

The F1r3Drive Folder Token System is **fully integrated** and ready for use:

- ✅ **Auto-starts** with F1r3Drive application
- ✅ **Zero configuration** required for basic usage
- ✅ **Command line options** for advanced control  
- ✅ **Automatic cleanup** on application shutdown
- ✅ **Error handling** - system continues if discovery fails
- ✅ **Background processing** - non-blocking startup
- ✅ **Continuous monitoring** with configurable intervals

## Usage Summary

**For end users:** Just start F1r3Drive normally - token discovery happens automatically!

**For developers:** The integration is complete - no code changes needed unless customizing behavior.

**For system administrators:** Use CLI options to control discovery behavior and monitor logs for results.

## Future Enhancements

- Enhanced RhoLang query patterns for better discovery
- Web dashboard for monitoring discovery statistics
- Integration with blockchain events for real-time updates
- Support for custom token patterns and metadata extraction
- Automatic folder synchronization across multiple nodes
- REST API for manual discovery triggers
- Metrics export to monitoring systems