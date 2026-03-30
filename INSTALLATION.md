Installation
==

## Prerequisites

### Required Software

- **Java 17+** (Temurin recommended)
- **Docker and Docker Compose** (for running F1r3fly shard)
- **Git** (for cloning system-integration repository)

### Platform-Specific Requirements

## macOS (Recommended)

F1r3Drive uses **native macOS File Provider Framework** - no FUSE required!

**Requirements:**
- macOS 10.15+ (Catalina or later)
- Xcode Command Line Tools

```bash
# Install Xcode Command Line Tools
xcode-select --install

# Install JDK 17 (Temurin)
brew install --cask temurin
```

**Note:** The native macOS implementation provides better performance and Finder integration compared to FUSE-based solutions.

## Linux

F1r3Drive on Linux uses FUSE (Filesystem in Userspace).

[`libfuse`](https://github.com/libfuse/libfuse) needs to be installed.

#### Ubuntu/Debian
```bash
sudo apt-get install libfuse-dev fuse
```

#### Fedora/RHEL
```bash
sudo dnf install fuse fuse-devel
```

#### Arch Linux
```bash
sudo pacman -S fuse2
```

## Windows (Future)

Windows support is planned using WinFsp.

A library implementing the fuse API needs to be installed and the library path must be set via the `jnr-fuse.windows.libpath` system property.
If the system property is not set, jnr-fuse falls back to [`winfsp`](https://github.com/billziss-gh/winfsp), if it is installed.

```batch
choco install winfsp
```

---

## Quick Start

### 1. Clone the Project

```bash
git clone https://github.com/f1r3fly-io/f1r3drive.git
cd f1r3drive
```

### 2. Build the Project

```bash
# Enable development environment (if using direnv)
direnv allow

# Build macOS JAR (recommended for macOS)
./gradlew shadowJarMacOS -x test

# Or build all-platforms JAR
./gradlew shadowJar -x test
```

### 3. Start F1r3Drive

**Recommended:** Use the provided `run.sh` script (macOS):

```bash
./run.sh
```

This script automatically:
- Starts required Docker nodes
- Cleans mount point and cache
- Launches F1r3Drive with correct configuration

**Manual start:** See [Demo.md](Demo.md) for detailed instructions.

---

## Troubleshooting

### macOS

* **Native library loading errors:**
  - Ensure Xcode Command Line Tools are installed: `xcode-select --install`
  - Rebuild native libraries: `cd src/macos/native && make`

* **FileProvider not appearing in Finder:**
  - Reset File Provider database: `fileproviderctl reset`
  - Restart Finder: `killall Finder`
  - Reboot system if necessary

* **Placeholder files not materializing:**
  - Check logs: `tail -f ~/Library/Logs/f1r3drive.log`
  - Verify File Provider is enabled in System Preferences

### Linux

* If you see the `service java has failed to start` error or corrupted file names/content, setting
the explicit file encoding `-Dfile.encoding=UTF-8` might help.

* **FUSE not found:**
  - Ensure FUSE is installed: `sudo apt-get install libfuse-dev`
  - Load FUSE module: `sudo modprobe fuse`

* **Permission denied:**
  - Add user to fuse group: `sudo usermod -a -G fuse $USER`
  - Log out and log back in

### Windows (Future)

* Ensure WinFsp service is running
* Set system property: `-Djnr-fuse.windows.libpath=C:\Program Files\WinFsp\bin\winfsp-x64.dll`

---

## Configuration

For detailed configuration options, see [Demo.md](Demo.md) or [docs/configuration.md](docs/configuration.md).

**Key configuration files:**
- **Shard config:** `system-integration/compose/f1r3node-rust.yml`
- **Wallet addresses:** `local-shard/genesis/wallets.txt`
- **Cipher key:** `~/cipher.key` (create your own)

---

## Next Steps

After installation:
1. Read [Demo.md](Demo.md) for usage examples
2. Check [docs/README.md](docs/README.md) for project overview
3. Review [docs/features.md](docs/features.md) for supported features
