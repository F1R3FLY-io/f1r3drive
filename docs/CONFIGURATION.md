# F1r3Drive CLI Configuration Reference

This document describes the command-line interface configuration for F1r3Drive, a **native macOS filesystem** that integrates with the F1r3fly blockchain network using File Provider Framework and FSEvents API.

## Overview

F1r3Drive uses [`picocli`](https://picocli.info/) for command-line argument parsing. The main entry point is the `F1r3DriveCli` class located in `io.f1r3fly.f1r3drive.app`.

**Platform:** macOS 10.15+ (Catalina or later)  
**Native Integration:** File Provider Framework + FSEvents API  
**Build:** Platform-specific JAR (`f1r3drive-macos-*.jar`)

## Basic Syntax

```bash
java -jar f1r3drive-macos-*.jar <mount_point> [OPTIONS]
```

## Positional Arguments

| Argument | Required | Description |
|----------|----------|-------------|
| `mount_point` | Yes | Directory path where F1r3Drive will be mounted (e.g., `~/demo-f1r3drive`) |

## Options

### Blockchain Connection

Configure connection to F1r3fly shard nodes.

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--validator-host` | `-vh` | No | `localhost` | Hostname of the validator node for gRPC communication |
| `--validator-port` | `-vp` | No | `40402` | Port of the validator node |
| `--observer-host` | `-oh` | No | `localhost` | Hostname of the observer node for read operations |
| `--observer-port` | `-op` | No | `40442` | Port of the observer node |

**Note:** F1r3Drive connects to both validator and observer nodes. Validator is used for write operations (deploys), observer for read operations (queries).

### Encryption

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--cipher-key-path` | `-ck` | Yes | - | Path to the AES cipher key file for file encryption/decryption |

**Security Note:** The cipher key file should contain a secure random key (128/256-bit). Never commit this file to version control.

### Wallet / Identity

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--rev-address` | `-ra` | No | - | REV address for wallet access (e.g., `111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA`) |
| `--private-key` | `-pk` | No | - | Private key for wallet unlocking (hex format, 64 characters) |

**Important:** 
- `--rev-address` and `--private-key` must be used together to unlock a specific wallet
- Private key is stored only in memory and cleared on shutdown
- Without these flags, F1r3Drive operates in read-only mode for public data

### Propose Mode

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--manual-propose` | `-mp` | No | `false` | Enable manual propose mode for development/testing |

**Modes:**
- `--manual-propose=true`: Waits for block proposal and finalization after each deploy (development/testing only)
- `--manual-propose=false`: Deploys only, relies on auto-propose (production shards)

### macOS Integration

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--platform` | `-pl` | No | Auto-detect | Platform variant: `macos` (recommended), `linux`, `windows` |
| `--file-provider-enabled` | `-fp` | No | `true` | Enable File Provider Framework integration |
| `--deep-integration` | `-di` | No | `true` | Enable full macOS integration (Finder, Spotlight, Quick Look) |

**Note:** Use `f1r3drive-macos-*.jar` for native macOS features.

### Debugging

| Flag | Short | Required | Default | Description |
|------|-------|----------|---------|-------------|
| `--verbose` | `-v` | No | `false` | Enable verbose logging (DEBUG level) |

## Built-in Help

| Flag | Description |
|------|-------------|
| `--help` | Show help message and exit |
| `--version` | Print version information and exit |

## Full Example Commands

### Development Setup (Local Shard)

Complete example for local development with a 2-node shard:

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

### Minimal Setup (Read-Only)

Read-only access without wallet unlocking:

```bash
java -jar f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost \
   --validator-port 40402
```

### Production Setup (Remote Shard)

Connect to a remote production shard:

```bash
java -jar f1r3drive-macos-0.1.1.jar ~/f1r3drive \
   --cipher-key-path ~/.f1r3drive/cipher.key \
   --validator-host validator.example.com \
   --validator-port 40402 \
   --observer-host observer.example.com \
   --observer-port 40442 \
   --manual-propose=false \
   --rev-address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
   --private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9
```

### Multiple Wallets (All Genesis Wallets)

Mount with all wallets from genesis block:

```bash
java -jar f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost \
   --validator-port 40402 \
   --all-wallets=true
```

## Environment Variables

Alternatively, some configuration can be provided via environment variables:

| Variable | Equivalent Flag |
|----------|-----------------|
| `F1R3_CIPHER_KEY_PATH` | `--cipher-key-path` |
| `F1R3_VALIDATOR_HOST` | `--validator-host` |
| `F1R3_OBSERVER_HOST` | `--observer-host` |
| `F1R3_PLATFORM` | `--platform` |

## Configuration File

F1r3Drive can read configuration from a properties file:

**Location:** `~/.f1r3drive/config.properties`

```properties
# Blockchain Connection
validator.host=localhost
validator.port=40402
observer.host=localhost
observer.port=40442

# Encryption
cipher.key.path=~/cipher.key

# Wallet
rev.address=111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA
private.key=357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9

# macOS Integration
file.provider.enabled=true
deep.integration=true

# Mode
manual.propose=false
verbose=false
```

## Native Library Loading

F1r3Drive automatically loads native libraries for macOS integration:

**Libraries:**
- `libf1r3drive-fileprovider.dylib` - File Provider Framework bridge
- `libf1r3drive-fsevents.dylib` - FSEvents API bridge

**Location:** Bundled in `f1r3drive-macos-*.jar`

**Fallback:** If native libraries fail to load, F1r3Drive continues with reduced functionality (File Provider disabled).

## Related Documentation

- [Native macOS Implementation](NATIVE_MACOS_IMPLEMENTATION.md) - Native module details
- [Features](FEATURES.md) - List of implemented and planned features
- [Data Flow](f1r3flyfs_flow.md) - How data moves through the system

