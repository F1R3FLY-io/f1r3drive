# Physical Wallet Folder Implementation Plan

## 📋 Overview

This document outlines the comprehensive plan to implement physical wallet folder creation and management for F1r3Drive, based on the `InMemoryFileSystem` architecture. The goal is to create actual filesystem folders that mirror the blockchain wallet structure with real content.

## 🎯 Current Status

### ✅ Completed Features
- [x] Genesis block wallet address extraction (`GenesisWalletExtractor`)
- [x] Basic physical folder creation for wallets
- [x] Conditional logic (specific wallet vs all wallets)
- [x] Integration with macOS CLI
- [x] Fallback to test wallet data

### 🔄 Current Issues
- Physical folders are created but contain only basic placeholder content
- No wallet unlocking mechanism
- No real blockchain data extraction
- Missing token files and balance information
- No real-time updates from blockchain changes

## 🚀 Implementation Phases

### **Phase 1: Core Wallet Infrastructure (CRITICAL - Week 1)**

#### 1.1 Wallet Unlocking System
**Priority: HIGH** 🔴

**Files to create/modify:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/PhysicalWalletManager.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/LockedPhysicalWallet.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/UnlockedPhysicalWallet.java`

**Implementation Details:**
```java
// Core method to implement
public CompletableFuture<UnlockedPhysicalWallet> unlockPhysicalWallet(
    String revAddress, 
    String privateKey,
    Path walletFolderPath
) throws InvalidSigningKeyException
```

**Features:**
- Validate private key against wallet address
- Create unlocked wallet folder structure
- Enable access to token directories
- Set up blockchain context for the wallet

#### 1.2 Wallet Folder Structure
**Priority: HIGH** 🔴

**Folder Structure to Implement:**
```
/Users/jedoan/demo-f1r3drive/
├── wallet_111127RX...nR32PiHA/                 # Wallet folder
│   ├── .wallet_info                           # Wallet metadata
│   ├── .locked                                # Lock status indicator
│   ├── tokens/                                # Token directory
│   │   ├── REV.token                         # REV balance file
│   │   └── other_tokens/                     # Other token files
│   ├── folders/                              # Sub-folder tokens
│   │   ├── documents/                        # Example folder
│   │   └── images/                           # Example folder
│   ├── blockchain_files/                     # Blockchain-stored files
│   └── README.md                             # Wallet information
```

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletFolderStructure.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/TokenFileManager.java`

#### 1.3 DeployDispatcher Integration
**Priority: HIGH** 🔴

**Files to modify:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/BlockchainFolderIntegration.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/GenesisWalletExtractor.java`

**Implementation:**
```java
// Add DeployDispatcher to handle blockchain operations
private final DeployDispatcher deployDispatcher;

// Method to initialize with blockchain context
public void initializeWithDeployDispatcher(DeployDispatcher deployDispatcher) {
    this.deployDispatcher = deployDispatcher;
    deployDispatcher.startBackgroundDeploy();
}
```

---

### **Phase 2: Real Blockchain Data Integration (IMPORTANT - Week 2)**

#### 2.1 Token Balance Extraction
**Priority: MEDIUM** 🟡

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/TokenBalanceExtractor.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/PhysicalTokenFile.java`

**Features:**
- Extract real REV balances from blockchain
- Discover other tokens owned by wallet
- Create token files with current balances
- Update token files when balances change

**Example Token File Content:**
```json
{
  "tokenType": "REV",
  "balance": "1000000000",
  "lastUpdated": "2023-12-19T14:39:00Z",
  "walletAddress": "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
  "blockHeight": 12345
}
```

#### 2.2 Blockchain File Discovery
**Priority: MEDIUM** 🟡

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/BlockchainFileExtractor.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/PhysicalBlockchainFile.java`

**Features:**
- Discover files stored on blockchain for each wallet
- Download and create physical copies
- Maintain file metadata (hash, size, timestamp)
- Handle file updates and versioning

#### 2.3 Folder Token Discovery
**Priority: MEDIUM** 🟡

**Enhancement to existing:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/TokenDiscovery.java`

