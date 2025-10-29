# F1r3Drive - Updated Architecture Documentation

## Overview

F1r3Drive is a distributed filesystem that integrates with the F1r3fly blockchain network. It provides a virtual filesystem interface through native platform APIs (File Provider on macOS, FUSE on Linux), allowing users to interact with blockchain storage as if it were a local directory.

**Key Architecture Principle:** Platform-specific filesystem mounting is abstracted through a unified `ChangeWatcher` layer, enabling consistent behavior across platforms while using native APIs for each OS.

## System Architecture Diagram

```
┌────────────────────────┐              ┌────────────────────────┐
│       Local - macOS    │              │      Local - Linux     │
├────────────────────────┤              ├────────────────────────┤
│ FSEvents               │              │ FUSE                   │
│ File Provider          │              │ inotify/fanotify       │
└────────────┬───────────┘              └────────────┬───────────┘
             │                                       │
             └───────────────┬─────────────────────┘
                             │
        ┌────────────────────▼────────────────────┐
        │   Layer 0: Platform Abstraction         │
        │   ChangeWatcher (unified interface)     │
        └────────────────────┬────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      F1r3Drive Application                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ jnr-fuse                                               │    │
│  │ (FUSE bridge - Linux only, abstracted by ChangeWatcher)    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 1: Application Entry                             │    │
│  │ ├── F1r3DriveCli (CLI entry point) [EXISTING]         │    │
│  │ ├── F1r3DriveFuse (Virtual FS initialization) [EXISTING]    │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 2: Change Listener (Event Routing)              │    │
│  │ ├── Receives events from ChangeWatcher               │    │
│  │ ├── Routes filesystem changes to handlers             │    │
│  │ ├── Bridges Platform Layer to Virtual Filesystem      │    │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 3: Virtual Filesystem Core                      │    │
│  │ ├── InMemoryFileSystem (main FS implementation) [EXISTING]  │
│  │ ├── FileSystem (interface for FS operations) [EXISTING]     │
│  │ ├── readFile, writeFile, createFile, deleteFile [EXISTING] │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 4: Filesystem Nodes (Path Hierarchy)            │    │
│  │ ├── Path (interface - base for all nodes) [EXISTING]  │    │
│  │ ├── AbstractPath (common implementation) [EXISTING]   │    │
│  │ ├── Directory, File, RootDirectory [EXISTING]         │    │
│  │ ├── LockedWalletDirectory, UnlockedWalletDirectory [EXISTING]   │
│  │ ├── BlockchainDirectory, BlockchainFile [EXISTING]    │    │
│  │ ├── FetchedDirectory, FetchedFile [EXISTING]          │    │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 5: State Management (Background Processing)     │    │
│  │ ├── StateChangeEventsManager (async event coordinator) [EXISTING]     │
│  │ ├── EventQueue (async event queue) [EXISTING]         │    │
│  │ ├── EventProcessor (event handlers) [EXISTING]        │    │
│  │ ├── EventProcessorRegistry (processor registry) [EXISTING]  │
│  │ └── Language: Java 17+ (ExecutorService, threads)     │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 6: Placeholder Manager [NEW]                    │    │
│  │ ├── Manages virtual file placeholders [NEW]           │    │
│  │ ├── Handles lazy loading of blockchain data [NEW]     │    │
│  │ ├── Caches fetched data efficiently [NEW]             │    │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 7: Security & Encryption                        │    │
│  │ ├── AESCipher (encryption/decryption - singleton) [EXISTING]  │
│  │ ├── SecurityUtils (permission validation) [EXISTING]  │    │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 8: Error Handling                               │    │
│  │ ├── F1r3DriveError (base exception) [EXISTING]        │    │
│  │ ├── PathNotFound, FileAlreadyExists [EXISTING]        │    │
│  │ ├── DirectoryNotEmpty, OperationNotPermitted [EXISTING]     │
│  │ └── Language: Java 17+                                │    │
│  └────────────────────────────────────────────────────────┘    │
│                             │                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ Layer 9: Blockchain Integration                       │    │
│  │ ├── F1r3flyBlockchainClient (gRPC communication) [EXISTING] │
│  │ ├── BlockchainContext (wallet & deployment context) [EXISTING]   │
│  │ ├── DeployDispatcher (deploy transaction queue) [EXISTING] │
│  │ ├── RevWalletInfo (wallet credentials) [EXISTING]     │    │
│  │ ├── RholangExpressionConstructor (smart contracts) [EXISTING]    │
│  │ └── Language: Java 17+ with Protocol Buffers & gRPC   │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                             │
                    gRPC API Connection
                             │
┌────────────────────────────▼────────────────────────────────────┐
│              Remote F1r3fly Shard (Blockchain)                  │
│                                                                 │
│  • Executes Rholang contracts                                  │
│  • Manages state transactions                                  │
│  • Returns query results via gRPC                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## macOS Native Implementation (Primary)

### Platform Abstraction Layer (Layer 0) - macOS Focus

The **ChangeWatcher** is the unified interface that abstracts macOS filesystem APIs:

**On macOS:**
```
┌─────────────────────────────────────┐
│   macOS File System Events          │
├─────────────────────────────────────┤
│                                     │
│  FSEvents Monitor [NEW]             │
│  ├── Monitors directory changes [NEW]     │
│  ├── Detects file access [NEW]      │
│  └── Real-time notifications [NEW]  │
│                                     │
│  File Provider Integration [NEW]    │
│  ├── Virtual mount point creation [NEW]   │
│  ├── Placeholder file management [NEW]    │
│  └── On-demand file loading [NEW]   │
│                                     │
└────────────┬────────────────────────┘
             │
    ┌────────▼─────────┐
    │  ChangeWatcher [NEW]   │
    │  (unified API)   │
    └────────┬─────────┘
             │
    ┌────────▼─────────────────────┐
    │   change listener [NEW]      │
    │   (event routing)            │
    └──────────────────────────────┘
