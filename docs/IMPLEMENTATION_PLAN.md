# F1r3Drive Platform Implementation Plan

## Overview

This document describes the exact implementation plan for F1r3Drive's platform-specific architecture with detailed breakdown of all classes and methods, clearly showing what is added, changed, or remains unchanged.

## Legend

- 🆕 **[NEW]** - new classes/methods that need to be created
- 🔄 **[UPDATE]** - existing classes/methods that need to be changed  
- ⚫ **[EXISTING]** - existing classes/methods without changes
- 🍎 **[macOS ONLY]** - only in macOS JAR
- 🐧 **[LINUX ONLY]** - only in Linux JAR
- 🪟 **[WINDOWS ONLY]** - only in Windows JAR

---

## Build Strategy

### Gradle Configuration Changes 🔄 **[UPDATE]**

![Build System](diagrams/01-build-system.puml)

**Changes in build.gradle:**
- Add platform-specific source sets: `macos`, `linux`, `windows`
- Create build tasks: `buildMacOS`, `buildLinux`, `buildWindows`  
- Add property `platformVariant` for platform selection
- Configure platform-specific dependencies

**Build commands:**
```bash
# macOS JAR
./gradlew shadowJar -PplatformVariant=macos

# Linux JAR  
./gradlew shadowJar -PplatformVariant=linux

# Windows JAR (future implementation)
./gradlew shadowJar -PplatformVariant=windows
```

**Output JARs:**
- `f1r3drive-macos.jar` (~15MB) 🍎
- `f1r3drive-linux.jar` (~18MB) 🐧
- `f1r3drive-windows.jar` (~20MB) 🪟

---

## 1. Platform Abstraction Layer (Layer 0)

### Core Interfaces 🆕 **[NEW]**

![Platform Abstraction](diagrams/02-platform-abstraction.puml)

**Key components:**

#### `ChangeWatcher` (interface)
**Package**: `io.f1r3fly.f1r3drive.platform`
- Main interface for platform-specific file monitoring
- Abstracts differences between macOS FSEvents, Linux inotify, and Windows ReadDirectoryChangesW
- Provides unified API across all platforms

#### `ChangeListener` (interface)  
**Package**: `io.f1r3fly.f1r3drive.platform`
- File system event handler
- Receives notifications about file creation, modification, deletion
- Routes events to `StateChangeEventsManager`

#### `FileChangeCallback` (interface)
**Package**: `io.f1r3fly.f1r3drive.platform`
- Callback for on-demand file loading
- Supports lazy loading from blockchain
- Manages content caching

#### `PlatformDetector` & `ChangeWatcherFactory`
- Automatic platform detection
- Factory pattern for creating platform-specific implementations

---

## 2. macOS Implementation 🍎

### macOS Classes 🆕 **[NEW - macOS ONLY]**

![macOS Implementation](diagrams/03-macos-implementation.puml)

**Key components:**

#### `MacOSChangeWatcher`
**Package**: `io.f1r3fly.f1r3drive.platform.macos`
- Main integration point for macOS
- Manages `FSEventsMonitor` and `FileProviderIntegration`
- Provides bidirectional synchronization with `InMemoryFileSystem`

#### `FSEventsMonitor`
**Package**: `io.f1r3fly.f1r3drive.platform.macos`
- JNI integration with macOS FSEvents API
- Low-level filesystem monitoring
- Runs in separate thread with CFRunLoop
- **Native dependency**: `libf1r3drive-fsevents.dylib`

#### `FileProviderIntegration`
**Package**: `io.f1r3fly.f1r3drive.platform.macos`
- Integration with macOS File Provider Framework
- Creates virtual filesystem in Finder
- Supports placeholder files for lazy loading
- Provides seamless integration with macOS applications

#### **JNI Native Library Requirements:**
```c
// FSEvents API bindings
FSEventStreamRef FSEventStreamCreate(...);
void FSEventStreamStart(FSEventStreamRef streamRef);
void FSEventStreamStop(FSEventStreamRef streamRef);

// File Provider Framework bindings  
NSFileProviderExtension* createProvider(...);
void updateProviderItem(...);
```

---

## 3. Linux Implementation 🐧

### Linux Classes 🆕 **[NEW - LINUX ONLY]**

