# Rholang Data Storage Structure

This document describes how F1r3Drive stores filesystem data on the F1r3fly blockchain using Rholang smart contracts.

---

## Overview

F1r3Drive represents the filesystem as a hierarchy of Rholang channels, where each file and directory is stored as structured data on the blockchain. The storage model uses a channel-based addressing scheme that mirrors the filesystem path structure.

---

## Storage Model Diagram

```
Filesystem Hierarchy                      Blockchain Representation
                                                                    
  ┌──────────┐                            ┌──────────────┐          
  │   root   │                            │  @{root}     │          
  └────┬─────┘                            └──────┬───────┘          
       │                                         │                   
       ▼                                         ▼                   
  ┌──────────┐                            ┌──────────────┐          
  │  wallet  │                            │ @{wallet}    │          
  └────┬─────┘                            └──────┬───────┘          
       │                                         │                   
       ├────────────────┐                        ├────────────────┐  
       ▼                ▼                        ▼                ▼  
┌──────────────┐ ┌──────────────┐      ┌──────────────┐   ┌──────────────┐
│   .tokens    │ │  documents   │      │@{wallet}/    │   │@{wallet}/    │
│              │ │              │      │.tokens       │   │documents     │
└──────────────┘ └──────┬───────┘      └──────────────┘   └──────┬───────┘
                        │                                         │          
                        ├──────────────┐                          ├──────────┐
                        ▼              ▼                          ▼          ▼
                 ┌────────────┐ ┌────────────┐         ┌────────────┐ ┌────────────┐
                 │  file1.txt │ │  file2.txt │         │.../data    │ │.../meta    │
                 └────────────┘ └────────────┘         └────────────┘ └────────────┘
```

---

## Components of the Storage Model

### 1. Channel-Based Addressing

Each filesystem node is stored at a unique channel derived from its path:

```rholang
// Root directory
@"f1r3drive/root"

// Wallet directory
@"f1r3drive/root/{revAddress}"

// File or directory
@"f1r3drive/root/{revAddress}/{pathComponents...}"
```

**Example:**
```rholang
// File: /wallet_111127RX.../documents/report.txt
@"f1r3drive/root/wallet_111127RX.../documents/report.txt"
```

**Class:** `RholangExpressionConstructor` generates these paths

---

### 2. Directory Nodes

Directories are stored as maps containing child entries:

```json
{
  "type": "directory",
  "name": "documents",
  "createdBy": "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
  "createdAt": 1703001234567,
  "permissions": "rwxr-xr-x",
  "children": {
    "file1.txt": {
      "type": "file",
      "hash": "abc123...",
      "size": 1024,
      "encrypted": true
    },
    "subfolder": {
      "type": "directory",
      "hash": "def456..."
    }
  }
}
```

**Rholang Storage:**
```rholang
@"f1r3drive/root/wallet_111127RX.../documents"!(
  {
    "type": "directory",
    "name": "documents",
    "children": {
      "file1.txt": {"type": "file", "hash": "abc123...", "size": 1024},
      "subfolder": {"type": "directory", "hash": "def456..."}
    },
    "createdAt": 1703001234567,
    "permissions": "rwxr-xr-x"
  }
)
```

**Class:** `BlockchainDirectory` manages directory state

---

### 3. File Metadata Nodes

Each file has associated metadata stored separately from content:

```json
{
  "type": "file",
  "name": "report.txt",
  "path": "documents/report.txt",
  "createdBy": "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
  "createdAt": 1703001234567,
  "modifiedAt": 1703005678901,
  "size": 2048,
  "encrypted": true,
  "encryptionAlgo": "AES-256-GCM",
  "mimeType": "text/plain",
  "permissions": "rw-r--r--",
  "chunks": [
    {
      "index": 0,
      "hash": "sha256:abc123...",
      "size": 1048576
    },
    {
      "index": 1,
      "hash": "sha256:def456...",
      "size": 1000000
    }
  ]
}
```

**Class:** `BlockchainFile` manages file metadata

---

### 4. Data Chunks

File content is split into chunks (currently 16MB max, planned 1MB):