```

### What We Have (macOS):

✅ **FSEvents Support** [NEW]
- Real-time directory monitoring [NEW]
- Low overhead file system observation [NEW]
- Integrated with macOS kernel [NEW]

✅ **File Provider Framework** [NEW]
- Virtual filesystem mounting capability [NEW]
- Lazy loading support for blockchain data [NEW]
- Placeholder management infrastructure [NEW]

✅ **Native Integration Points** [NEW]
- Direct macOS framework access [NEW]
- No intermediate layers needed [NEW]
- Efficient memory and CPU usage [NEW]

### Data Flow - macOS Write Operation

```
User writes file in Finder/Terminal
    ↓
File Provider receives write operation
    ↓
ChangeWatcher intercepted event
    ↓
Layer 2 (change listener) processes event notification
    ↓
Layer 3 (InMemoryFileSystem) routes to appropriate node
    ↓
Layer 4 (Filesystem Nodes) handles operation
    (BlockchainFile → prepare for deployment)
    ↓
Layer 6 (placeholder manager) updates metadata
    ↓
Layer 5 (State Management) queues event asynchronously
    ↓
Layer 7 (Security) validates permissions, encrypts if needed
    ↓
Layer 9 (Blockchain) creates Rholang expression, deploys via gRPC
    ↓
F1r3fly Shard executes transaction
    ↓
Event propagates back through State Manager
    ↓
Filesystem state synchronized
    ↓
File Provider syncs changes back to macOS
```

### Data Flow - macOS Read Operation

```
User opens file in Finder/Terminal
    ↓
File Provider checks cache
    ↓
If placeholder → File Provider calls ChangeWatcher fetch callback
    ↓
Layer 2 (change listener) requests data load
    ↓
Layer 6 (placeholder manager) marks as loading
    ↓
Layer 9 (Blockchain) sends read query via gRPC
    ↓
F1r3fly Shard returns file data
    ↓
Layer 6 (placeholder manager) replaces placeholder with real file
    ↓
Layer 7 (Security) decrypts if needed
    ↓
File Provider updates cache
    ↓