![Linux Implementation](diagrams/04-linux-implementation.puml)

**Key components:**

#### `LinuxChangeWatcher`
**Package**: `io.f1r3fly.f1r3drive.platform.linux`
- Main integration point for Linux
- Manages `FuseFilesystem` and `InotifyMonitor`
- Integration with existing jnr-fuse code

#### `FuseFilesystem`
**Package**: `io.f1r3fly.f1r3drive.platform.linux`
- Extension of `FuseStubFS` (jnr-fuse)
- Implements FUSE operations: `getattr`, `read`, `write`, `readdir`
- Bridge between FUSE kernel module and `InMemoryFileSystem`
- Supports placeholder files

#### `InotifyMonitor`
**Package**: `io.f1r3fly.f1r3drive.platform.linux`
- JNR-FFI integration with Linux inotify API
- Kernel-level file event monitoring
- Recursive directory monitoring
- Event handling: `IN_CREATE`, `IN_MODIFY`, `IN_DELETE`, `IN_ACCESS`

#### **JNR-FFI Integration:**
```java
// Linux system calls through JNR-FFI
LibC.inotify_init1(IN_CLOEXEC);
LibC.inotify_add_watch(fd, path, IN_CREATE | IN_MODIFY | IN_DELETE);
LibC.read(fd, buffer, BUFFER_SIZE);
```

**Dependencies:**
- `jnr-fuse:0.5.7` (existing)
- `jnr-ffi:2.2.15` (existing)
- `jnr-posix:3.1.18` (existing)

---

## 4. Shared Components (in both JARs)

### PlaceholderManager 🆕 **[NEW]**

![Placeholder Manager](diagrams/05-placeholder-manager.puml)

**Key components:**

#### `PlaceholderManager`
**Package**: `io.f1r3fly.f1r3drive.placeholder`
- Central component for managing lazy loading of files
- Content caching with various eviction policies
- Loading priority support
- Cache usage statistics

#### `PlaceholderInfo`
**Package**: `io.f1r3fly.f1r3drive.placeholder`
- Placeholder file metadata
- Loading state tracking
- Blockchain addresses for files
- Access timestamps

**Workflow:**
1. `createPlaceholder()` - create placeholder in filesystem
2. File access triggers `loadContent()`
3. `onDemandLoad()` calls blockchain client
4. Content is cached and returned to user
5. Update statistics and metadata

---

## 5. Updated Existing Classes

### F1r3DriveFuse 🔄 **[UPDATE]**

![Updated Classes](diagrams/08-updated-classes.puml)

**Changes in F1r3DriveFuse:**

#### New fields 🆕:
- `ChangeWatcher changeWatcher` - platform-specific watcher
- `PlaceholderManager placeholderManager` - lazy loading manager  
- `F1r3DriveChangeListener changeListener` - event router
- `Thread shutdownHook` - cleanup hook

#### Updated methods 🔄:

**`initialize(String mountPath, BlockchainContext context)`**
- ✅ Existing: AES setup, filesystem init, blockchain client
- 🆕 NEW: ChangeWatcher creation via factory
- 🆕 NEW: PlaceholderManager setup
- 🆕 NEW: Platform integration configuration

**`start()`**
- ✅ Existing: Blockchain connect, state manager start
- 🆕 NEW: `changeWatcher.startMonitoring()`
- 🆕 NEW: Preload blockchain files as placeholders
- 🆕 NEW: Setup shutdown hooks

**`shutdown()`**
- ✅ Existing: State shutdown, blockchain disconnect  
- 🆕 NEW: `changeWatcher.cleanup()`
- 🆕 NEW: `placeholderManager.cleanup()`

#### New methods 🆕:
- `createPlatformChangeWatcher()` - factory method
- `setupPlatformIntegration()` - integration setup
- `preloadBlockchainFiles()` - placeholder creation
- `registerBlockchainFile()` - file registration

### StateChangeEventsManager 🔄 **[UPDATE]**

**Changes in StateChangeEventsManager:**

#### New fields 🆕:
- `ChangeListener changeListener` - external event listener
- `BlockingQueue<ExternalChangeEvent> externalEventQueue` - external events
- `Thread externalEventProcessor` - processor thread

