# F1r3Drive Architecture

This document describes the high-level architecture of the F1r3Drive application, focusing on the relationships between classes and their responsibilities.

## Overview

F1r3Drive is a blockchain-integrated file system that acts as a bridge between a local macOS environment (via FileProvider) and the F1r3fly blockchain. It maintains an in-memory representation of the file system which is synchronized with the blockchain state.

## Core Components

### 1. File System Core (`io.f1r3fly.f1r3drive.filesystem`)

The core logic handles file operations, structural hierarchy, and state management.

*   **`FileSystem` (Interface)**: Defines standard file system operations (create, read, write, delete, etc.).
*   **`InMemoryFileSystem`**: The primary implementation of `FileSystem`. It holds the root of the file tree and manages the lifecycle of files and directories. It is responsible for:
    *   Routing operations to specific nodes.
    *   Managing cache invalidation (`CacheStrategy`).
    *   Coordinating between local changes and blockchain updates.
    *   Handling "Placeholders" for the macOS FileProvider.

### 2. File System Nodes (`io.f1r3fly.f1r3drive.filesystem.common` & `.deployable` & `.local`)

The file tree is built from nodes implementing `Path`, `File`, or `Directory` interfaces.

*   **`AbstractPath`**: Base class for all nodes, holding common metadata (name, parent, lastUpdated).
*   **Deployable Nodes** (Blockchain-backed):
    *   **`AbstractDeployablePath`**: Base class for nodes that sync with the blockchain. Handles Rholang deployment queuing.
    *   **`BlockchainDirectory`**: Represents a folder on the blockchain. Manages children and syncs structure changes.
    *   **`BlockchainFile`**: Represents a file on the blockchain. Manages content chunks, encryption, and local caching.
*   **Local Nodes** (Local-only):
    *   **`AbstractLocalPath`**: Base class for nodes that exist only locally or have special logic.
    *   **`TokenDirectory`** (`.tokens`): A special directory that visualizes wallet balance as token files. It is read-only and regenerates its content based on blockchain balance.
    *   **`TokenFile`**: Represents a specific amount of REV tokens.

### 3. Blockchain Integration (`io.f1r3fly.f1r3drive.blockchain`)

Handles communication with the F1r3fly blockchain node.

*   **`BlockchainContext`**: Holds references to the client, wallet info, and dispatcher. Accessible to all file system nodes.
*   **`F1r3flyBlockchainClient`**: Low-level client for gRPC/HTTP communication with the node.
*   **`DeployDispatcher`**: Manages a queue of Rholang deployments to ensure sequential and reliable execution of blockchain transactions.
*   **`RholangExpressionConstructor`**: Helper to generate Rholang code for file operations (create, write, delete, check balance).

### 4. Platform Integration (`io.f1r3fly.f1r3drive.platform` & `.placeholder`)

Bridges the Java application with the macOS operating system.

*   **`F1r3DriveChangeListener`**: Listens for file system events from the OS. It filters noise (like `.DS_Store`) and triggers syncing logic in `FileSystem` or `PlaceholderManager`.
*   **`FileProviderIntegration`**: Manages the macOS FileProvider extension, creating "placeholder" files that look like real files but load content on demand.
*   **`MacOSChangeWatcher`**: Main platform abstraction for macOS.
*   **`FSEventsMonitor`**: Low-level monitoring of file system events via JNI.

## Architecture Diagram

```
┌─────────────────────────────────────┐
│       macOS (Local System)          │
│                                     │
│  FSEvents         FileProvider      │
│     │                  │            │
└─────┼──────────────────┼────────────┘
      │                  │
      ▼                  ▼
┌─────────────────────────────────────┐
│   Platform Integration Layer        │
│                                     │
│  FSEventsMonitor   FileProviderInt. │
│         │              │            │
│         ▼              │            │
│    MacOSChangeWatcher  │            │
│         │              │            │
│         ▼              ▼            │
│     F1r3DriveChangeListener         │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│   Core File System (InMemoryFS)     │
│                                     │
│        PlaceholderManager           │
│                │                    │
│    ┌───────────┴─────────────┐      │
│    │  File System Tree       │      │
│    │                         │      │
│    │  [Root]                 │      │
│    │     │                   │      │
│    │  [Wallet]               │      │
│    │     ├── .tokens         │      │
│    │     └── [BlockchainDir] │      │
│    │             │           │      │
│    │         [BlockchainFile]│      │
│    └─────────────────────────┘      │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│   Blockchain Integration Layer      │
│                                     │
│         DeployDispatcher            │
│                │                    │
│     F1r3flyBlockchainClient         │
└────────────────┬────────────────────┘
                 │
                 ▼
           [ gRPC API ]
                 │
      ┌──────────▼──────────┐
      │  F1r3fly Blockchain │
      │      (Rholang)      │
      └─────────────────────┘
```