**Features:**
- Discover actual folder tokens from blockchain
- Create corresponding physical directories
- Populate folders with real content
- Handle folder permissions and access

---

### **Phase 3: Real-time Updates & Monitoring (IMPORTANT - Week 3)**

#### 3.1 State Change Events Integration
**Priority: MEDIUM** 🟡

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/PhysicalWalletEventProcessor.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletFolderUpdateManager.java`

**Features:**
```java
// Event processor for wallet balance changes
stateChangeEventsManager.registerEventProcessor(
    StateChangeEvents.WalletBalanceChanged.class,
    new PhysicalWalletEventProcessor() {
        @Override
        public void processEvent(StateChangeEvents.WalletBalanceChanged event) {
            updatePhysicalTokenFiles(event.revAddress(), event.newBalance());
        }
    }
);
```

#### 3.2 Continuous Blockchain Monitoring
**Priority: MEDIUM** 🟡

**Enhancement to existing:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/AutoFolderCreator.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/BlockchainFolderIntegration.java`

**Features:**
- Monitor blockchain for new transactions affecting wallets
- Update physical files when blockchain state changes
- Handle new folder creation/deletion
- Sync file content changes

---

### **Phase 4: File Operations & Management (DESIRABLE - Week 4)**

#### 4.1 Physical File Operations
**Priority: LOW** 🟢

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/PhysicalFileOperations.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletFileSystem.java`

**Features:**
```java
// File operations similar to InMemoryFileSystem
public void createFile(String walletPath, String fileName, byte[] content)
public byte[] readFile(String walletPath, String fileName) 
public void writeFile(String walletPath, String fileName, byte[] content)
public void deleteFile(String walletPath, String fileName)
public void renameFile(String walletPath, String oldName, String newName)
```

#### 4.2 Search and Navigation
**Priority: LOW** 🟢

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletPathManager.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletContentSearcher.java`

**Features:**
- Find wallets by address or name
- Search files within wallet folders
- Navigate folder hierarchies
- Path resolution and validation

---

### **Phase 5: Advanced Features & Polish (OPTIONAL - Week 5)**

#### 5.1 Filesystem Statistics
**Priority: LOW** 🟢

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletFolderStats.java`

**Features:**
- Calculate disk usage per wallet
- Generate filesystem statistics
- Monitor folder sizes and file counts
- Performance metrics and reporting

#### 5.2 Lifecycle Management
**Priority: LOW** 🟢

**Files to modify:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/BlockchainFolderIntegration.java`

**Features:**
```java
public void terminate() {
    // Clean shutdown process
    waitOnBackgroundDeploy();
    destroyDeployDispatcher();
    cleanLocalCache();
    shutdownStateChangeEventsManager();
}
```

#### 5.3 Error Handling and Recovery
**Priority: LOW** 🟢

**Files to create:**
- `src/main/java/io/f1r3fly/f1r3drive/folders/WalletFolderRecovery.java`
- `src/main/java/io/f1r3fly/f1r3drive/folders/CorruptionDetector.java`

**Features:**
- Detect corrupted wallet folders
- Recover from incomplete operations
- Validate folder integrity
- Automatic repair mechanisms

---

## 📚 Key Classes to Study and Adapt

### From InMemoryFileSystem Architecture:

1. **`LockedWalletDirectory`** → `LockedPhysicalWallet`
   - Represents locked wallet with limited access
   - Requires private key for unlocking

2. **`UnlockedWalletDirectory`** → `UnlockedPhysicalWallet` 
   - Full access to wallet contents
   - Token directories and files available

3. **`TokenDirectory`** → `PhysicalTokenDirectory`
   - Physical folder containing token files
   - Handles balance updates and changes

4. **`TokenFile`** → `PhysicalTokenFile`
   - Individual token balance files
   - Synchronized with blockchain state