#### New methods 🆕:
- `setChangeListener(ChangeListener listener)` - set external listener
- `notifyFileChange(String path, ChangeType type)` - notify external changes
- `notifyExternalChange()` - queue external events

#### Updated methods 🔄:
- `processEvents()` - added external events processing
- `shutdown()` - added external processor cleanup

---

## 6. Existing Classes (без изменений) ⚫

### Core Components **[EXISTING]**

![Existing Classes](diagrams/07-existing-classes.puml)

**All the following components remain UNCHANGED:**

#### Entry Point ⚫
- `F1r3DriveCli` - only calls updated F1r3DriveFuse

#### Filesystem Core ⚫  
- `FileSystem` interface
- `InMemoryFileSystem`
- `Node`, `Directory`, `File` hierarchy
- `RootDirectory`, `WalletDirectory`

#### Blockchain Layer ⚫
- `F1r3flyBlockchainClient` - entire gRPC integration
- `BlockchainContext`, `WalletInfo`
- `GrpcClient`, `DeployDispatcher`
- `RholangExpressionConstructor`

#### State Management ⚫
- `EventQueue`, `BlockingEventQueue`
- `EventProcessor`, `EventProcessorRegistry`  
- `StateChangeEvents`, `StateChangeEvent`

#### Utility Classes ⚫
- `AESCipher` - encryption
- `PathResolver`, `FilePermissions`
- `F1r3DriveError` и все exception классы

**Total: ~95% of existing code remains unchanged**

---

## 7. Integration Flow

### Data Flow Sequence

![Integration Flow](diagrams/06-integration-flow.puml)

**Key integration points:**

1. **Platform Event Detection**
   - macOS: FSEvents + File Provider
   - Linux: inotify + FUSE operations
   - Windows: ReadDirectoryChangesW + WinFsp (future)

2. **Event Routing**
   - Platform events → `ChangeWatcher`
   - `ChangeWatcher` → `ChangeListener`  
   - `ChangeListener` → `StateChangeEventsManager`

3. **Filesystem Sync**
   - Bidirectional synchronization
   - Platform FS ↔ InMemoryFileSystem
   - Placeholder file handling

4. **Lazy Loading**
   - File access → placeholder detection
   - On-demand blockchain query
   - Content caching and update

---

## 8. Implementation Phases

### Phase 1: Core Interfaces 🆕 (Week 1-2)
- [ ] `ChangeWatcher` interface
- [ ] `ChangeListener` interface  
- [ ] `FileChangeCallback` interface
- [ ] `PlatformDetector` class
- [ ] `ChangeWatcherFactory` class
- [ ] `PlatformInfo` abstract class
- [ ] Unit tests for interfaces

### Phase 2: PlaceholderManager 🆕 (Week 3-4)
- [ ] `PlaceholderManager` class
- [ ] `PlaceholderInfo` class
- [ ] `PlaceholderState` enum
- [ ] `CacheConfiguration` class
- [ ] Cache management implementation
- [ ] Integration tests

### Phase 3: macOS Implementation 🍎 (Week 5-7)
- [ ] `MacOSChangeWatcher` class
- [ ] `FSEventsMonitor` with JNI
- [ ] Native library: `libf1r3drive-fsevents.dylib`
- [ ] `FileProviderIntegration` class
- [ ] `MacOSPlatformInfo` class
- [ ] macOS-specific tests
- [ ] Integration with Xcode build system

### Phase 4: Linux Implementation 🐧 (Week 8-10)
- [ ] `LinuxChangeWatcher` class  
- [ ] `FuseFilesystem` extension
- [ ] `InotifyMonitor` with JNR-FFI
- [ ] `LinuxPlatformInfo` class
- [ ] Linux-specific tests
- [ ] Docker test environment

### Phase 5: Integration Updates 🔄 (Week 11-12)
- [ ] Update `F1r3DriveFuse.initialize()`
- [ ] Update `F1r3DriveFuse.start()`  
- [ ] Update `F1r3DriveFuse.shutdown()`
- [ ] Add `StateChangeEventsManager.setChangeListener()`
- [ ] Add `StateChangeEventsManager.notifyFileChange()`
- [ ] Update `StateChangeEventsManager.processEvent()`
- [ ] Integration testing