```rholang
// Chunk storage channel
@"f1r3drive/root/wallet_111127RX.../documents/report.txt/chunk/0"

// Chunk data (hex-encoded, encrypted)
@"f1r3drive/root/wallet_111127RX.../documents/report.txt/chunk/0"!(
  "0a1b2c3d4e5f..."  // Hex-encoded encrypted binary data
)
```

**Chunk Structure:**
```json
{
  "index": 0,
  "data": "0a1b2c3d4e5f...",
  "hash": "sha256:abc123...",
  "size": 1048576,
  "encrypted": true,
  "compression": "none"
}
```

---

## File Operations in Rholang

### Create File

```rholang
// Create file with initial content
new return in {
  @"f1r3drive/root/wallet_111127RX..."!(
    {
      "op": "createFile",
      "path": "documents/report.txt",
      "content": "Hello, Blockchain!",
      "permissions": "rw-r--r--",
      "timestamp": 1703001234567
    },
    *return
  )
}
```

**Class:** `RholangExpressionConstructor.forCreateFile()`

---

### Write File

```rholang
// Write/Update file content
new return in {
  @"f1r3drive/root/wallet_111127RX.../documents/report.txt"!(
    {
      "op": "write",
      "content": "Updated content here",
      "chunkIndex": 0,
      "timestamp": 1703005678901
    },
    *return
  )
}
```

**Class:** `RholangExpressionConstructor.forWriteFile()`

---

### Read File

```rholang
// Read file content (query, not deploy)
new return in {
  @"f1r3drive/root/wallet_111127RX.../documents/report.txt"!(
    {
      "op": "read",
      "chunkIndices": [0, 1]
    },
    *return
  )
}
```

**Response:**
```json
{
  "chunks": [
    {
      "index": 0,
      "data": "0a1b2c3d...",
      "hash": "sha256:abc123..."
    },
    {
      "index": 1,
      "data": "4e5f6a7b...",
      "hash": "sha256:def456..."
    }
  ]
}
```

**Class:** `RholangExpressionConstructor.forReadFile()`

---

### Delete File

```rholang
// Delete file
new return in {
  @"f1r3drive/root/wallet_111127RX..."!(
    {
      "op": "delete",
      "path": "documents/report.txt",
      "timestamp": 1703009876543
    },
    *return
  )
}
```

**Class:** `RholangExpressionConstructor.forDeleteFile()`

---

### Rename/Move File

```rholang
// Rename file
new return in {
  @"f1r3drive/root/wallet_111127RX..."!(
    {
      "op": "rename",
      "oldPath": "documents/report.txt",
      "newPath": "documents/report_final.txt",
      "timestamp": 1703009876543
    },
    *return
  )
}
```

**Class:** `RholangExpressionConstructor.forRenameFile()`

---

## Token Storage

### REV Balance Token

```json
{
  "tokenType": "REV",
  "balance": "1000000000",
  "walletAddress": "111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA",
  "lastUpdated": "2023-12-19T14:39:00Z",
  "blockHeight": 12345
}
```

**Rholang Storage:**
```rholang
@"f1r3drive/root/wallet_111127RX.../.tokens/REV"!(
  {
    "type": "token",
    "name": "REV",
    "balance": "1000000000",
    "lastUpdated": 1703001234567
  }
)
```

**Class:** `TokenDirectory`, `TokenFile`

---

## Encryption Scheme

### File Content Encryption

```
Plaintext → AES-256-GCM → Ciphertext → Hex Encode → Blockchain Storage
                ↓
           Cipher Key (from --cipher-key-path)
```

**Encryption Process:**
```java
// Pseudocode from AESCipher
byte[] plaintext = "Hello, Blockchain!".getBytes();
byte[] cipherKey = loadCipherKey("~/cipher.key");
byte[] iv = generateIV();
byte[] ciphertext = aesEncrypt(plaintext, cipherKey, iv);
String hexData = bytesToHex(iv) + bytesToHex(ciphertext);
// Store hexData on blockchain
```

**Decryption Process:**
```java
// Pseudocode from AESCipher
String hexData = retrieveFromBlockchain();
byte[] iv = hexToBytes(hexData.substring(0, 32));
byte[] ciphertext = hexToBytes(hexData.substring(32));
byte[] plaintext = aesDecrypt(ciphertext, cipherKey, iv);
return new String(plaintext);
```

