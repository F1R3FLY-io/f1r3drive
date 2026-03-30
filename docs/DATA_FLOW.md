# F1r3Drive Data Flow & Use Cases

This document describes how F1r3Drive works internally, with detailed sequence diagrams showing the flow of data and operations. Each diagram clearly identifies which classes participate at each step.

---

## 📊 System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           User Layer                                    │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐       │
│  │    User     │         │   Finder    │         │   Terminal  │       │
│  └─────────────┘         └─────────────┘         └─────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
         │                           │                        │
         │                           ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Platform Layer (Native macOS)                        │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐ │
│  │ FileProvider        │  │ FSEventsMonitor     │  │ MacOSChange     │ │
│  │ Integration         │  │ (JNI + FSEvents)    │  │ Watcher         │ │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
         │                           │                        │
         └───────────────────────────┼────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Core Layer                                      │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐ │
│  │ InMemoryFileSystem  │  │ PlaceholderManager  │  │ F1r3Drive       │ │
│  │                     │  │                     │  │ ChangeListener  │ │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Cache Layer                                      │
│  ┌─────────────────────────────┐    ┌─────────────────────────────┐    │
│  │ L1 Memory Cache             │    │ L2 Disk Cache               │    │
│  │ Caffeine (100MB)            │───▶│ ~/.f1r3drive (1GB, 30min)   │    │
│  └─────────────────────────────┘    └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Blockchain Layer                                    │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐ │
│  │ F1r3flyBlockchain   │  │ DeployDispatcher    │  │ RholangExpression│ │
│  │ Client              │  │ (Queue Management)  │  │ Constructor     │ │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Storage Layer                                      │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │           F1r3fly Shard (Rholang Blockchain)                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Use Case 1: Application Startup

### Description
User launches F1r3Drive application to mount the blockchain filesystem.

### Sequence Diagram

```
┌────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │  F1r3Drive   │   │  F1r3Drive   │   │    MacOS     │
│        │   │     Cli      │   │     Fuse     │   │ChangeWatcher │
└───┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │               │                  │                  │
    │ java -jar     │                  │                  │
    │──────────────▶│                  │                  │
    │               │                  │                  │
    │               │ Parse CLI args   │                  │
    │               │ Init AESCipher   │                  │
    │               │                  │                  │
    │               │ initialize()     │                  │
    │               │─────────────────▶│                  │
    │               │                  │                  │
    │               │                  │ connect()        │
    │               │                  │─────────────────▶│
    │               │                  │                  │
    │               │                  │◀─────────────────│ Connected
    │               │                  │                  │
    │               │                  │ new              │
    │               │                  │ ChangeWatcher()  │
    │               │                  │─────────────────▶│
    │               │                  │                  │
    │               │                  │ startMonitoring()│
    │               │                  │─────────────────▶│
    │               │                  │                  │
    │               │                  │                  │ Init FSEvents
    │               │                  │                  │ Init FileProvider
    │               │                  │                  │
    │               │                  │◀─────────────────│ Ready
    │               │                  │                  │
    │               │                  │ Register shutdown│
    │               │                  │ hook             │
    │               │                  │                  │
    │               │◀─────────────────│                  │
    │               │                  │                  │
    │◀──────────────│                  │                  │
    │ Mount ready   │                  │                  │
    │               │                  │                  │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `F1r3DriveCli` | `io.f1r3fly.f1r3drive.app` | Parse CLI arguments, initialize components |
| `F1r3DriveFuse` | `io.f1r3fly.f1r3drive.app` | Manage lifecycle, mount/unmount |
| `AESCipher` | `io.f1r3fly.f1r3drive.encryption` | Singleton encryption service |
| `MacOSChangeWatcher` | `io.f1r3fly.f1r3drive.platform.macos` | Coordinate FSEvents + FileProvider |
| `FileProviderIntegration` | `io.f1r3fly.f1r3drive.platform.macos` | Native File Provider bridge |
| `FSEventsMonitor` | `io.f1r3fly.f1r3drive.platform.macos` | Native FSEvents monitoring |
| `InMemoryFileSystem` | `io.f1r3fly.f1r3drive.filesystem` | Core filesystem implementation |
| `F1r3flyBlockchainClient` | `io.f1r3fly.f1r3drive.blockchain.client` | gRPC communication |
| `DeployDispatcher` | `io.f1r3fly.f1r3drive.blockchain` | Queue and manage deploys |

---

## 🎯 Use Case 2: Create File (User writes file)

### Description
User creates a new file in the mounted filesystem (e.g., `echo "data" > file.txt`).

### Sequence Diagram

```
┌────────┐   ┌─────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │ Finder  │   │ FSEvents     │   │    MacOS     │   │      F1r3    │
│        │   │         │   │   Monitor    │   │ChangeWatcher │   │    Drive     │
│        │   │         │   │              │   │              │   │ ChangeListener│
└───┬────┘   └────┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │             │                │                 │                 │
    │ Create file │                │                 │                 │
    │────────────▶│                │                 │                 │
    │             │                │                 │                 │
    │             │ FSEvent:       │                 │                 │
    │             │ ItemCreated    │                 │                 │
    │             │───────────────▶│                 │                 │
    │             │                │                 │                 │
    │             │                │ onFileSystemEvent()              │
    │             │                │────────────────▶│                 │
    │             │                │                 │                 │
    │             │                │                 │ onFileCreated() │
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │                 │                 │ createFile()
    │             │                │                 │                 │───────────▶
    │             │                │                 │                 │
    │             │                │                 │                 │ new BlockchainFile
    │             │                │                 │                 │
    │             │                │                 │                 │ createPlaceholder()
    │             │                │                 │                 │
    │             │◀────────────────────────────────────────────────────│
    │             │                │                 │                 │
    │             │ File appears   │                 │                 │
    │◀────────────│                │                 │                 │
    │             │                │                 │                 │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `FileProviderIntegration` | `platform.macos` | Create placeholder via JNI |