### Phase 6: Build System 🔄 (Week 13)
- [ ] Platform-specific source sets in `build.gradle`
- [ ] `buildMacOS` task configuration
- [ ] `buildLinux` task configuration
- [ ] Platform variant property handling
- [ ] Dependency management per platform
- [ ] CI/CD pipeline updates

### Phase 7: Testing & Documentation (Week 14-16)
- [ ] Unit tests for all new classes
- [ ] Integration tests for both platforms
- [ ] End-to-end tests
- [ ] Performance benchmarks
- [ ] Memory usage optimization
- [ ] Documentation updates
- [ ] Migration guide
- [ ] API documentation

---

## 9. Technical Requirements

### macOS Requirements
- **OS Version**: macOS 10.15+ (File Provider Framework)
- **Xcode**: 12.0+ for native development
- **JDK**: 17+ with JNI support
- **Frameworks**: Foundation, FileProvider, CoreServices

### Linux Requirements  
- **Kernel**: Linux 2.6.13+ (inotify support)
- **FUSE**: 2.6+ kernel module
- **JDK**: 17+ with JNR-FFI support
- **Dependencies**: fuse-dev, libc6-dev

### Windows Requirements (Future)
- **OS Version**: Windows 10 1803+ 
- **WinFsp**: Windows File System Proxy
- **JDK**: 17+ with JNA support
- **SDK**: Windows 10 SDK

---

## 10. Performance Considerations

### Memory Usage
- **macOS JAR**: ~15MB (without jnr-fuse dependencies)
- **Linux JAR**: ~18MB (with jnr-fuse)
- **Runtime Memory**: +20-30MB for platform monitoring
- **Cache Management**: Configurable limits

### CPU Usage
- **FSEvents**: Minimal overhead, kernel-level
- **inotify**: Low overhead, event-driven
- **Thread Usage**: 2-3 additional threads per platform

### Network Usage
- **Blockchain Queries**: On-demand only on file access
- **Caching**: Reduces repeated blockchain calls
- **Batch Operations**: Group multiple file operations

---

## 11. Security Considerations

### File Access Control
- Use existing `FilePermissions`
- Platform-specific permission mapping
- Secure temp file handling

### Blockchain Security
- Existing AES encryption unchanged
- Secure wallet key handling
- Signed blockchain transactions

### Platform Security
- JNI library signature verification (macOS)
- SELinux compatibility (Linux)  
- Windows UAC integration (future)

---

## 12. Testing Strategy

### Unit Tests
- Mock platform APIs для cross-platform testing
- Interface contract validation
- Error condition handling

### Integration Tests
- Platform-specific test suites
- File system operations validation
- Blockchain integration testing

### Performance Tests
- Memory usage monitoring
- File access latency measurement
- Concurrent operation testing

### Manual Testing
- Real filesystem operations
- Multiple platform validation
- User experience testing

---

## 13. Error Handling & Recovery

### Platform Errors
- Graceful fallback при platform API failures
- Automatic retry mechanisms
- User-friendly error messages

### Blockchain Errors
- Connection failure handling
- Transaction timeout recovery
- Cached content fallback

### Resource Management
- Proper cleanup of native resources
- Thread pool management
- Memory leak prevention

---

## 14. Summary

### What's Added 🆕
- **29 new classes/interfaces** для platform abstraction
- **macOS-specific implementation** с native integration
- **Linux-specific implementation** с FUSE integration  
- **PlaceholderManager system** для lazy loading
- **Platform detection и factory** для automatic configuration

### What's Updated 🔄
- **F1r3DriveFuse**: 4 новых поля + 7 новых методов
- **StateChangeEventsManager**: 2 новых поля + 6 новых методов
- **build.gradle**: platform-specific configuration

### What Stays Same ⚫
- **~95% существующего кода** без изменений
- Все **filesystem**, **blockchain**, **encryption** слои
- Все **interfaces** и **APIs** остаются совместимыми
- Вся **бизнес-логика** приложения

### Benefits
- **Platform-native performance** с использованием OS APIs
- **Reduced JAR sizes** через platform-specific builds  
- **Better user experience** с proper OS integration
- **Extensible architecture** для future Windows support
- **Backward compatibility** с existing deployments

Этот план обеспечивает плавный переход к платформо-специфичной архитектуре с минимальными изменениями в существующем коде и максимальной производительностью на каждой платформе.