## Data Flows

### 1. File Creation / Write

```
User Action (Finder)
      │
      ▼
MacOSChangeWatcher (FSEvents)
      │
      ▼
F1r3DriveChangeListener.onFileModified()
      │
      ▼
InMemoryFileSystem.writeFile()
      │
      ▼
BlockchainFile.write()
      │
      ├── 1. Updates Local Cache (File)
      │
      └── 2. Queues Deployment (if changed)
             │
             ▼
      DeployDispatcher.enqueueDeploy()
             │
             ▼
      F1r3flyBlockchainClient.deploy()
```

### 2. Reading a File (Placeholder)

```
User Action (Open File)
      │
      ▼
FileProviderIntegration.onMaterializationRequest()
      │
      ▼
PlaceholderManager.loadContent()
      │
      ▼
CacheStrategy.get() ──────────────────────────┐
      │                                       │
      ▼ [Miss]                                ▼ [Hit]
FileChangeCallback.loadFileContent()     Return Cached Content
      │                                       ▲
      ▼                                       │
F1r3flyBlockchainClient.exploratoryDeploy()   │
      │                                       │
      ▼                                       │
CacheStrategy.put() ──────────────────────────┘
      │
      ▼
Return Content to FileProvider
```

### 3. File Deletion

```
User Action (Move to Trash)
      │
      ▼
MacOSChangeWatcher
      │
      ▼
F1r3DriveChangeListener.onFileDeleted()
      │
      ▼
InMemoryFileSystem.unlinkFile()
      │
      ├── 1. Add to pendingDeletions (Loop Prevention)
      │
      ├── 2. Remove FileProvider Placeholder
      │
      ├── 3. Remove Link from Parent Directory
      │
      └── 4. Queue Blockchain Deletion
             │
             ▼
      RholangExpressionConstructor.forgetChanel()
             │
             ▼
      DeployDispatcher.enqueueDeploy()
```

### 4. Directory Creation

```
User Action (New Folder)
      │
      ▼
MacOSChangeWatcher
      │
      ▼
F1r3DriveChangeListener.onFileCreated()
      │
      ▼
InMemoryFileSystem.makeDirectory()
      │
      ├── 1. Check Existence (Idempotency)
      │
      ├── 2. Create In-Memory BlockchainDirectory
      │
      └── 3. Create FileProvider Placeholder
             │
             ▼
      FileProviderIntegration.createPlaceholder()
```

### 5. File Rename / Move

```
User Action (Rename)
      │
      ▼
MacOSChangeWatcher
      │
      ▼
F1r3DriveChangeListener.onFileMoved()
      │
      ▼
InMemoryFileSystem.renameFile()
      │
      ├── 1. Update In-Memory Path & Parent
      │
      ├── 2. BlockchainFile.onChange()
      │
      └── 3. Update FileProvider Placeholders
             (Remove Old -> Create New)
```



## Key Interactions

*   **Idempotency**: `BlockchainFile.write` checks if content has actually changed before queuing a transaction.
*   **Loop Prevention**: `InMemoryFileSystem` uses the return value of `BlockchainFile.onChange()` to decide whether to notify `FileProvider`, breaking infinite sync loops.
*   **State Preservation**: `BlockchainDirectory` reuses existing file objects to preserve their state (e.g., `isDirty` flags) during directory refresh.

## Caching Strategy

F1r3Drive employs a multi-tiered caching system to optimize performance and minimize blockchain reads.

### 1. Tiered Cache (Managed by `CacheStrategy`)
Used for storing file content retrieved from the blockchain to serve read requests quickly.

*   **L1 Memory Cache**:
    *   **Size**: 100 MB
    *   **Implementation**: Caffeine (High-performance Java cache)
    *   **Use Case**: Frequently accessed small files.
*   **L2 Disk Cache**:
    *   **Location**: `~/.f1r3drive/cache`
    *   **Size**: 1 GB
    *   **Expiration**: 30 minutes
    *   **Use Case**: Larger files or persistence across restarts.

### 2. Local Operational Cache (`BlockchainFile` Internal)
Used for temporary storage during file operations (write/read/encryption).

*   **Location**: System Temp Directory (`java.io.tmpdir`)
*   **Format**: Unique temporary files (e.g., `myfile.txt___12345.tmp`)
*   **Lifecycle**: Created on file access, deleted on file close or application exit.
*   **Purpose**:
    *   Buffer for `RandomAccessFile` operations.
    *   Staging area for encryption/decryption before blockchain deployment.