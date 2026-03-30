# F1r3Drive - Native macOS File Provider Implementation

## Overview

F1r3Drive is a **blockchain-backed filesystem** for macOS that integrates directly with the **macOS File Provider Framework** and **FSEvents API** through native JNI bindings. Unlike traditional FUSE-based solutions, F1r3Drive provides native macOS integration where files appear directly in Finder with seamless lazy-loading from the F1r3fly blockchain.

**Key Differentiator:** Native macOS implementation using File Provider Framework (not FUSE wrapper)

---

## 🎯 Unique Features

### 1. Native File Provider Integration

F1r3Drive uses the **macOS File Provider Framework** (iOS 11+ / macOS 10.15+) to create a virtual filesystem that:
- Appears natively in Finder sidebar
- Supports on-demand file loading (placeholders)
- Integrates with macOS system features (Spotlight, Quick Look, Time Machine)
- Provides status badges and sync indicators

**Architecture:**
```
┌─────────────────────────────────────────┐
│         macOS Finder / Applications     │
└───────────────┬─────────────────────────┘
                │
    ┌───────────▼────────────┐
    │  File Provider Framework │ ← Native macOS API
    └───────────┬────────────┘
                │
    ┌───────────▼────────────┐
    │  FileProviderIntegration │ ← Java bridge (JNI)
    │  - Placeholder management│
    │  - Materialization      │
    │  - Eviction handling    │
    └───────────┬────────────┘
                │
    ┌───────────▼────────────┐
    │  InMemoryFileSystem    │ ← Core filesystem logic
    └───────────┬────────────┘
                │
    ┌───────────▼────────────┐
    │  F1r3fly Blockchain    │ ← gRPC connection
    └────────────────────────┘
```

### 2. FSEvents Monitoring

Real-time filesystem monitoring using native **FSEvents API**:

```c
// Native implementation: src/macos/native/fileprovider_integration.m
FSEventStreamRef stream = FSEventStreamCreate(
    allocator,
    callback,
    &context,
    paths,
    sinceWhen,
    latency,
    flags
);
```

**Benefits:**
- Kernel-level event detection (no polling)
- Sub-second latency (configurable, default 100ms)
- Minimal CPU overhead
- Handles rename detection, inode changes, xattr modifications

### 3. Placeholder File System

On-demand file loading with **zero-byte placeholders**:

**File States:**
```
Placeholder (0 bytes on disk)
    ↓ [User opens file]
Materialization Request (native callback)
    ↓
Blockchain Query (async)
    ↓
Content Download (gRPC)
    ↓
File Populated (actual size)
    ↓ [Memory pressure]
Eviction (back to placeholder)
```

**Extended Attributes:**
```bash
# Files marked with custom xattr
xattr -l file.txt
# user.f1r3drive.placeholder: true
```

---

## 🏗️ Architecture

### Component Hierarchy

```
F1r3Drive Application
│
├── Platform Layer (macOS)
│   ├── MacOSChangeWatcher
│   │   ├── FSEventsMonitor (JNI)
│   │   └── FileProviderIntegration (JNI)
│   └── Native Libraries
│       ├── libf1r3drive-fsevents.dylib
│       └── libf1r3drive-fileprovider.dylib
│
├── Core Filesystem
│   ├── InMemoryFileSystem
│   ├── Path Hierarchy (Root → Wallet → Files)
│   └── PlaceholderManager
│
├── Caching Layer
│   ├── L1 Memory Cache (Caffeine, 100MB)
│   └── L2 Disk Cache (~/.f1r3drive/cache, 1GB)
│
└── Blockchain Integration
    ├── F1r3flyBlockchainClient (gRPC)
    ├── DeployDispatcher
    └── RholangExpressionConstructor
```

### Native Module Architecture

**File: `src/macos/native/fileprovider_integration.m`**

```objc
// Core components
typedef struct {
    NSString *domainIdentifier;
    NSString *displayName;
    NSString *rootPath;
    NSMutableDictionary *placeholderFiles;
    dispatch_queue_t queue;
    int isActive;
} FileProviderContext;

// Key functions
- nativeCreateProvider(): Initialize File Provider extension
- nativeCreatePlaceholder(): Create zero-byte placeholder file
- nativeMaterializePlaceholder(): Populate placeholder with content
- nativeUpdatePlaceholder(): Update metadata (size, mtime)
- nativeRemovePlaceholder(): Remove file from filesystem
```

**JNI Bridge:**
```java
// File: src/main/java/io/f1r3fly/f1r3drive/platform/macos/FileProviderIntegration.java
private native long nativeCreateProvider(
    String domainIdentifier,
    String displayName,
    String rootPath
);

private native boolean nativeCreatePlaceholder(
    long contextRef,
    String path,
    long size,
    long lastModified,
    int itemType,
    int materializationPolicy
);
```

### Build System

**Platform-specific JARs:**
```bash
# macOS (includes native libraries)
./gradlew shadowJarMacOS
# Output: build/libs/f1r3drive-macos-0.1.1.jar

# Linux (FUSE-based)
./gradlew shadowJarLinux
# Output: build/libs/f1r3drive-linux-0.1.1.jar

# Windows (future, WinFsp)
./gradlew shadowJarWindows
```

**Gradle Configuration:**
```groovy
sourceSets {
    macos {
        java { srcDir 'src/macos/java' }
        resources { srcDir 'src/macos/resources' }
    }
    linux {
        java { srcDir 'src/linux/java' }
        resources { srcDir 'src/linux/resources' }
    }
}
```

---

## 🚀 Getting Started

### Prerequisites

**macOS 10.15+ (Catalina or later)**
- Required for File Provider Framework
- JDK 17+ (Temurin recommended)
- Xcode Command Line Tools (for native compilation)

### Installation