| `FSEventsMonitor` | `platform.macos` | Detect file creation event |
| `MacOSChangeWatcher` | `platform.macos` | Bridge native events to Java |
| `F1r3DriveChangeListener` | `platform` | Filter events, convert to StateChangeEvents |
| `InMemoryFileSystem` | `filesystem` | Create file node in memory |
| `BlockchainFile` | `filesystem.deployable` | Prepare blockchain deploy |
| `DeployDispatcher` | `blockchain` | Queue and sequence deploys |
| `F1r3flyBlockchainClient` | `blockchain.client` | Send gRPC deploy request |

### Rholang Contract Generated

```rholang
// File creation contract
new return in {
  @"f1r3drive/root/{wallet}/{path}"!(
    {
      "op": "create",
      "type": "file",
      "name": "file.txt",
      "createdBy": "{revAddress}",
      "timestamp": 1234567890
    },
    *return
  )
}
```

---

## 🎯 Use Case 3: Read File (Placeholder Materialization)

### Description
User opens a placeholder file - content is loaded on-demand from blockchain.

### Sequence Diagram

```
┌────────┐   ┌─────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │ Finder  │   │  FileProvider│   │  Placeholder │   │    Cache     │
│        │   │         │   │  Integration │   │   Manager    │   │   Strategy   │
└───┬────┘   └────┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │             │                │                 │                 │
    │ Open file   │                │                 │                 │
    │────────────▶│                │                 │                 │
    │             │                │                 │                 │
    │             │ Materialization│                 │                 │
    │             │ request        │                 │                 │
    │             │───────────────▶│                 │                 │
    │             │                │                 │                 │
    │             │                │ onMaterialize() │                 │
    │             │                │────────────────▶│                 │
    │             │                │                 │                 │
    │             │                │                 │ isPlaceholder() │
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │                 │◀────────────────│ Yes
    │             │                │                 │                 │
    │             │                │                 │ get()           │
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │                 │ L1 Cache        │
    │             │                │                 │──────────┐      │
    │             │                │                 │          │ MISS  │
    │             │                │                 │◀─────────┘      │
    │             │                │                 │                 │
    │             │                │                 │ L2 Cache        │
    │             │                │                 │──────────┐      │
    │             │                │                 │          │ MISS  │
    │             │                │                 │◀─────────┘      │
    │             │                │                 │                 │
    │             │                │                 │ Blockchain Query│
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │◀──────────────────────────────────│ Content
    │             │                │                 │                 │
    │             │                │ materialize()   │                 │
    │             │◀───────────────│                 │                 │
    │             │                │                 │                 │
    │◀────────────│                │                 │                 │
    │ File opens  │                │                 │                 │
    │             │                │                 │                 │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `FileProviderIntegration` | `platform.macos` | Handle materialization callback |
| `PlaceholderManager` | `placeholder` | Coordinate lazy loading |
| `InMemoryFileSystem` | `filesystem` | Track placeholder state |
| `CacheStrategy` | `cache` | Tiered cache management |
| `F1r3flyBlockchainClient` | `blockchain.client` | Query blockchain for content |
| `AESCipher` | `encryption` | Decrypt content |

### Cache Flow

```
User Request
    ↓
