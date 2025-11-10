# F1r3Drive Layer 6: File Availability Management - Detailed Functionality

## Overview

Layer 6 (File Availability Management) is the core orchestration layer of F1r3Drive that bridges the gap between the in-memory filesystem representation and the blockchain storage. This layer manages the complex lifecycle of files, from their initial creation as placeholders to their full materialization with content from the blockchain network.

## Architecture Position

```
Layer 6: File Availability Management
├── Manages: PlaceholderManager, FileAvailabilityTracker, OnDemandLoader
├── Coordinates with: Layer 5 (Filesystem Core), Layer 9 (Blockchain)
└── Platform Integration: Works with Layer 0 (Platform Abstraction)
```

## Core Components

### 1. PlaceholderManager

The `PlaceholderManager` is the central component responsible for managing the lifecycle of placeholder files - lightweight representations of blockchain-stored files.

#### Key Responsibilities:
- **Placeholder Creation**: Creates lightweight file representations with metadata
- **State Management**: Tracks file states (NOT_LOADED, LOADING, LOADED, ERROR, CACHED)
- **Content Caching**: Implements intelligent caching with configurable policies
- **Priority Management**: Handles loading priorities (LOW, NORMAL, HIGH, CRITICAL)
- **Statistics Tracking**: Monitors cache hit rates, loading times, and performance metrics

#### Core Methods:
```java
public class PlaceholderManager {
    // Placeholder lifecycle management
    public void createPlaceholder(String path, PlaceholderInfo info);
    public void updatePlaceholder(String path, byte[] content);
    public void removePlaceholder(String path);
    
    // State queries
    public boolean isPlaceholder(String path);
    public PlaceholderInfo getPlaceholderInfo(String path);
    
    // Content loading
    public CompletableFuture<byte[]> loadContent(String path);
    public void preloadContent(String path);
    
    // Cache management
    public void cacheContent(String path, byte[] content);
    public void clearCache(String path);
    public void evictExpiredEntries();
    
    // Statistics and monitoring
    public CacheStatistics getCacheStatistics();
    public void updateCacheStats();
}
```

#### Placeholder States:
- **NOT_LOADED**: Initial state, only metadata available
- **LOADING**: Content is being fetched from blockchain
- **LOADED**: Content successfully loaded and available
- **ERROR**: Loading failed, error state with retry logic
- **CACHED**: Content is cached in memory for fast access
- **EXPIRED**: Cache entry expired, needs refresh
- **EVICTED**: Removed from cache due to memory pressure

### 2. FileAvailabilityTracker

Tracks the availability status of files across the entire filesystem, providing a global view of which files are immediately available versus those requiring blockchain queries.

#### Key Responsibilities:
- **Availability Status**: Tracks which files are locally available vs. need fetching
- **Dependency Tracking**: Manages file dependencies and loading order
- **Resource Monitoring**: Monitors system resources (memory, disk space)
- **Load Balancing**: Distributes loading requests to optimize performance

#### Core Methods:
```java
public class FileAvailabilityTracker {
    // Availability queries
    public FileAvailability getFileAvailability(String path);
    public Set<String> getUnavailableFiles(String directory);
    public boolean isDirectoryFullyLoaded(String directory);
    
    // Batch operations
    public void preloadDirectory(String directory, LoadingPriority priority);
    public void markFilesUnavailable(Set<String> paths);
    
    // Resource management
    public ResourceStatus getResourceStatus();
    public void optimizeMemoryUsage();
}
```

#### Availability States:
- **IMMEDIATELY_AVAILABLE**: File content is in memory or local cache
- **BLOCKCHAIN_REQUIRED**: File exists but content must be fetched from blockchain
- **NOT_EXISTS**: File does not exist in blockchain
- **LOADING**: Currently being fetched from blockchain
- **FAILED**: Loading failed, may require manual intervention

### 3. OnDemandLoader

Handles the actual loading of file content from the blockchain when requested, implementing sophisticated retry logic, timeout management, and error handling.

#### Key Responsibilities:
- **Blockchain Communication**: Interfaces with F1r3flyBlockchainClient
- **Retry Logic**: Implements exponential backoff for failed loads
- **Timeout Management**: Handles network timeouts and connection issues
- **Concurrent Loading**: Manages multiple simultaneous loading operations
- **Error Handling**: Provides detailed error reporting and recovery options