1. **Install dependencies:**
```bash
# Install MacFUSE (optional, for fallback)
brew install --cask macfuse

# Install JDK 17
brew install --cask temurin
```

2. **Build from source:**
```bash
git clone https://github.com/f1r3fly-io/f1r3drive.git
cd f1r3drive

# Enable development environment
direnv allow

# Build macOS JAR
./gradlew shadowJarMacOS -x test
```

3. **Run local shard:**
```bash
cd local-shard
docker-compose -f shard-with-autopropose.yml up -d
```

4. **Launch F1r3Drive:**
```bash
java -jar ./build/libs/f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost \
   --validator-port 40402 \
   --observer-host localhost \
   --observer-port 40442 \
   --manual-propose=true \
   --rev-address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
   --private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9
```

---

## 📖 Usage

### File Operations

**Create file:**
```bash
echo "Hello, Blockchain!" > ~/demo-f1r3drive/wallet_111127RX.../hello.txt
```

**Read file (triggers materialization):**
```bash
cat ~/demo-f1r3drive/wallet_111127RX.../hello.txt
# Output: Hello, Blockchain!
```

**List directory:**
```bash
ls -lh ~/demo-f1r3drive/wallet_111127RX.../
# -rw-r--r--  1 user  staff    0B Mar 30 10:00 placeholder.txt
# -rw-r--r--  1 user  staff   20B Mar 30 10:05 hello.txt
```

### Placeholder Management

**Check placeholder status:**
```bash
# Check if file is a placeholder
xattr -l ~/demo-f1r3drive/wallet_111127RX.../placeholder.txt
# user.f1r3drive.placeholder: true
```

**Force materialization:**
```bash
# Opening file triggers materialization
open ~/demo-f1r3drive/wallet_111127RX.../placeholder.txt
```

---

## 🔧 Configuration

### Required Options

```bash
--cipher-key-path ~/cipher.key     # AES encryption key
--validator-host localhost          # Validator node
--validator-port 40402              # Validator port
```

### macOS-Specific Options

```bash
--platform macos                    # Force macOS platform
--file-provider-enabled true        # Enable File Provider (default: true)
--deep-integration true             # Full macOS integration (default: true)
```

### Optional Options

```bash
--rev-address 111127RX...          # Wallet address
--private-key 357cdc42...          # Private key (hex)
--manual-propose=true              # Dev mode (wait for finalization)
--verbose                          # Debug logging
```

---

## 🎯 Advantages Over FUSE

| Feature | F1r3Drive (Native) | Traditional FUSE |
|---------|-------------------|------------------|
| **Finder Integration** | ✅ Native sidebar | ❌ Requires 3rd-party tools |
| **Placeholder Support** | ✅ Built-in (File Provider) | ❌ Manual implementation |
| **Performance** | ✅ Kernel-level (FSEvents) | ⚠️ Userspace polling |
| **System Features** | ✅ Spotlight, Quick Look, Time Machine | ❌ Limited support |
| **Stability** | ✅ Apple-supported API | ⚠️ 3rd-party kernel extension |
| **Security** | ✅ App Sandbox compatible | ❌ Requires system extensions |
| **Future-proof** | ✅ Apple-maintained | ⚠️ Community-maintained |

---

## 📊 Technical Specifications

### Native Libraries

| Library | Purpose | Location |
|---------|---------|----------|
| `libf1r3drive-fsevents.dylib` | FSEvents API bridge | `src/macos/native/` |
| `libf1r3drive-fileprovider.dylib` | File Provider bridge | `src/macos/native/` |

### JNI Methods

**FSEventsMonitor:**
```java
native long nativeCreateStream(String path, double latency, int flags)
native boolean nativeScheduleStream(long streamRef)
native boolean nativeStartStream(long streamRef)
native void nativeStopStream(long streamRef)
native void nativeRunLoop(long streamRef)
```

**FileProviderIntegration:**
```java
native long nativeCreateProvider(String domainId, String displayName, String rootPath)
native boolean nativeCreatePlaceholder(long contextRef, String path, long size, long lastModified, int itemType, int policy)
native boolean nativeMaterializePlaceholder(long contextRef, String path, byte[] content)
native boolean nativeUpdatePlaceholder(long contextRef, String path, long size, long lastModified)
native boolean nativeRemovePlaceholder(long contextRef, String path)
```

### File Provider Constants

```java
// Item types
NSFileProviderItemTypeData = 0
NSFileProviderItemTypeFolder = 1
NSFileProviderItemTypeSymbolicLink = 2

// Materialization policies
NSFileProviderMaterializationPolicyOnDemand = 0
NSFileProviderMaterializationPolicyAlways = 1
```

---

## 🐛 Troubleshooting

### File Provider Not Appearing in Finder

**Solution:**
```bash
# Reset File Provider database
fileproviderctl reset

# Restart Finder
killall Finder

# Reboot system (if necessary)
sudo reboot
```

### Placeholders Not Materializing

**Check logs:**
```bash
# View F1r3Drive logs
tail -f ~/Library/Logs/f1r3drive.log

# Check system logs
log show --predicate 'process == "Finder"' --last 5m
```

### Native Library Loading Errors

**Verify libraries:**
```bash
# Check library exists
ls -la build/libs/*.dylib

# Check library signatures
codesign -v build/libs/*.dylib
```

---

## 📝 License

MIT License - See [LICENSE](../LICENSE) for details.

---

## 🔗 Related Documentation

- [Configuration Reference](CONFIGURATION.md) - Full CLI options
- [Features](FEATURES.md) - Implemented and planned features
- [Data Flow](DATA_FLOW.md) - How data moves through the system
- [Storage Structure](RHOLANG_STORAGE.md) - Blockchain storage format

---

*Last updated: 2026-03-30*  