L1 Cache (Caffeine, 100MB)
    ↓ [MISS]
L2 Cache (Disk, 1GB, 30min TTL)
    ↓ [MISS]
Blockchain Query (gRPC)
    ↓
Decrypt (AES)
    ↓
Store in L1 + L2
    ↓
Return to User
```

---

## 🎯 Use Case 4: Write File (Content Update)

### Description
User modifies file content - changes are synced to blockchain.

### Sequence Diagram

```
┌────────┐   ┌─────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │ Finder  │   │ FSEvents     │   │    MacOS     │   │      F1r3    │
│        │   │         │   │   Monitor    │   │ChangeWatcher │   │    Drive     │
│        │   │         │   │              │   │              │   │ ChangeListener│
└───┬────┘   └────┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │             │                │                 │                 │
    │ Write file  │                │                 │                 │
    │────────────▶│                │                 │                 │
    │             │                │                 │                 │
    │             │ FSEvent:       │                 │                 │
    │             │ ItemModified   │                 │                 │
    │             │───────────────▶│                 │                 │
    │             │                │                 │                 │
    │             │                │ onFileSystemEvent()              │
    │             │                │────────────────▶│                 │
    │             │                │                 │                 │
    │             │                │                 │ onFileModified()│
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │                 │                 │ writeFile()
    │             │                │                 │                 │───────────▶
    │             │                │                 │                 │
    │             │                │                 │                 │ invalidate cache
    │             │                │                 │                 │ encrypt content
    │             │                │                 │                 │ split to chunks
    │             │                │                 │                 │
    │             │                │                 │                 │ enqueueDeploy()
    │             │                │                 │                 │───────────┐
    │             │                │                 │                 │           │
    │             │                │                 │                 │◀──────────┘
    │             │                │                 │                 │ Deploy sent
    │             │                │                 │                 │
    │             │◀────────────────────────────────────────────────────│ updatePlaceholder()
    │             │                │                 │                 │
    │             │ File updated   │                 │                 │
    │◀────────────│                │                 │                 │
    │             │                │                 │                 │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `FSEventsMonitor` | `platform.macos` | Detect modification event |
| `F1r3DriveChangeListener` | `platform` | Route event to filesystem |
| `InMemoryFileSystem` | `filesystem` | Update file node |
| `BlockchainFile` | `filesystem.deployable` | Prepare write deploy |
| `CacheStrategy` | `cache` | Invalidate cached content |
| `AESCipher` | `encryption` | Encrypt before blockchain |
| `DeployDispatcher` | `blockchain` | Queue deploy |

### Chunking Strategy