5. **`BlockchainFile`** → `PhysicalBlockchainFile`
   - Files stored on blockchain
   - Local physical copies with metadata

6. **`BlockchainContext`** → **Use existing**
   - Wallet information and deploy dispatcher
   - Connection to blockchain services

## 🔧 Technical Implementation Notes

### CLI Integration
**Modify:** `src/macos/java/io/f1r3fly/f1r3drive/app/F1r3DriveCli.java`

```java
// Enhanced CLI options
@Option(names = {"--unlock-wallet"}, description = "Automatically unlock wallet with provided private key")
private boolean unlockWallet = false;

@Option(names = {"--extract-tokens"}, description = "Extract real token balances")
private boolean extractTokens = true;

@Option(names = {"--monitor-changes"}, description = "Monitor blockchain changes in real-time")
private boolean monitorChanges = true;
```

### Configuration
**Create:** `src/main/resources/wallet-folder-config.properties`

```properties
# Physical Wallet Folder Configuration
wallet.folder.base.path=/Users/jedoan/demo-f1r3drive
wallet.folder.auto.unlock=false
wallet.folder.extract.tokens=true
wallet.folder.monitor.changes=true
wallet.folder.update.interval.minutes=5
wallet.folder.max.file.size.mb=100
```

### Testing Strategy
**Create test files:**
- `src/test/java/io/f1r3fly/f1r3drive/folders/PhysicalWalletManagerTest.java`
- `src/test/java/io/f1r3fly/f1r3drive/folders/TokenBalanceExtractorTest.java`
- `src/test/java/io/f1r3fly/f1r3drive/folders/WalletFolderIntegrationTest.java`

## 🎖️ Success Criteria

### Phase 1 Success Metrics:
- [ ] Can unlock wallets with private key
- [ ] Create proper folder structure for locked/unlocked wallets
- [ ] DeployDispatcher integration working

### Phase 2 Success Metrics:
- [ ] Real REV balances displayed in token files
- [ ] Blockchain files downloaded to physical folders
- [ ] Folder tokens created from real blockchain data

### Phase 3 Success Metrics:
- [ ] Real-time updates when wallet balances change
- [ ] Continuous monitoring detects new transactions
- [ ] Files automatically sync with blockchain state

### Overall Success:
- [ ] Physical folders contain real, up-to-date wallet data
- [ ] System works with both specific wallets and all-wallet discovery
- [ ] No more placeholder/mock data - everything from blockchain
- [ ] Robust error handling and recovery
- [ ] Performance suitable for production use

## 🚨 Risk Mitigation

### High-Risk Areas:
1. **Private Key Security** - Ensure keys are handled securely
2. **Blockchain Connection Failures** - Robust fallback mechanisms
3. **File System Permissions** - Handle access denied scenarios  
4. **Concurrent Access** - Thread-safe file operations
5. **Large Wallet Data** - Memory and disk usage optimization

### Mitigation Strategies:
- Comprehensive error handling and logging
- Extensive testing with mock blockchain data
- Gradual rollout with feature flags
- Performance monitoring and alerting
- User documentation and troubleshooting guides

## 📅 Timeline Summary

| Phase | Duration | Priority | Key Deliverables |
|-------|----------|----------|------------------|
| Phase 1 | Week 1 | 🔴 HIGH | Wallet unlocking, folder structure, DeployDispatcher |
| Phase 2 | Week 2 | 🟡 MEDIUM | Real blockchain data, token files, file extraction |
| Phase 3 | Week 3 | 🟡 MEDIUM | Real-time updates, monitoring, state events |
| Phase 4 | Week 4 | 🟢 LOW | File operations, search, navigation |
| Phase 5 | Week 5 | 🟢 OPTIONAL | Statistics, lifecycle, error recovery |

**Total Estimated Time: 3-5 weeks** (depending on scope)

---

*This plan transforms F1r3Drive from creating empty placeholder folders to generating fully functional physical wallet folders with real blockchain data and live updates.*