User receives file content
```

---

## Component Relationships

### macOS Entry Point
- **F1r3DriveCli** [EXISTING] → parses command line arguments
- **F1r3DriveFuse** [EXISTING] → manages lifecycle, initializes ChangeWatcher [UPDATE needed to initialize ChangeWatcher]
- **ChangeWatcher** [NEW] → activates FSEvents + File Provider

### Platform Abstraction (Layer 0) - macOS
- **ChangeWatcher** [NEW interface] → unified interface wrapping FSEvents and File Provider
- **MacOSFileSystemWatcher** [NEW implementation] → implements ChangeWatcher for macOS
  - `onFileCreated(path)` [NEW] - handles file creation events
  - `onFileModified(path)` [NEW] - handles file modification events
  - `onFileDeleted(path)` [NEW] - handles file deletion events
- **change listener** [NEW] → routes ChangeWatcher events to StateChangeEventsManager
  - `handleFileSystemEvent(event)` [NEW] - routes event to appropriate handler
  - `notifyStateManager(event)` [NEW] - updates state management

### Filesystem Core
- **InMemoryFileSystem** [EXISTING] → orchestrates all FS operations
  - `createFile(path)` [EXISTING]
  - `readFile(path)` [EXISTING]
  - `writeFile(path, data)` [EXISTING]
  - `deleteFile(path)` [EXISTING]
  - `notifyPlaceholders(path)` [UPDATE needed for placeholder integration]
- **RootDirectory** [EXISTING] → entry point to virtual filesystem tree
- **Path hierarchy** [EXISTING] → files, directories, wallets, blockchain data

### File Availability Management
```
InMemoryFileSystem
  ├── placeholder manager [NEW] (manages virtual files)
  │   ├── createPlaceholder(path, metadata) [NEW]
  │   ├── replacePlaceholder(path, actualFile) [NEW]
  │   ├── updateLoadingState(path, state) [NEW]
  │   └── Integrates with File Provider [NEW]
  │
  ├── change listener [NEW] (event routing)
  │   ├── Receives ChangeWatcher notifications [NEW]
  │   ├── Routes to StateChangeEventsManager [NEW]
  │   └── Triggers placeholder updates [NEW]
  │
  └── StateChangeEventsManager [EXISTING]
      ├── Queues file operations asynchronously [EXISTING]
      ├── Maintains consistency [EXISTING]
      └── Propagates state changes [EXISTING]
```

### Wallet Access Pattern
```
RootDirectory
  ├── LockedWalletDirectory (revAddress only)
  │   ↓ [unlock with privateKey]
  │   └── UnlockedWalletDirectory (blockchain context available)
  │       ├── BlockchainDirectory (read/write blockchain data)
  │       └── BlockchainFile (individual file operations)
```

### State Synchronization
- **StateChangeEventsManager** [EXISTING] → async event coordinator
  - `queue(event)` [EXISTING]
  - `processAsync()` [EXISTING]
  - `registerProcessor(type, processor)` [EXISTING]
- **EventQueue** [EXISTING] → thread-safe event buffer
  - `put(event)` [EXISTING]
  - `take()` [EXISTING]
- **EventProcessor** [EXISTING] → handles file creation, deletion, modification
- **change listener** [NEW] → bridges ChangeWatcher and StateChangeEventsManager
  - `onChangeWatcherEvent(event)` [NEW] - receives events from ChangeWatcher
  - `convertToStateEvent(event)` [NEW] - converts platform events to state events

### Blockchain Integration
### Blockchain Integration (Layer 9)
- **F1r3flyBlockchainClient** [EXISTING] → gRPC communication
  - `query(request)` [EXISTING]
  - `deploy(contract)` [EXISTING]
  - `notifyFileChange(path, data)` [UPDATE needed to notify on file changes]
- **DeployDispatcher** [EXISTING] → queues and manages transactions
  - `queue(deploy)` [EXISTING]
  - `processQueue()` [EXISTING]
- **RholangExpressionConstructor** [EXISTING] → builds smart contracts
  - `buildFromFileOperation(operation)` [EXISTING]
  - `buildCreateExpression(path)` [EXISTING]
  - `buildDeleteExpression(path)` [EXISTING]
- **RevWalletInfo** [EXISTING] → manages wallet credentials
  - `unlock(privateKey)` [EXISTING]
  - `getContext()` [EXISTING]

---

## Key Design Patterns

1. **Adapter Pattern** [NEW]: ChangeWatcher adapts macOS native APIs (FSEvents, File Provider) to unified interface
2. **Facade Pattern** [EXISTING]: InMemoryFileSystem abstracts complexity of node hierarchy
3. **Composite Pattern** [EXISTING]: Path node hierarchy for tree structure
4. **Strategy Pattern** [EXISTING]: EventProcessor implementations for different event types
5. **Observer Pattern** [EXISTING/UPDATE]: StateChangeEventsManager notifies processors
   - UPDATE: add notification for placeholder changes
   - UPDATE: add notification for ChangeWatcher events
6. **Command Pattern** [EXISTING]: RholangExpressionConstructor builds contracts
7. **Registry Pattern** [EXISTING]: EventProcessorRegistry manages handlers
8. **Singleton Pattern** [EXISTING]: AESCipher for global encryption instance
9. **Bridge Pattern** [NEW]: ChangeWatcher bridges platform-specific implementations to platform-agnostic application code

---

## Technology Stack (macOS)

- **Language**: Java 17+
- **Platform Abstraction**: ChangeWatcher interface
- **macOS Native Integration**: 
  - FSEvents for file monitoring
  - File Provider API for virtual filesystem
  - Foundation framework via JNI
- **Blockchain Communication**: Protocol Buffers & gRPC
- **Smart Contracts**: Rholang (F1r3fly language)
- **Concurrency**: ExecutorService, BlockingQueue
- **Encryption**: AES (128/256-bit)
- **Build System**: Gradle with Shadow JAR

---

## File Organization

```
src/main/java/io/f1r3fly/f1r3drive/
├── app/                              # Entry point
│   ├── F1r3DriveCli.java
│   └── F1r3DriveFuse.java
│
├── platform/                         # Platform Abstraction Layer (Layer 0)
│   ├── ChangeWatcher.java (interface) [NEW]
│   ├── MacOSFileSystemWatcher.java   # macOS implementation [NEW]
│   ├── macos/
│   │   ├── FSEventsMonitor.java [NEW]
│   │   └── FileProviderIntegration.java [NEW]
│   └── ChangeListener.java (interface) [NEW]
│
├── filesystem/                       # Virtual Filesystem (Layers 3-4)
│   ├── InMemoryFileSystem.java [EXISTING]
│   ├── FileSystem.java (interface) [EXISTING]
│   ├── common/                       # Base path types [EXISTING]
│   ├── local/                        # Local filesystem nodes [EXISTING]
│   └── deployable/                   # Blockchain nodes [EXISTING]
│
├── placeholder/                      # Placeholder Manager (Layer 6)
│   ├── PlaceholderManager.java [NEW]
│   └── PlaceholderInfo.java [NEW]
│
├── state/                            # State Management (Layer 5)
│   ├── StateChangeEventsManager.java [EXISTING]
│   ├── EventQueue.java (interface) [EXISTING]
│   ├── BlockingEventQueue.java [EXISTING]
│   ├── EventProcessor.java (interface) [EXISTING]
│   ├── EventProcessorRegistry.java [EXISTING]
│   └── StateChangeEvents.java [EXISTING]
│
├── blockchain/                       # Blockchain Integration (Layer 9)
│   ├── BlockchainContext.java [EXISTING]
│   ├── client/ [EXISTING]
│   ├── wallet/ [EXISTING]
│   └── rholang/ [EXISTING]
│
├── encryption/                       # Security (Layer 7)
│   └── AESCipher.java [EXISTING]
│
└── errors/                           # Error Handling (Layer 8)
     ├── F1r3DriveError.java [EXISTING]
     ├── PathNotFound.java [EXISTING]
     └── ... [EXISTING]