```
File Content (e.g., 50MB)
    ↓
Split into chunks (16MB max each)
    ↓
Chunk 0: 0-16MB
Chunk 1: 16-32MB
Chunk 2: 32-48MB
Chunk 3: 48-50MB (2MB)
    ↓
Each chunk → Separate deploy
```

---

## 🎯 Use Case 5: Delete File

### Description
User deletes file - removed from filesystem and blockchain.

### Sequence Diagram

```
┌────────┐   ┌─────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │ Finder  │   │ FSEvents     │   │    MacOS     │   │      F1r3    │
│        │   │         │   │   Monitor    │   │ChangeWatcher │   │    Drive     │
│        │   │         │   │              │   │              │   │ ChangeListener│
└───┬────┘   └────┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │             │                │                 │                 │
    │ Delete file │                │                 │                 │
    │ (Cmd+Delete)│                │                 │                 │
    │────────────▶│                │                 │                 │
    │             │                │                 │                 │
    │             │ FSEvent:       │                 │                 │
    │             │ ItemRemoved    │                 │                 │
    │             │───────────────▶│                 │                 │
    │             │                │                 │                 │
    │             │                │ onFileSystemEvent()              │
    │             │                │────────────────▶│                 │
    │             │                │                 │                 │
    │             │                │                 │ onFileDeleted() │
    │             │                │                 │────────────────▶│
    │             │                │                 │                 │
    │             │                │                 │                 │ deleteFile()
    │             │                │                 │                 │───────────▶
    │             │                │                 │                 │
    │             │                │                 │                 │ generate forget contract
    │             │                │                 │                 │ enqueueDeploy()
    │             │                │                 │                 │───────────┐
    │             │                │                 │                 │           │
    │             │                │                 │                 │◀──────────┘
    │             │                │                 │                 │ Deploy sent
    │             │                │                 │                 │
    │             │                │                 │                 │ removePlaceholder()
    │             │◀────────────────────────────────────────────────────│
    │             │                │                 │                 │
    │             │ File removed   │                 │                 │
    │◀────────────│                │                 │                 │
    │             │                │                 │                 │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `FSEventsMonitor` | `platform.macos` | Detect deletion event |
| `InMemoryFileSystem` | `filesystem` | Remove node from tree |
| `BlockchainFile` | `filesystem.deployable` | Generate forget contract |
| `FileProviderIntegration` | `platform.macos` | Remove placeholder |

### Rholang Contract

```rholang
// File deletion contract
new return in {
  @"f1r3drive/root/{wallet}/{path}"!(
    {
      "op": "delete",
      "timestamp": 1234567890
    },
    *return
  )
}
```

---

## 🎯 Use Case 6: Wallet Unlock

### Description
User provides private key to unlock wallet access.

### Sequence Diagram

```
┌────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  User  │   │  F1r3Drive   │   │  InMemory    │   │   Locked     │   │  Unlocked    │
│        │   │     Cli      │   │  FileSystem  │   │   Wallet     │   │   Wallet     │
│        │   │              │   │              │   │  Directory   │   │  Directory   │
└───┬────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
    │               │                  │                  │                  │
    │ --rev-address │                  │                  │                  │
    │ --private-key │                  │                  │                  │
    │──────────────▶│                  │                  │                  │
    │               │                  │                  │                  │
    │               │ unlockWallet()   │                  │                  │
    │               │─────────────────▶│                  │                  │
    │               │                  │                  │                  │
    │               │                  │ findWallet()     │                  │
    │               │                  │─────────────────▶│                  │
    │               │                  │                  │                  │
    │               │                  │                  │ Validate key     │
    │               │                  │                  │ Derive pubkey    │
    │               │                  │                  │ Verify address   │
    │               │                  │                  │                  │
    │               │                  │                  │ new              │
    │               │                  │                  │ UnlockedWallet() │
    │               │                  │                  │─────────────────▶│
    │               │                  │                  │                  │
    │               │                  │                  │                  │ Create .tokens
    │               │                  │                  │                  │ Query balance
    │               │                  │                  │                  │
    │               │                  │                  │                  │ queryBalance()
    │               │                  │                  │                  │───────────┐
    │               │                  │                  │                  │           │
    │               │                  │                  │                  │◀──────────┘
    │               │                  │                  │                  │ 1000 REV
    │               │                  │                  │                  │
    │               │                  │                  │                  │ Create REV.token
    │               │                  │                  │                  │
    │               │                  │ replaceNode()    │                  │
    │               │                  │◀─────────────────│                  │
    │               │                  │                  │                  │
    │               │                  │ Update tree      │                  │
    │               │                  │                  │                  │
    │               │◀─────────────────│                  │                  │
    │               │                  │                  │                  │
    │◀──────────────│                  │                  │                  │
    │ Wallet unlocked│                  │                  │                  │
    │               │                  │                  │                  │
