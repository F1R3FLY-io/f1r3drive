# Physical Wallet Folder Architecture Overview

## 🏗️ Architecture Overview

This document provides a comprehensive architectural overview of the Physical Wallet Folder system for F1r3Drive, showing how it integrates with the existing blockchain infrastructure to create real filesystem folders with live wallet data.

## 📊 Current vs Target Architecture

### Current Architecture (Basic)
```
┌─────────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   F1r3Drive CLI     │────│  GenesisWallet   │────│  Physical       │
│                     │    │  Extractor       │    │  Folders        │
│ - Parse arguments   │    │                  │    │                 │
│ - Start services    │    │ - Extract addrs  │    │ - wallet_xxx/   │
│ - Handle shutdown   │    │ - Create folders │    │ - README.md     │
└─────────────────────┘    └──────────────────┘    │ - wallet_info   │
                                                    └─────────────────┘
```

### Target Architecture (Full Implementation)
```
┌─────────────────────────────────────────────────────────────────────┐
│                         F1r3Drive CLI Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ Argument Parser │  │ Service Manager │  │ Shutdown Handler    │  │
│  │ - Wallet addr   │  │ - Initialize    │  │ - Clean termination │  │
│  │ - Private key   │  │ - Start/Stop    │  │ - Save state        │  │
│  │ - Config opts   │  │ - Health check  │  │ - Release resources │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Core Wallet Management Layer                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │              PhysicalWalletManager                              ││
│  │  ┌───────────────────┐    ┌─────────────────────────────────┐  ││
│  │  │ Wallet Discovery  │    │      Wallet Operations         │  ││
│  │  │ - Genesis extract │    │ - Lock/Unlock wallets          │  ││
│  │  │ - Address valid   │    │ - Create folder structure      │  ││
│  │  │ - Filter specific │    │ - Update wallet metadata       │  ││
│  │  └───────────────────┘    └─────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Blockchain Integration Layer                    │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │ DeployDispatcher│  │StateChangeEvents│  │  BlockchainContext  │   │
│ │ - Deploy mgmt   │  │ - Balance change│  │  - Wallet info      │   │
│ │ - Queue monitor │  │ - File updates  │  │  - Connection state │   │
│ │ - Background    │  │ - Real-time     │  │  - Transaction hist │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │F1r3flyBlockchain│  │  TokenDiscovery │  │  FileExtractor      │   │
│ │Client           │  │  - Query tokens │  │  - Download files   │   │
│ │ - RPC calls     │  │  - Folder scan  │  │  - Verify hashes    │   │
│ │ - Deploy exec   │  │  - Real vs test │  │  - Update content   │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data Processing Layer                         │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │TokenBalanceExtr │  │BlockchainFileExtr│ │  FolderTokenExtr    │   │
│ │ - REV balances  │  │ - File discovery│  │  - Folder structure │   │
│ │ - Other tokens  │  │ - Content dwnld │  │  - Permissions      │   │
│ │ - Balance hist  │  │ - Metadata      │  │  - Hierarchy        │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │PhysicalTokenFile│  │PhysicalBcFile   │  │ WalletFolderStruct  │   │
│ │ - JSON format   │  │ - Binary/Text   │  │ - Directory tree    │   │
│ │ - Auto-update   │  │ - Version ctrl  │  │ - Permission model  │   │
│ │ - Lock handling │  │ - Sync status   │  │ - Access control    │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        File System Layer                           │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │WalletFileSystem │  │PhysicalFileOps  │  │ PathManager         │   │
│ │ - CRUD ops      │  │ - Create/Read   │  │ - Path resolution   │   │
│ │ - Permissions   │  │ - Write/Delete  │  │ - Navigation        │   │
│ │ - Locking       │  │ - Rename/Move   │  │ - Search            │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
│                                                                     │
│ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│ │   Statistics    │  │ Error Recovery  │  │  Security Manager   │   │
│ │ - Disk usage    │  │ - Corruption    │  │ - Key handling      │   │
│ │ - Performance   │  │ - Repair        │  │ - Access control    │   │
│ │ - Health check  │  │ - Validation    │  │ - Audit logging     │   │
│ └─────────────────┘  └─────────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Physical Storage Layer                       │
│                                                                     │
│           /Users/jedoan/demo-f1r3drive/ (Base Directory)            │
│                                                                     │
│  wallet_111127RX...nR32PiHA/     wallet_1111Atah...ikBk5r3g/       │
│  ├── .wallet_info                ├── .wallet_info                  │
│  ├── .locked                     ├── .unlocked                     │
│  ├── tokens/                     ├── tokens/                       │
│  │   ├── REV.token              │   ├── REV.token                  │
│  │   └── other_tokens/          │   └── custom_token.token         │
│  ├── folders/                   ├── folders/                       │
│  │   ├── documents/             │   ├── images/                    │
│  │   └── private/               │   └── contracts/                 │
│  ├── blockchain_files/          ├── blockchain_files/              │
│  │   ├── file1.txt             │   ├── document.pdf               │
│  │   └── metadata/              │   └── metadata/                  │
│  └── README.md                  └── README.md                      │
└─────────────────────────────────────────────────────────────────────┘
```