```

---

## Deployment Flow (macOS)

1. **User** starts application with mount point and blockchain connection details
2. **F1r3DriveCli** [EXISTING] parses arguments and initializes AESCipher
3. **F1r3DriveFuse** [EXISTING - UPDATE] creates blockchain client connection and initializes ChangeWatcher
4. **ChangeWatcher** [NEW] (MacOSChangeWatcher) initializes:
   - FSEvents monitor [NEW] starts watching directories
   - File Provider integration [NEW] activates
5. **InMemoryFileSystem** [EXISTING] initializes RootDirectory with wallet nodes
6. **placeholder manager** [NEW] pre-populates filesystem with blockchain file placeholders
7. **File Provider** [NEW] mounts virtual filesystem to Finder
8. **change listener** [NEW] connects ChangeWatcher events to StateChangeEventsManager
9. **User** can now interact with blockchain as local filesystem in Finder
10. **Wallet unlock** [EXISTING] creates UnlockedWalletDirectory with blockchain context
11. **File operations** [EXISTING] route through layers with async state management
12. **File access** [NEW] triggers placeholder loading if needed
13. **Cmd+C** triggers shutdown hook → unmount and cleanup [EXISTING with UPDATE for ChangeWatcher cleanup]

---

## macOS Integration Points

### File Provider + ChangeWatcher
```java
// When user accesses file in Finder
FileProvider receives request
  → ChangeWatcher.onFileAccess(path) [NEW]
    → change listener.handleAccess(event) [NEW]
      → StateChangeEventsManager.queue(event) [EXISTING]
        → EventProcessor handles the operation [EXISTING]
          → InMemoryFileSystem updates state [EXISTING]
            → placeholder manager updates placeholder [NEW]
              → File Provider syncs to Finder [NEW]
```

### Blockchain State Changes
```java
// When blockchain state changes
F1r3flyBlockchainClient receives update [EXISTING - UPDATE to notify on changes]
  → StateChangeEventsManager.queue(blockchainEvent) [EXISTING]
    → EventProcessor handles blockchain event [EXISTING]
      → InMemoryFileSystem updates FS state [EXISTING]
        → change listener notifies File Provider [NEW]
          → File Provider updates Finder view [NEW]
