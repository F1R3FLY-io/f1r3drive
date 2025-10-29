# F1r3Drive - Hierarchical Architecture Overview

## System Architecture

F1r3Drive is a FUSE-based filesystem that integrates with the F1r3fly blockchain network. The architecture follows a layered approach:

```
┌─────────────────────────────────────────────────────────────────┐
│                     User Application / OS                        │
│                  (Linux / macOS / Windows)                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                        FUSE Kernel
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      F1r3Drive Application                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Layer 1: Application Entry                              │  │
│  │ ├── F1r3DriveCli (CLI entry point)                      │  │
│  │ ├── F1r3DriveFuse (FUSE initialization)                 │  │
│  │ └── Language: Java 17+                                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │ Layer 2: FUSE Bridge (jnr-fuse)                         │  │
│  │ ├── AbstractFuseFS (FUSE operations wrapper)            │  │
│  │ ├── FuseStubFS (mounting/unmounting)                    │  │
│  │ ├── FuseCallbacks (kernel syscall handlers)             │  │
│  │ └── Language: Java 17+ with JNR (Java Native Runtime)   │  │
│  └──────────────────────────▬──────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │ Layer 3: Virtual Filesystem                             │  │
│  │ ├── InMemoryFileSystem (main FS implementation)         │  │
│  │ ├── FileSystem (interface for FS operations)            │  │
│  │ ├── readFile, writeFile, createFile, etc.              │  │
│  │ └── Language: Java 17+                                  │  │
│  └──────────────────────────┬──────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │ Layer 4: Filesystem Nodes (Path Hierarchy)              │  │
│  │ ├── Path (interface - base for all nodes)               │  │
│  │ ├── AbstractPath (common implementation)                │  │
│  │ ├── Directory (directories, wallets, etc.)              │  │
│  │ ├── File (regular files and blockchain data)            │  │
│  │ ├── RootDirectory (filesystem root)                     │  │
│  │ ├── LockedWalletDirectory (auth required)               │  │
│  │ ├── UnlockedWalletDirectory (authenticated access)      │  │
│  │ ├── BlockchainDirectory (blockchain data access)        │  │
│  │ ├── BlockchainFile (blockchain file operations)         │  │
│  │ ├── FetchedDirectory (cached blockchain data)           │  │
│  │ ├── FetchedFile (cached files)                          │  │
│  │ └── Language: Java 17+                                  │  │
│  └──────────────────────────┬──────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │ Layer 5: State Management (Background Processing)       │  │
│  │ ├── StateChangeEventsManager (event coordinator)        │  │
│  │ ├── EventQueue (async event queue)                      │  │
│  │ ├── BlockingEventQueue (queue implementation)           │  │
│  │ ├── EventProcessor (event handlers)                     │  │
│  │ ├── EventProcessorRegistry (processor registry)         │  │
│  │ ├── StateChangeEvents (event definitions)               │  │
│  │ └── Language: Java 17+ (ExecutorService, threads)       │  │
│  └──────────────────────────┬──────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │ Layer 6: Security & Encryption                          │  │
│  │ ├── AESCipher (encryption/decryption - singleton)       │  │
│  │ ├── SecurityUtils (permission validation)               │  │
│  │ └── Language: Java 17+                                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Layer 7: Error Handling                                 │  │
│  │ ├── F1r3DriveError (base exception)                     │  │
│  │ ├── PathNotFound, FileAlreadyExists                     │  │
│  │ ├── DirectoryNotEmpty, OperationNotPermitted            │  │
│  │ ├── PathIsNotAFile, PathIsNotADirectory                 │  │
│  │ ├── NoDataByPath, InvalidSigningKeyException            │  │
│  │ ├── F1r3flyDeployError                                  │  │
│  │ └── Language: Java 17+                                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                             │
                    gRPC API Connection
                             │
┌────────────────────────────▼────────────────────────────────────┐
│              Remote F1r3fly Shard (Blockchain)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Layer 8: Blockchain Integration                         │  │
│  │ ├── F1r3flyBlockchainClient (gRPC communication)        │  │
│  │ ├── BlockchainContext (wallet & deployment context)     │  │
│  │ ├── DeployDispatcher (deploy transaction queue)         │  │
│  │ ├── RevWalletInfo (wallet credentials)                  │  │
│  │ ├── PrivateKeyValidator (key validation)                │  │
│  │ ├── RholangExpressionConstructor (smart contracts)      │  │
│  │ └── Language: Java 17+ with Protocol Buffers & gRPC     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

### Write Operation Example
```
User writes file
    ↓
FUSE Kernel syscall
    ↓
Layer 1 (CLI/FUSE init)
    ↓
Layer 2 (FUSE Bridge) - converts kernel calls
    ↓
Layer 3 (Virtual FileSystem) - routes to appropriate node
    ↓
Layer 4 (Filesystem Nodes) - BlockchainFile, FetchedFile, etc.
    ↓
Layer 5 (State Management) - queues event asynchronously
    ↓