## 🔄 Data Flow Architecture

### Wallet Discovery and Creation Flow
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐
│  CLI Startup    │────▶│ Argument Parse  │────▶│ Service Init        │
│                 │     │                 │     │                     │
│ java -jar       │     │ - Rev address   │     │ - BlockchainClient  │
│ --rev-address   │     │ - Private key   │     │ - DeployDispatcher  │
│ 111127RX...     │     │ - Config opts   │     │ - EventsManager     │
└─────────────────┘     └─────────────────┘     └─────────────────────┘
                                                            │
                                                            ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐
│ Genesis Extract │◀────│ Wallet Decision │────▶│ Specific Wallet     │
│                 │     │                 │     │                     │
│ - Parse genesis │     │ if revAddress   │     │ - Single wallet     │
│ - Extract all   │     │ != null         │     │ - Create folder     │
│ - Create list   │     │                 │     │ - Unlock if key     │
└─────────────────┘     └─────────────────┘     └─────────────────────┘
         │                                                  │
         └──────────────────────┬───────────────────────────┘
                                ▼
                    ┌─────────────────────┐
                    │ Physical Folder     │
                    │ Creation            │
                    │                     │
                    │ - Create base dir   │
                    │ - Wallet folders    │
                    │ - Folder structure  │
                    │ - Metadata files    │
                    └─────────────────────┘
```

### Real-time Update Flow
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐
│ Blockchain      │────▶│ StateChange     │────▶│ Event Processing    │
│ Transaction     │     │ Detection       │     │                     │
│                 │     │                 │     │ - Filter wallet     │
│ - Balance chg   │     │ - Monitor chain │     │ - Update files      │
│ - File update   │     │ - Detect events │     │ - Notify system     │
│ - New folder    │     │ - Queue changes │     │ - Log changes       │
└─────────────────┘     └─────────────────┘     └─────────────────────┘
                                                            │
                                                            ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐
│ File System     │◀────│ Change Apply    │────▶│ Physical Update     │
│ Validation      │     │                 │     │                     │
│                 │     │ - Queue ops     │     │ - Write token file  │
│ - Check perms   │     │ - Batch updates │     │ - Update metadata   │
│ - Verify paths  │     │ - Error handle  │     │ - Sync directories  │
│ - Lock files    │     │ - Rollback      │     │ - Release locks     │
└─────────────────┘     └─────────────────┘     └─────────────────────┘
```

## 🏛️ Component Relationships

### Core Components
```
PhysicalWalletManager
├── LockedPhysicalWallet
│   ├── WalletFolderStructure
│   ├── BasicMetadata
│   └── LimitedOperations
│
├── UnlockedPhysicalWallet
│   ├── FullWalletAccess
│   ├── TokenDirectory
│   │   ├── PhysicalTokenFile (REV)
│   │   ├── PhysicalTokenFile (Other)
│   │   └── TokenBalanceExtractor
│   ├── FolderDirectory
│   │   ├── SubfolderStructure
│   │   └── FolderPermissions
│   ├── BlockchainFilesDirectory
│   │   ├── PhysicalBlockchainFile
│   │   └── BlockchainFileExtractor
│   └── RealTimeUpdates
│       ├── StateChangeEventProcessor
│       └── WalletFolderUpdateManager
│
└── WalletOperations
    ├── UnlockWallet(privateKey)
    ├── CreateFolderStructure()
    ├── ExtractTokenBalances()
    ├── DownloadBlockchainFiles()
    └── StartRealTimeMonitoring()
```

### Integration Points
```
F1r3Drive Ecosystem Integration:
├── BlockchainClient ──────────┐
├── DeployDispatcher ──────────┤
├── StateChangeEventsManager ──┤ ───▶ PhysicalWalletManager
├── BlockchainContext ─────────┤
└── TokenDiscovery ────────────┘

External Dependencies:
├── Java NIO (File Operations)
├── Jackson (JSON Processing)  
├── SLF4J (Logging)
└── JUnit (Testing)
```

## 🔐 Security Architecture