#### Core Methods:
```java
public class OnDemandLoader {
    // Primary loading interface
    public CompletableFuture<byte[]> loadFileContent(String blockchainAddress);
    public CompletableFuture<FileMetadata> loadFileMetadata(String path);
    
    // Batch loading
    public CompletableFuture<Map<String, byte[]>> loadMultipleFiles(Set<String> paths);
    
    // Loading control
    public void cancelLoad(String path);
    public void prioritizeLoad(String path, LoadingPriority priority);
    
    // Configuration
    public void setLoadingTimeout(Duration timeout);
    public void setRetryPolicy(RetryPolicy policy);
}
```

## Data Flow Patterns

### 1. File Access Flow

```
User Request → InMemoryFileSystem → PlaceholderManager → OnDemandLoader → Blockchain
     ↓                ↓                    ↓               ↓              ↓
File Access    Check if File       Check State      Load Content    Query Network
   Event        is Placeholder        (LOADED?)       (if needed)     (gRPC call)
     ↓                ↓                    ↓               ↓              ↓
Platform        Return Cached       Update State     Cache Result   Return Content
Integration      Content or          to LOADING       & Notify       to User App
                Trigger Load
```

### 2. Placeholder Creation Flow

```
Blockchain Sync → New File Detected → Create PlaceholderInfo → Add to InMemoryFileSystem
       ↓                ↓                     ↓                        ↓
  File Metadata    Extract File Info    Set State=NOT_LOADED    Create Node Structure
   Retrieved        (size, hash, etc)        ↓                        ↓
       ↓                ↓            Register with Tracker      Notify Platform Layer
Update File List   Store in Manager         ↓                        ↓
                        ↓              Update Availability       File Appears in OS
                 Prepare for Access         Status              (Finder/Explorer)
```

### 3. Cache Management Flow

```
Cache Hit → Return Immediately
    ↓
Cache Miss → Check Placeholder State
    ↓
NOT_LOADED → Trigger OnDemandLoader → Update State to LOADING
    ↓              ↓                         ↓
Set Loading    Query Blockchain         Show Progress Indicator
  Timeout           ↓                         ↓
    ↓         Content Retrieved         Content Available
    ↓              ↓                         ↓
Cache Result → Update State to LOADED → Return to User
    ↓              ↓                         ↓
Update Stats   Notify Listeners      Update UI/Platform
```

## Caching Strategy

### Cache Configuration
```java
public class CacheConfiguration {
    // Size limits
    private long maxSize = 10000;              // Max number of cached files
    private long maxMemorySize = 512L << 20;   // 512MB memory limit
    
    // Time-based eviction
    private Duration expireAfterWrite = Duration.ofHours(24);
    private Duration expireAfterAccess = Duration.ofHours(6);
    private Duration refreshAfterWrite = Duration.ofHours(12);
    
    // Eviction policy
    private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
    
    // Performance options
    private boolean compressionEnabled = true;
    private int concurrencyLevel = 16;
}
```

### Eviction Policies
- **LRU (Least Recently Used)**: Default policy, removes oldest accessed files
- **LFU (Least Frequently Used)**: Removes files accessed least often
- **FIFO (First In, First Out)**: Simple time-based eviction
- **PRIORITY_BASED**: Considers file priority and access patterns
- **SIZE_BASED**: Prioritizes keeping smaller files in cache

### Cache Statistics
```java
public class CacheStatistics {
    // Performance metrics
    private long hitCount;           // Cache hits
    private long missCount;          // Cache misses
    private double hitRate;          // Hit ratio (hits/total)
    
    // Loading metrics
    private long loadCount;          // Total loads from blockchain
    private long errorCount;         // Failed loads
    private long totalLoadTime;      // Total time spent loading
    private double averageLoadTime;  // Average load time per file
    
    // Memory metrics
    private long currentSize;        // Current cache size
    private long evictionCount;      // Number of evicted entries
    private long memoryUsage;        // Current memory usage
}
```

## Integration Points

### 1. Platform Integration (Layer 0)
```java
// Platform events trigger file availability checks
public class F1r3DriveChangeListener implements ChangeListener {
    @Override
    public void onFileAccessed(String path, AccessType accessType) {
        if (placeholderManager.isPlaceholder(path)) {
            // Trigger on-demand loading
            CompletableFuture<byte[]> content = placeholderManager.loadContent(path);
            content.thenAccept(data -> updateInMemoryFileSystem(path, data));
        }
    }
}
```

### 2. Filesystem Integration (Layer 5)
```java
// InMemoryFileSystem queries Layer 6 for file content
public class InMemoryFileSystem {
    public Optional<File> getFile(String path) {
        Optional<Node> node = getNode(path);
        if (node.isPresent() && node.get() instanceof File) {
            File file = (File) node.get();
            
            // Check if this is a placeholder that needs loading
            if (placeholderManager.isPlaceholder(path)) {
                PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
                if (info.getState() == PlaceholderState.NOT_LOADED) {
                    // Trigger background loading
                    placeholderManager.preloadContent(path);
                }
            }
            
            return Optional.of(file);
        }
        return Optional.empty();
    }
}
```