**Class:** `AESCipher` (Singleton)

---

## Data Integrity

### Hash Verification

Each chunk includes a SHA-256 hash for integrity verification:

```json
{
  "index": 0,
  "data": "0a1b2c3d...",
  "hash": "sha256:abc123..."  // Hash of decrypted data
}
```

**Verification Flow:**
```
1. Retrieve chunk from blockchain
2. Hex decode → Decrypt → Plaintext
3. Compute SHA-256 hash of plaintext
4. Compare with stored hash
5. If match: return data
6. If mismatch: error (data corruption)
```

**Class:** `BlockchainFile.verifyChunkHash()`

---

## State Synchronization

### Deploy → Propose → Finalize

```
┌──────────────────┐      ┌────────────┐      ┌─────────┐      ┌──────────┐
│ F1r3Drive Client │      │ Validator  │      │  Shard  │      │ Observer │
└────────┬─────────┘      └──────┬─────┘      └────┬────┘      └────┬─────┘
         │                      │                  │                │
         │ Deploy Rholang       │                  │                │
         │ contract             │                  │                │
         │─────────────────────▶│                  │                │
         │                      │                  │                │
         │                      │ Add to block     │                │
         │                      │─────────────────▶│                │
         │                      │                  │                │
         │                      │ Replicate block  │                │
         │                      │─────────────────────────────────▶│
         │                      │                  │                │
         │                      │                  │ Consensus      │
         │                      │                  │ reached        │
         │                      │                  │                │
         │                      │ Deploy accepted  │                │
         │                      │─────────────────▶│                │
         │                      │◀─────────────────│                │
         │                      │                  │                │
         │                      │ Block proposed   │                │
         │                      │─────────────────▶│                │
         │                      │◀─────────────────│                │
         │                      │                  │                │
         │                      │ Block finalized  │                │
         │                      │─────────────────▶│                │
         │                      │◀─────────────────│                │
         │                      │                  │                │
         │◀─────────────────────│                  │                │
         │ State consistent     │                  │                │
         │                      │                  │                │
```

**Classes:** `DeployDispatcher`, `F1r3flyBlockchainClient`

---

## Gas Costs

### Typical Gas Consumption

| Operation | Gas Cost | Notes |
|-----------|----------|-------|
| Create File | ~100-200 | Simple deploy |
| Write Chunk | ~200-500 | Depends on chunk size |
| Read File | ~50-100 | Query only |
| Delete File | ~100-200 | Simple deploy |
| Rename | ~100-200 | Metadata update |

**Note:** Gas costs vary based on Rholang complexity and shard configuration.

---

## Limitations

### Current Limitations

| Limitation | Value | Impact |
|------------|-------|--------|
| Max gRPC Message Size | 16MB | Limits chunk size |
| Max Deploy Size | ~1MB | Limits metadata complexity |
| Block Time | 10-20s | Affects write latency |
| State Size | Unlimited | But affects query performance |

### Planned Improvements

| Improvement | Target | Status |
|-------------|--------|--------|
| Chunked Uploads | 1MB chunks | 🔴 Critical |
| Streaming API | Progressive transfer | 🟡 High |
| Async Reads | Non-blocking | 🔴 Critical |
| Compression | Reduce storage | 🟡 Medium |

---

## Class Responsibilities

| Class | Package | Storage Responsibility |
|-------|---------|----------------------|
| `RholangExpressionConstructor` | `blockchain.rholang` | Generate Rholang contracts |
| `BlockchainFile` | `filesystem.deployable` | Manage file metadata + chunks |
| `BlockchainDirectory` | `filesystem.deployable` | Manage directory structure |
| `TokenFile` | `filesystem.local` | Token balance representation |
| `AESCipher` | `encryption` | Encrypt/decrypt content |
| `DeployDispatcher` | `blockchain` | Queue and sequence deploys |
| `F1r3flyBlockchainClient` | `blockchain.client` | Send contracts to shard |

---

## 🔗 Related Documentation

- [Data Flow](DATA_FLOW.md) - How operations flow through the system
- [Features](FEATURES.md) - Implemented features
- [Configuration](CONFIGURATION.md) - CLI options

---

*Last updated: 2026-03-30*
