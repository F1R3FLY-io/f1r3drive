# F1r3Drive Documentation Index

**Welcome to F1r3Drive documentation** - A native macOS filesystem that integrates with the F1r3fly blockchain using File Provider Framework and FSEvents API.

---

## 📚 Quick Navigation

### 🚀 Getting Started (Start Here)

| Document | Description | When to Read |
|----------|-------------|--------------|
| **[README](README.md)** | **Overview, quick start, installation** | **First time setup** |
| [Configuration](CONFIGURATION.md) | CLI options, examples, environment variables | Running F1r3Drive |
| [Features](FEATURES.md) | Implemented and planned features | Evaluating capabilities |

### 🏗️ Architecture & Technical

| Document | Description | When to Read |
|----------|-------------|--------------|
| **[Data Flow](DATA_FLOW.md)** | **Sequence diagrams, component interactions** | **Debugging issues** |
| **[Native macOS Implementation](NATIVE_MACOS_IMPLEMENTATION.md)** | **Native JNI modules, File Provider, FSEvents** | **Understanding native code** |
| **[Storage Structure](RHOLANG_STORAGE.md)** | **Blockchain storage format, Rholang contracts** | **Understanding data layer** |

### 🔧 Development

| Document | Description | When to Read |
|----------|-------------|--------------|
| [INSTALLATION.md](../INSTALLATION.md) | Platform-specific installation | Setting up dev environment |
| [Demo.md](../Demo.md) | Step-by-step usage guide | Testing functionality |

---

## 🎯 Recommended Reading Order

### For New Users

1. **[README](README.md)** - What is F1r3Drive
2. **[Configuration](CONFIGURATION.md)** - How to run it
3. **[Demo.md](../Demo.md)** - Try it out
4. **[Features](FEATURES.md)** - What you can do

### For Developers

1. **[README](README.md)** - Project overview
2. **[Native macOS Implementation](NATIVE_MACOS_IMPLEMENTATION.md)** - Native modules (JNI, File Provider, FSEvents)
3. **[Data Flow](DATA_FLOW.md)** - How it works internally (Use Cases + Diagrams)
4. **[Storage Structure](RHOLANG_STORAGE.md)** - Blockchain data storage
5. **[Features](FEATURES.md)** - Implementation status

---

## 📖 Document Summaries

### [README.md](README.md)

**Purpose:** Project overview and quick start  
**Key Sections:**
- What makes F1r3Drive unique (Native macOS vs FUSE)
- Architecture diagram
- Quick start guide
- Advantages over FUSE
- Technical specifications

**Read this first if:** You're new to F1r3Drive

---

### [CONFIGURATION.md](CONFIGURATION.md)

**Purpose:** Complete CLI reference  
**Key Sections:**
- Basic syntax
- Positional arguments
- Options (Blockchain, Encryption, Wallet, macOS Integration, Debugging)
- Environment variables
- Configuration file format
- Example commands (Development, Minimal, Production)
- Native library loading

**Read this if:** You need to configure or troubleshoot F1r3Drive

---

### [FEATURES.md](FEATURES.md)

**Purpose:** Comprehensive feature list  
**Key Sections:**
- Implemented features (Native macOS, File Ops, Placeholders, Wallet, Caching)
- Extension features (Future Finder Sync, Spotlight, Quick Look)
- Planned features (Performance, Enhanced Native, Advanced Ops)
- Feature comparison: Native vs FUSE
- Implementation status by phase
- Performance metrics
- Known limitations
- E2E test coverage

**Read this if:** You want to know what works and what's planned

---

### [NATIVE_MACOS_IMPLEMENTATION.md](NATIVE_MACOS_IMPLEMENTATION.md)

**Purpose:** Deep dive into native macOS modules  
**Key Sections:**
- Why native instead of FUSE (detailed comparison)
- Architecture with component diagrams
- Native library implementation (fileprovider_integration.m, fsevents_integration.m)
- JNI bridge code (Java ↔ Objective-C)
- Performance benchmarks (2-3x faster than FUSE)
- Security advantages (App Sandbox, no kernel extension)
- Build instructions for native libraries

**Read this if:** You want to understand or modify the native macOS code

---

### [DATA_FLOW.md](DATA_FLOW.md)