### 3. Blockchain Integration (Layer 9)
```java
// OnDemandLoader interfaces with blockchain client
public class OnDemandLoader {
    private CompletableFuture<byte[]> loadFromBlockchain(String blockchainAddress) {
        return blockchainClient.readFile(blockchainAddress)
            .thenApply(content -> {
                // Update placeholder state
                placeholderManager.updatePlaceholder(path, content);
                return content;
            })
            .exceptionally(throwable -> {
                // Handle loading errors
                handleLoadingError(blockchainAddress, throwable);
                return null;
            });
    }
}
```

## Error Handling and Recovery

### Error Types
1. **Network Errors**: Connection timeouts, network unavailable
2. **Blockchain Errors**: Invalid addresses, file not found
3. **Memory Errors**: Out of memory, cache full
4. **Concurrency Errors**: Loading conflicts, race conditions

### Recovery Strategies
```java
public class ErrorRecoveryManager {
    // Retry policies
    public void handleNetworkError(String path, NetworkException e) {
        RetryPolicy policy = RetryPolicy.exponentialBackoff()
            .maxAttempts(5)
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(1));
            
        retryLoadWithPolicy(path, policy);
    }
    
    // Fallback mechanisms
    public void handleMemoryPressure() {
        // Aggressive cache cleanup
        placeholderManager.evictExpiredEntries();
        
        // Reduce cache size temporarily
        cacheConfiguration.setMaxSize(cacheConfiguration.getMaxSize() / 2);
        
        // Notify system about memory pressure
        systemEventPublisher.publish(new MemoryPressureEvent());
    }
}
```

## Performance Optimizations

### 1. Predictive Loading
```java
public class PredictiveLoader {
    // Analyze access patterns to predict future loads
    public void analyzeAccessPattern(String path) {
        AccessPattern pattern = accessAnalyzer.analyze(path);
        
        if (pattern.isSequentialRead()) {
            // Preload next files in sequence
            preloadSequentialFiles(path, pattern.getDirection());
        }
        
        if (pattern.isDirectoryTraversal()) {
            // Preload entire directory
            preloadDirectory(getParent(path));
        }
    }
}
```

### 2. Batch Operations
```java
public class BatchLoadingManager {
    // Group multiple file requests for efficient loading
    public CompletableFuture<Map<String, byte[]>> batchLoad(Set<String> paths) {
        // Group by blockchain shard for efficient querying
        Map<String, Set<String>> shardGroups = groupByBlockchainShard(paths);
        
        List<CompletableFuture<Map<String, byte[]>>> futures = shardGroups.entrySet()
            .stream()
            .map(entry -> loadFromShard(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
            
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(HashMap::new, Map::putAll, Map::putAll));
    }
}
```

### 3. Memory Optimization
```java
public class MemoryOptimizer {
    // Compress cached content to save memory
    public byte[] compressContent(byte[] content) {
        if (content.length > compressionThreshold) {
            return compressionEngine.compress(content);
        }
        return content;
    }
    
    // Use weak references for infrequently accessed content
    public void optimizeCacheReferences() {
        placeholderCache.entrySet().removeIf(entry -> {
            PlaceholderInfo info = entry.getValue();
            return info.getLastAccessed() < getThresholdTime() 
                && info.getPriority() == PlaceholderPriority.LOW;
        });
    }
}
```

## Configuration Options

### Runtime Configuration
```java
public class Layer6Configuration {
    // Cache settings
    @ConfigProperty(name = "cache.max.size", defaultValue = "10000")
    private int maxCacheSize;
    
    @ConfigProperty(name = "cache.memory.limit", defaultValue = "512MB")
    private String memoryLimit;
    
    @ConfigProperty(name = "cache.ttl", defaultValue = "24h")
    private Duration cacheTTL;
    
    // Loading settings
    @ConfigProperty(name = "loading.timeout", defaultValue = "30s")
    private Duration loadingTimeout;
    
    @ConfigProperty(name = "loading.retry.max", defaultValue = "3")
    private int maxRetries;
    
    @ConfigProperty(name = "loading.concurrent.max", defaultValue = "10")
    private int maxConcurrentLoads;
    
    // Predictive loading
    @ConfigProperty(name = "predictive.loading.enabled", defaultValue = "true")
    private boolean predictiveLoadingEnabled;
    
    @ConfigProperty(name = "predictive.lookahead", defaultValue = "5")
    private int predictiveLookahead;
}
```