```

### Classes Involved

| Class | Package | Responsibility |
|-------|---------|----------------|
| `LockedWalletDirectory` | `filesystem.local` | Read-only wallet access |
| `UnlockedWalletDirectory` | `filesystem.local` | Full wallet access |
| `TokenDirectory` | `filesystem.local` | Display token balances |
| `BlockchainContext` | `blockchain` | Hold wallet credentials |

---

## 🔄 Background Processes

### Deploy Queue Processing

```
┌─────────────┐
│    Idle     │
└──────┬──────┘
       │
       │ Deploy enqueued
       ▼
┌─────────────┐
│  Processing │
└──────┬──────┘
       │
       │ Deploy sent
       ▼
┌─────────────┐
│   Waiting   │
└──────┬──────┘
       │
       │ Propose received
       ▼
┌─────────────┐
│ Finalizing  │
└──────┬──────┘
       │
       │ Block finalized
       ▼
┌─────────────┐     Retry      ┌─────────────┐
│  Complete   │◀───────────────│    Retry    │
└──────┬──────┘                └──────┬──────┘
       │                              ▲
       │ Next deploy                  │ Deploy failed
       └──────────────────────────────┘
```

**Classes:** `DeployDispatcher`, `F1r3flyBlockchainClient`

### Cache Eviction

```
                    ┌──────────────────┐
                    │  L1 Cache Full   │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ Eviction Policy  │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
    ┌──────────────────┐          ┌──────────────────┐
    │ LRU              │          │ LFU              │
    │ Remove Least     │          │ Remove Least     │
    │ Recently Used    │          │ Frequently Used  │
    └────────┬─────────┘          └────────┬─────────┘
             │                             │
             └──────────────┬──────────────┘
                            │
                            ▼
                   ┌───────────────────┐
                   │ Move to L2 if     │
                   │ dirty (modified)  │
                   └─────────┬─────────┘
                             │
                             ▼
                   ┌───────────────────┐
                   │ Free space in L1  │
                   └───────────────────┘
```

**Classes:** `CacheStrategy`, `TieredCacheStrategy`

---

## 📊 Performance Metrics

### Operation Latency

| Operation | Avg Latency | Classes Involved |
|-----------|-------------|------------------|
| Create File | 5-10ms | FP, FS, BF, DD, BC |
| Read (cached) | 3-5ms | FP, PM, CACHE |
| Read (uncached) | 100-200ms | FP, PM, CACHE, BC, AES |
| Write | 8-15ms | FS, BF, CACHE, DD, BC |
| Delete | 5-10ms | FS, BF, DD, BC |

### Cache Hit Rates

| Cache Level | Hit Rate | Latency |
|-------------|----------|---------|
| L1 (Memory) | 60-70% | 3-5ms |
| L2 (Disk) | 20-25% | 10-20ms |
| Blockchain | 10-15% | 100-200ms |

---

## 🔗 Related Documentation

- [Configuration](CONFIGURATION.md) - CLI options
- [Features](FEATURES.md) - Implemented features
- [README](README.md) - Project overview

---

*Last updated: 2026-03-30*