**Purpose:** Detailed sequence diagrams and use cases  
**Key Sections:**
- System architecture overview
- Use Case 1: Application Startup (all classes)
- Use Case 2: Create File (sequence diagram)
- Use Case 3: Read File (placeholder materialization)
- Use Case 4: Write File (content update)
- Use Case 5: Delete File
- Use Case 6: Wallet Unlock
- Background processes (Deploy Queue, Cache Eviction)
- Performance metrics

**Read this if:** You're debugging or need to understand how operations work

---

### [RHOLANG_STORAGE.md](RHOLANG_STORAGE.md)

**Purpose:** Blockchain storage structure  
**Key Sections:**
- Channel-based addressing
- Directory nodes (JSON + Rholang examples)
- File metadata nodes
- Data chunks (chunking strategy)
- File operations in Rholang (create, read, write, delete, rename)
- Token storage (REV, custom tokens)
- Encryption scheme (AES-256-GCM)
- Data integrity (hash verification)
- Gas costs

**Read this if:** You need to understand blockchain storage

---

## 🔍 Find Information By Topic

### Installation & Setup

- [README.md](README.md) - Quick start, prerequisites
- **[CONFIGURATION.md](CONFIGURATION.md)** - CLI options, examples
- **[INSTALLATION.md](../INSTALLATION.md)** - Detailed installation guide

### Native macOS Features

- [README.md](README.md) - Native features overview
- **[FEATURES.md](FEATURES.md)** - Native features list
- **[NATIVE_MACOS_IMPLEMENTATION.md](NATIVE_MACOS_IMPLEMENTATION.md)** - File Provider, FSEvents, JNI details
- **[DATA_FLOW.md](DATA_FLOW.md)** - Native module interactions

### Performance

- **[FEATURES.md](FEATURES.md)** - Performance metrics
- **[DATA_FLOW.md](DATA_FLOW.md)** - Latency breakdown by operation

### Security

- [README.md](README.md) - App Sandbox, code signing
- **[CONFIGURATION.md](CONFIGURATION.md)** - Encryption options
- **[RHOLANG_STORAGE.md](RHOLANG_STORAGE.md)** - Encryption scheme (AES-256-GCM)

### Blockchain Integration

- **[RHOLANG_STORAGE.md](RHOLANG_STORAGE.md)** - Storage structure
- **[DATA_FLOW.md](DATA_FLOW.md)** - Data flow to blockchain
- **[FEATURES.md](FEATURES.md)** - Blockchain features

### Troubleshooting

- [README.md](README.md) - Common issues
- **[CONFIGURATION.md](CONFIGURATION.md)** - Native library loading
- **[FEATURES.md](FEATURES.md)** - Known limitations
- **[DATA_FLOW.md](DATA_FLOW.md)** - Debug with sequence diagrams

---

## 📊 Documentation Statistics

| Metric | Value |
|--------|-------|
| **Total Documents** | 7 (client-facing) |
| **Total Pages** | ~60 pages |
| **Code Examples** | 60+ |
| **Diagrams** | 20+ (ASCII art) |
| **Tables** | 60+ |
| **Last Updated** | 2026-03-30 |

---

## 🎯 Key Differentiators

Your F1r3Drive implementation has these unique features:

### 1. Native macOS Integration
- File Provider Framework (not FUSE)
- FSEvents API for monitoring
- JNI bridge for performance

### 2. Superior Performance
- 2-3x faster than FUSE
- 50% less memory usage
- Kernel-level efficiency

### 3. Better Security
- No kernel extension required
- App Sandbox compatible
- Apple-approved APIs

### 4. Enhanced User Experience
- Native Finder sidebar integration
- Placeholder files (zero bytes on disk)
- System feature compatibility (Spotlight, Quick Look, Time Machine ready)

---

## 🔗 External Resources

- **GitHub Repository:** https://github.com/f1r3fly-io/f1r3drive
- **F1r3fly Documentation:** https://github.com/F1R3FLY-io/f1r3fly
- **Rholang Documentation:** https://developers.rchain.coop/
- **File Provider Framework:** https://developer.apple.com/documentation/fileprovider
- **FSEvents API:** https://developer.apple.com/documentation/coreservices/file_system_events

---

## 📞 Getting Help

1. **Check Documentation:** Start with this index
2. **Review Examples:** See [CONFIGURATION.md](CONFIGURATION.md) for examples
3. **Check Known Issues:** See [FEATURES.md](FEATURES.md) limitations
4. **Enable Debug Mode:** Use `--verbose` flag
5. **View Logs:** `~/Library/Logs/f1r3drive.log`

---

*Last updated: 2026-03-30*  
