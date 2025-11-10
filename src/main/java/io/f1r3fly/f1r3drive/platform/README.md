# F1r3Drive Platform Abstraction Layer

## Overview

This package contains the platform abstraction layer for F1r3Drive, implementing platform-specific file system monitoring and integration capabilities for macOS, Linux, and Windows.

## Architecture

The platform abstraction layer provides a unified API across all supported platforms while leveraging native OS capabilities for optimal performance and user experience.

### Core Interfaces

- **`ChangeWatcher`** - Main interface for platform-specific file monitoring
- **`ChangeListener`** - File system event handler interface
- **`FileChangeCallback`** - Callback interface for on-demand file loading from blockchain
- **`PlatformInfo`** - Abstract base class for platform-specific information

### Factory Classes

- **`ChangeWatcherFactory`** - Creates platform-specific ChangeWatcher instances
- **`PlatformDetector`** - Automatic platform detection and system validation

## Platform Implementations

### macOS (`platform/macos/`)

**Status: ✅ Implemented (Phase 2-3)**

- **`MacOSChangeWatcher`** - Main integration point for macOS
- **`FSEventsMonitor`** - JNI integration with macOS FSEvents API
- **`FileProviderIntegration`** - Integration with macOS File Provider Framework
- **`MacOSPlatformInfo`** - macOS-specific platform information

**Features:**
- Native FSEvents monitoring for efficient file system watching
- File Provider Framework integration for seamless Finder integration
- Placeholder files for lazy loading from blockchain
- Deep macOS system integration

**Requirements:**
- macOS 10.15+ (for File Provider Framework)
- JDK 17+
- Native library: `libf1r3drive-fsevents.dylib`
- Optional: `libf1r3drive-fileprovider.dylib` for File Provider support

### Linux (`platform/linux/`)

**Status: 🚧 Planned for Phase 4**

Will include:
- **`LinuxChangeWatcher`** - Main integration point for Linux
- **`FuseFilesystem`** - FUSE filesystem implementation
- **`InotifyMonitor`** - Linux inotify integration
- **`LinuxPlatformInfo`** - Linux-specific platform information

### Windows (`platform/windows/`)

**Status: 📋 Future Implementation**

Planned features:
- **`WindowsChangeWatcher`** - Main integration point for Windows
- **`WinFspFilesystem`** - Windows File System Proxy integration
- **`ReadDirectoryChangesWMonitor`** - Windows file monitoring
- **`WindowsPlatformInfo`** - Windows-specific platform information

## Usage

### Basic Usage

```java
import io.f1r3fly.f1r3drive.platform.*;

// Create platform-specific change watcher
ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher();

// Set up change listener
ChangeListener listener = new MyChangeListener();

// Start monitoring
watcher.startMonitoring("/path/to/watch", listener);

// Clean up when done
watcher.stopMonitoring();
watcher.cleanup();
```

### With Configuration

```java
import io.f1r3fly.f1r3drive.platform.ChangeWatcherFactory.*;

// Create configuration
ChangeWatcherConfig config = new ChangeWatcherConfig()
    .setFileProviderEnabled(true)
    .setDeepIntegrationEnabled(true)
    .setFSEventsLatency(0.1);

// Create configured watcher
ChangeWatcher watcher = ChangeWatcherFactory.createChangeWatcher(config);
```

### Platform Detection

```java
import io.f1r3fly.f1r3drive.platform.PlatformDetector;

// Check if current platform is supported
if (PlatformDetector.isCurrentPlatformSupported()) {
    System.out.println("Platform is supported!");
}

// Get detailed system information
PlatformDetector.SystemInfo info = PlatformDetector.getSystemInfo();
System.out.println(info);
```

## Integration with F1r3Drive Core

The platform abstraction layer integrates with the core F1r3Drive components:

1. **StateChangeEventsManager** - Receives platform events through ChangeListener
2. **InMemoryFileSystem** - Bidirectional synchronization with platform filesystem
3. **PlaceholderManager** - Manages lazy loading of files from blockchain
4. **F1r3DriveFuse** - Main application class coordinates all components

## Event Flow

```
Platform FS Event → ChangeWatcher → ChangeListener → StateChangeEventsManager
                                                            ↓
InMemoryFileSystem ← Bidirectional Sync ←            EventProcessor
                                                            ↓
Blockchain Client ← FileChangeCallback ←              PlaceholderManager
```

## Error Handling

All platform implementations provide robust error handling:

- **Graceful fallbacks** when native APIs fail
- **Automatic retry mechanisms** for transient errors
- **User-friendly error messages** with troubleshooting information
- **Resource cleanup** even when errors occur

## Thread Safety

All components in the platform abstraction layer are designed to be thread-safe:

- Use of `AtomicBoolean`, `ConcurrentHashMap`, and proper synchronization
- Read-write locks for performance optimization
- Background threads for monitoring operations
- Safe cleanup and shutdown procedures

## Native Dependencies

### macOS
- `libf1r3drive-fsevents.dylib` - FSEvents integration (required)
- `libf1r3drive-fileprovider.dylib` - File Provider integration (optional)

### Linux (Future)
- Uses JNR-FFI for system calls (no separate native library required)
- Requires FUSE kernel module and development headers

### Windows (Future)
- `f1r3drive-windows.dll` - Windows integration library
- Requires WinFsp installation

## Testing

Each platform implementation includes comprehensive tests:

- Unit tests for all public methods
- Integration tests with mock native APIs
- Performance benchmarks
- Memory usage validation
- Error condition testing

## Configuration

Platform-specific configuration is handled through:

1. **PlatformInfo.getConfigurationProperties()** - Default platform settings
2. **ChangeWatcherConfig** - Runtime configuration options
3. **System properties** - Override default behavior
4. **Environment variables** - Deployment-specific settings

## Troubleshooting

### macOS Issues
- Check macOS version (requires 10.15+)
- Verify native library is in library path
- Check Full Disk Access permissions if needed
- Ensure File Provider Framework is available

### Linux Issues (Future)
- Verify FUSE is installed and accessible
- Check kernel version for inotify support
- Ensure user has permissions for FUSE mounting
- Validate JNR-FFI dependencies

### General Issues
- Verify JDK 17+ is being used
- Check system architecture compatibility
- Review logs for detailed error messages
- Use PlatformDetector to validate system requirements

## Performance Considerations

- **macOS**: FSEvents is very efficient, minimal CPU/memory overhead
- **Linux**: inotify provides kernel-level monitoring with low overhead
- **Memory Usage**: Configurable caching with automatic cleanup
- **Thread Management**: Minimal thread usage, optimized for each platform

## Future Enhancements

1. **Windows Support** - Complete Windows implementation
2. **Additional Platforms** - FreeBSD, Solaris support
3. **Enhanced Caching** - More sophisticated cache management
4. **Performance Monitoring** - Built-in metrics and monitoring
5. **Configuration UI** - Graphical configuration interface

## Contributing

When adding new platform support:

1. Extend the appropriate base interfaces
2. Implement platform-specific PlatformInfo
3. Add factory support in ChangeWatcherFactory
4. Include comprehensive tests
5. Update documentation and examples