```

---

## Current Implementation Status

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| F1r3DriveCli | ✅ Implemented | `app/` | Entry point |
| F1r3DriveFuse | ✅ Implemented | `app/` | Lifecycle management - **UPDATE: initialize ChangeWatcher** |
| InMemoryFileSystem | ✅ Implemented | `filesystem/` | Core FS logic - **UPDATE: placeholder integration** |
| Path hierarchy | ✅ Implemented | `filesystem/` | All node types |
| StateChangeEventsManager | ✅ Implemented | `state/` | Event coordination - **UPDATE: add change listener** |
| AESCipher | ✅ Implemented | `encryption/` | Encryption/decryption |
| F1r3DriveError | ✅ Implemented | `errors/` | Error handling |
| F1r3flyBlockchainClient | ✅ Implemented | `blockchain/` | gRPC communication - **UPDATE: file change notifications** |
| **ChangeWatcher** | 🔄 NEW | `platform/` | **Interface needed** |
| **MacOSChangeWatcher** | 🔄 NEW | `platform/macos/` | **Needs implementation** |
| **change listener** | 🔄 NEW | `state/` | **Needs implementation** |
| **placeholder manager** | 🔄 NEW | `placeholder/` | **Needs implementation** |
| **FSEventsMonitor** | 🔄 NEW | `platform/macos/` | **Needs implementation** |
| **FileProviderIntegration** | 🔄 NEW | `platform/macos/` | **Needs implementation** |

---

## Appendix A: Linux Implementation Notes

⚠️ **This section documents Linux-specific architecture for reference.**

### Linux Platform Implementation

Linux uses a different approach with FUSE kernel module abstraction:

```
ChangeWatcher (Abstract Interface)
│
└─ LinuxFileSystemWatcher (Future Implementation)
   ├── FUSE Bridge (Linux kernel module via jnr-fuse)
   │   └── jnr-fuse wraps kernel FUSE in Java
   ├── inotify/fanotify Monitor
   │   └── Monitors filesystem changes at kernel level
   └── change listener (same interface as macOS)
       └── Routes events to StateChangeEventsManager
```

### Linux Architecture Layers

- **Layer 0**: ChangeWatcher abstraction (same as macOS) [EXISTING for Core]
- **Layer 1**: F1r3DriveCli [EXISTING], F1r3DriveFuse [EXISTING]
- **jnr-fuse**: FUSE bridge (Linux only - not exposed to application layers) [EXISTING]
- **Layer 2**: change listener (same as macOS) [NEW]
- **Layer 3**: InMemoryFileSystem (same as macOS) [EXISTING]
- **Layer 4**: Filesystem Nodes (same as macOS) [EXISTING]
- **Layers 5-9**: Same as macOS [EXISTING]

### Linux Data Flow (High-level)

```
Linux FUSE Kernel
    ↓
jnr-fuse (Java binding)
    ↓
ChangeWatcher (Linux implementation)
    ↓
change listener (unified interface)
    ↓
StateChangeEventsManager (same)
    ↓
InMemoryFileSystem (same)
```

### Key Linux Differences

| Component | macOS | Linux |
|-----------|-------|-------|
| **FS Monitoring** | FSEvents | inotify/fanotify |
| **Virtual FS** | File Provider API | FUSE kernel module |
| **Bridge Layer** | ChangeWatcher (native) | ChangeWatcher (via jnr-fuse) |
| **Event Handler** | change listener | change listener (same) |
| **Placeholder Mgmt** | placeholder manager | placeholder manager (same) |

**Note**: The jnr-fuse dependency (already in `build.gradle`) [EXISTING] handles Linux FUSE abstraction. The ChangeWatcher pattern ensures that both platforms use the same application-layer code.

---

## Summary

F1r3Drive's architecture provides a clean separation of concerns through a layered approach with platform-native implementations:

- **Layer 0**: Platform abstraction (ChangeWatcher)
- **Layers 1-2**: Application entry and event routing
- **Layers 3-4**: Virtual filesystem implementation
- **Layer 5**: Asynchronous state management
- **Layer 6**: Placeholder management
- **Layers 7-8**: Security and error handling
- **Layer 9**: Blockchain integration

**For macOS** (primary focus):
- Uses native FSEvents for monitoring
- Uses native File Provider for virtual filesystem
- No intermediate FUSE layer needed
- Direct, efficient integration with macOS APIs

**For Linux** (abstracted):
- Uses FUSE kernel module (via jnr-fuse)
- Uses inotify/fanotify for monitoring
- jnr-fuse bridge is transparent to application code

The `ChangeWatcher` pattern unifies platform-specific implementations, allowing the core filesystem logic to remain platform-agnostic while supporting platform-specific optimizations.