### Security Layers
```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Boundary                        │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  Input Valid    │  │  Key Management │  │  Access Control │  │
│  │                 │  │                 │  │                 │  │
│  │ - Address fmt   │  │ - Secure store  │  │ - File perms    │  │
│  │ - Path travers  │  │ - Key rotation  │  │ - User context  │  │
│  │ - Injection     │  │ - Memory clear  │  │ - Audit trail   │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ Data Protection │  │  Network Sec    │  │ Error Handling  │  │
│  │                 │  │                 │  │                 │  │
│  │ - Encrypt files │  │ - TLS/SSL       │  │ - No info leak  │  │
│  │ - Hash verify   │  │ - Cert valid    │  │ - Safe defaults │  │
│  │ - Backup        │  │ - Rate limit    │  │ - Fail secure   │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Private Key Handling Flow
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ CLI Input       │────▶│ Memory Storage  │────▶│ Validation      │
│                 │     │                 │     │                 │
│ --private-key   │     │ - Secure heap   │     │ - Format check  │
│ 357cdc4201...   │     │ - No swap       │     │ - Length verify │
│                 │     │ - Clear on exit │     │ - Crypto valid  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                            │
                                                            ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Cleanup         │◀────│ Wallet Unlock   │────▶│ Use & Store     │
│                 │     │                 │     │                 │
│ - Zero memory   │     │ - Sign test     │     │ - Session only  │
│ - GC force      │     │ - Derive pubkey │     │ - No plaintext  │
│ - Log scrub     │     │ - Verify match  │     │ - Encrypted     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## 📈 Performance Architecture

### Scalability Considerations
```
Performance Optimization Strategy:

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ Batch Operations│  │ Async Processing│  │ Caching Strategy    │
│                 │  │                 │  │                     │
│ - File I/O      │  │ - CompletableFut│  │ - Wallet metadata   │
│ - DB queries    │  │ - Thread pools  │  │ - Token balances    │
│ - Network calls │  │ - Non-blocking  │  │ - File checksums    │
└─────────────────┘  └─────────────────┘  └─────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ Resource Limits │  │ Progress Track  │  │ Error Recovery      │
│                 │  │                 │  │                     │
│ - Memory caps   │  │ - Status report │  │ - Retry logic       │
│ - Disk quotas   │  │ - Cancellation  │  │ - Partial recovery  │
│ - Rate limits   │  │ - Timeouts      │  │ - Data validation   │
└─────────────────┘  └─────────────────┘  └─────────────────────┘
```

### Resource Management
```
Memory Management:
├── Wallet Processing: Max 100MB per wallet
├── File Buffering: 8KB chunks for large files  
├── Token Cache: LRU with 1000 entry limit
└── Event Queue: Bounded at 10,000 events

Disk Management:
├── Folder Size Limits: 1GB per wallet default
├── File Count Limits: 10,000 files per wallet
├── Cleanup Policy: Remove old temp files
└── Space Monitoring: Alert at 80% usage

Network Management:
├── Connection Pooling: Max 10 concurrent
├── Request Rate Limiting: 100/minute
├── Timeout Configuration: 30s default
└── Retry Strategy: Exponential backoff
```

## 🧪 Testing Architecture

### Test Strategy Pyramid
```
                    ┌─────────────┐
                    │   E2E Tests │
                    │             │
                    │ - Full flow │
                    │ - Real BC   │
                    └─────────────┘
                  ┌─────────────────┐
                  │ Integration Tests│
                  │                 │
                  │ - Component int │
                  │ - Mock BC       │
                  │ - File system   │
                  └─────────────────┘
              ┌─────────────────────────┐
              │      Unit Tests         │
              │                         │
              │ - Individual methods    │
              │ - Mock dependencies     │
              │ - Edge cases           │
              │ - Error conditions     │
              └─────────────────────────┘
```

### Test Data Management
```
Test Environment Setup:
├── Mock Blockchain
│   ├── Genesis block with test wallets
│   ├── Predefined token balances  
│   ├── Sample blockchain files
│   └── Simulated state changes
│
├── Test Filesystem
│   ├── Temporary directories
│   ├── Cleanup automation
│   ├── Permission testing
│   └── Corruption simulation
│
└── Performance Testing
    ├── Large wallet datasets
    ├── Concurrent operations
    ├── Network failure simulation
    └── Resource exhaustion tests
```

## 📚 Configuration Architecture

### Configuration Hierarchy
```
Configuration Priority (High to Low):
1. CLI Arguments (--rev-address, --private-key)
2. Environment Variables (F1R3_WALLET_PATH)
3. Configuration Files (wallet-folder-config.properties)
4. Default Values (hardcoded in application)

Configuration Categories:
├── Wallet Management
│   ├── wallet.folder.base.path
│   ├── wallet.folder.auto.unlock
│   └── wallet.folder.max.size.mb
│
├── Blockchain Connection
│   ├── blockchain.timeout.seconds
│   ├── blockchain.retry.attempts
│   └── blockchain.rate.limit
│
├── Performance Tuning
│   ├── performance.batch.size
│   ├── performance.thread.pool.size
│   └── performance.cache.size
│
└── Security Settings
    ├── security.file.permissions
    ├── security.audit.enabled
    └── security.encryption.enabled
```

This architecture provides a solid foundation for implementing real blockchain-integrated physical wallet folders while maintaining security, performance, and maintainability.