## Monitoring and Observability

### Metrics Collection
```java
@Component
public class Layer6Metrics {
    // Cache metrics
    @Gauge(name = "cache.hit.rate")
    public double getCacheHitRate() {
        return placeholderManager.getCacheStatistics().getHitRate();
    }
    
    @Counter(name = "files.loaded.total")
    private Counter filesLoadedCounter;
    
    @Timer(name = "file.loading.duration")
    private Timer loadingDurationTimer;
    
    // Memory metrics
    @Gauge(name = "cache.memory.usage")
    public long getCacheMemoryUsage() {
        return placeholderManager.getCacheStatistics().getMemoryUsage();
    }
    
    // Error metrics
    @Counter(name = "loading.errors.total")
    private Counter loadingErrorsCounter;
}
```

### Health Checks
```java
@Component
public class Layer6HealthCheck implements HealthIndicator {
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        // Check cache health
        CacheStatistics stats = placeholderManager.getCacheStatistics();
        if (stats.getErrorRate() > 0.1) {
            builder.down().withDetail("error_rate", stats.getErrorRate());
        }
        
        // Check memory usage
        long memoryUsage = stats.getMemoryUsage();
        long memoryLimit = cacheConfiguration.getMaxMemorySize();
        double memoryUtilization = (double) memoryUsage / memoryLimit;
        
        builder.withDetail("memory_utilization", memoryUtilization);
        if (memoryUtilization > 0.9) {
            builder.down().withDetail("memory_pressure", "high");
        }
        
        return builder.build();
    }
}
```

## Threading Model

### Concurrency Design
```java
public class Layer6ThreadingModel {
    // Dedicated thread pool for file loading
    private final ExecutorService loadingExecutor = 
        Executors.newFixedThreadPool(10, r -> {
            Thread t = new Thread(r, "file-loader");
            t.setDaemon(true);
            return t;
        });
    
    // Separate thread pool for cache maintenance
    private final ScheduledExecutorService cacheMaintenanceExecutor =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cache-maintenance");
            t.setDaemon(true);
            return t;
        });
    
    // Async event processing
    private final ExecutorService eventProcessor =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "layer6-events");
            t.setDaemon(true);
            return t;
        });
}
```

### Thread Safety
- All cache operations are thread-safe using concurrent data structures
- File loading operations use CompletableFuture for async processing
- State transitions are atomic using compare-and-swap operations
- Event publishing is decoupled using async event bus

## Security Considerations

### Access Control
```java
public class FileAccessController {
    // Validate access permissions before loading content
    public boolean canAccessFile(String path, WalletInfo wallet) {
        PlaceholderInfo info = placeholderManager.getPlaceholderInfo(path);
        return info != null && 
               info.getWalletAddress().equals(wallet.getAddress()) &&
               wallet.isUnlocked();
    }
    
    // Encrypt cached content
    public byte[] encryptCachedContent(byte[] content, String path) {
        WalletInfo wallet = getCurrentWallet(path);
        return aesEncryption.encrypt(content, wallet.getPrivateKey());
    }
}
```

### Data Integrity
- All cached content is verified against blockchain hashes
- Placeholder metadata includes checksums for validation
- Loading operations include integrity checks
- Cache corruption detection and automatic recovery

## Future Enhancements

### Planned Features
1. **Smart Prefetching**: ML-based prediction of file access patterns
2. **Distributed Caching**: Share cache across multiple F1r3Drive instances
3. **Content Deduplication**: Store identical content only once
4. **Compression Algorithms**: Advanced compression for better memory usage
5. **Tiered Storage**: Move less-accessed content to disk-based cache

### Scalability Improvements
1. **Horizontal Scaling**: Support for multiple loader instances
2. **Cache Partitioning**: Distribute cache across multiple memory regions
3. **Load Balancing**: Distribute blockchain queries across multiple nodes
4. **Adaptive Configuration**: Auto-tune cache sizes based on usage patterns

## Summary

Layer 6 (File Availability Management) provides the critical bridge between F1r3Drive's in-memory filesystem and the blockchain storage network. Through sophisticated caching, predictive loading, and error recovery mechanisms, it ensures that users experience fast, reliable access to their blockchain-stored files while optimizing network usage and memory consumption.

The layer's design emphasizes:
- **Performance**: Through intelligent caching and predictive loading
- **Reliability**: Through comprehensive error handling and retry logic
- **Scalability**: Through efficient resource management and batch operations
- **Observability**: Through comprehensive metrics and health monitoring
- **Security**: Through access control and data integrity verification

This foundation enables F1r3Drive to provide a seamless file system experience while leveraging the benefits of decentralized blockchain storage.