Layer 6 (Security) - validates permissions, encrypts if needed
    ↓
Layer 8 (Blockchain) - creates Rholang expression, deploys via gRPC
    ↓
F1r3fly Shard - executes transaction
    ↓
Event propagates back through State Manager
    ↓
Filesystem state updated
```

### Read Operation Example
```
User reads file
    ↓
FUSE Kernel syscall
    ↓
Layer 1 (CLI/FUSE init)
    ↓
Layer 2 (FUSE Bridge) - converts kernel calls
    ↓
Layer 3 (Virtual FileSystem) - finds correct path
    ↓
Layer 4 (Filesystem Nodes) - BlockchainFile queries blockchain
    ↓
Layer 8 (Blockchain) - sends read query via gRPC
    ↓
F1r3fly Shard - reads from state
    ↓
Response returned with file data
    ↓
Layer 6 (Security) - decrypts if needed
    ↓
Data returned to kernel
    ↓
User receives file content
```

## Component Relationships

### Entry Point
- **F1r3DriveCli** → parses command line arguments
- **F1r3DriveFuse** → manages lifecycle (mount/unmount)

### FUSE Bridge
- **FuseStubFS** → bridges FUSE kernel to Java layer
- **AbstractFuseFS** → implements FUSE operations
- **FuseCallbacks** → routes syscalls to handlers

### Filesystem Core
- **InMemoryFileSystem** → orchestrates all FS operations
- **RootDirectory** → entry point to virtual filesystem tree
- **Path hierarchy** → files, directories, wallets, blockchain data

### Wallet Access
```
RootDirectory
  ├── LockedWalletDirectory (revAddress only)
  │   ↓ [unlock with privateKey]
  │   └── UnlockedWalletDirectory (blockchain context available)
  │       ├── BlockchainDirectory (read/write blockchain data)
  │       └── BlockchainFile (individual file operations)
```

### State Synchronization
- **StateChangeEventsManager** → async event coordinator
- **EventQueue** → thread-safe event buffer
- **EventProcessor** → handles file creation, deletion, modification

### Blockchain Integration
- **F1r3flyBlockchainClient** → gRPC communication
- **DeployDispatcher** → queues and manages transactions
- **RholangExpressionConstructor** → builds smart contracts
- **RevWalletInfo** → manages wallet credentials

## Key Design Patterns

1. **Singleton Pattern**: AESCipher for global encryption instance
2. **Strategy Pattern**: EventProcessor implementations
3. **Composite Pattern**: Path node hierarchy
4. **Facade Pattern**: InMemoryFileSystem abstracts complexity
5. **Command Pattern**: RholangExpressionConstructor builds contracts
6. **Observer Pattern**: StateChangeEventsManager notifies processors
7. **Registry Pattern**: EventProcessorRegistry manages handlers
8. **Adapter Pattern**: AbstractFuseFS adapts FUSE to FileSystem interface

## Technology Stack

- **Language**: Java 17+
- **FUSE Bridge**: JNR (Java Native Runtime)
- **Blockchain Communication**: Protocol Buffers & gRPC
- **Smart Contracts**: Rholang (F1r3fly language)
- **Concurrency**: ExecutorService, BlockingQueue
- **Encryption**: AES (128/256-bit)
- **Build System**: Gradle with Shadow JAR

## File Organization

```
src/main/java/io/f1r3fly/f1r3drive/
├── app/                          # Entry point
│   ├── F1r3DriveCli.java
│   └── F1r3DriveFuse.java
├── fuse/                         # FUSE layer
│   ├── AbstractFuseFS.java
│   ├── FuseStubFS.java
│   ├── FuseCallbacks.java
│   └── struct/                   # FUSE data structures
├── filesystem/                   # Virtual filesystem
│   ├── InMemoryFileSystem.java
│   ├── FileSystem.java (interface)
│   ├── common/                   # Base path types
│   ├── local/                    # Local filesystem nodes
│   └── deployable/               # Blockchain nodes
├── blockchain/                   # Blockchain integration
│   ├── BlockchainContext.java
│   ├── client/
│   ├── wallet/
│   └── rholang/
├── background/state/             # State management
│   ├── StateChangeEventsManager.java
│   ├── EventQueue.java (interface)
│   └── EventProcessor.java (interface)
├── encryption/                   # Security
│   └── AESCipher.java
└── errors/                       # Error handling
    ├── F1r3DriveError.java
    ├── PathNotFound.java
    └── ...
```

## Deployment Flow

1. **User** starts application with mount point and blockchain connection details
2. **F1r3DriveCli** parses arguments and initializes AESCipher
3. **F1r3DriveFuse** creates blockchain client connection
4. **InMemoryFileSystem** initializes RootDirectory with wallet nodes
5. **FuseStubFS** mounts virtual filesystem to kernel
6. **User** can now interact with blockchain as local filesystem
7. **Wallet unlock** creates UnlockedWalletDirectory with blockchain context
8. **File operations** route through layers 2-8 with async state management
9. **Ctrl+C** triggers shutdown hook → unmount and cleanup