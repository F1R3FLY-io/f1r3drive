# Prerequisites
- [Docker and Docker Compose](https://www.docker.com/)
- [MacFuse](https://github.com/macfuse/macfuse/wiki/Getting-Started)
- [Git](https://git-scm.com/)

# Running the F1r3fly shard locally

## Option 1: Quick Start with run.sh (Recommended)

The easiest way to start F1r3Drive is using the provided `run.sh` script:

```sh
# Build the project first
./gradlew shadowJarMacOS -x test

# Run with the script
./run.sh
```

The script automatically:
- Starts the required Docker nodes (Validator1, Validator2, Observer)
- Cleans the mount point and cache
- Launches F1r3Drive with the correct configuration

**Configuration used by run.sh:**
- **Validator1**: `localhost:40412` (gRPC port for writes)
- **Observer**: `localhost:40452` (gRPC port for reads)
- **Config**: `f1r3node-rust.yml` from system-integration/compose/
- **Wallet**: Uses REV address from `local-shard/genesis/wallets.txt`

## Option 2: Manual Start

### Step 1: Clone and start the shard

Clone the system-integration repository:
```sh
git clone https://github.com/F1R3FLY-io/system-integration.git
cd system-integration/compose
```

Run the shard using the f1r3node-rust.yml config:
```sh
docker-compose -f f1r3node-rust.yml up -d validator1 validator2 readonly
```

Wait for "Listening for traffic" logs:
```sh
docker-compose -f f1r3node-rust.yml logs
```

Logs should be like:
```text
Listening for traffic on rnode://...@localhost?protocol=40400&discovery=40404
```

### Step 2: Get wallet credentials

Wallet addresses and private keys are configured in:
- **Wallets**: [local-shard/genesis/wallets.txt](local-shard/genesis/wallets.txt)
- **Private keys**: Use the corresponding private keys for these addresses

**Example wallets from wallets.txt:**
```
111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA,50000000000000000
1111AtahZeefej4tvVR6ti9TJtv8yxLebT31SCEVDCKMNikBk5r3g,50000000000000000
111129p33f7vaRrpLqK8Nr35Y2aacAjrR5pd6PCzqcdrMuPHzymczH,50000000000000000
```

# Run F1r3Drive app

1. Download the latest `f1r3drive-*.jar` from the [GitHub Releases page](https://github.com/f1r3fly-io/f1r3drive/releases).

2. Run F1r3Drive app with REV Address and Private key:

**Manual Propose Option:**
- `--manual-propose=true`: Manual deployment flow with propose and finalization waiting (for development/testing only)
- `--manual-propose=false`: Deploy only, skip propose and finalization waiting (production mode - used by run.sh)

**macOS Version:**
For macOS, use the platform-specific JAR file `f1r3drive-macos-0.1.1.jar` which includes native macOS integration and Caffeine-based caching for optimal performance.

- If you build the jar locally, run:
```sh
java -jar ./build/libs/f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost --validator-port 40412 \
   --observer-host localhost --observer-port 40452 \
   --manual-propose=false \
   --rev-address <YOUR_REV_ADDRESS> \
   --private-key <YOUR_PRIVATE_KEY>
```

- If you downloaded the JAR to your `~/Downloads` folder, run:
```sh
java -jar ~/Downloads/f1r3drive-macos-0.1.1.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost --validator-port 40412 \
   --observer-host localhost --observer-port 40452 \
   --manual-propose=false \
   --rev-address <YOUR_REV_ADDRESS> \
   --private-key <YOUR_PRIVATE_KEY>
```

**Replace `<YOUR_REV_ADDRESS>` and `<YOUR_PRIVATE_KEY>` with values from wallets.txt and your key storage.**

**Example (for reference - from wallets.txt):**
```sh
--rev-address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
--private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9
```

# Demo

Creating a tiny file inside ~/demo-f1r3drive folder:

```sh
echo "abc" > ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/demo.txt
ls -lh ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/demo.txt
cat ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/demo.txt
```

Copy 1M file inside ~/demo-f1r3drive folder:

```sh
# generating binary file with 1M size
dd if=/dev/zero of=large_data.txt bs=1m count=1

# copy file (use rsync for progress logs)
cp large_data.txt ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/
# OR: rsync -av --progress large_data.txt ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/

# wait for some time, then verify the file is copied
ls -lh ~/demo-f1r3drive/111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA/large_data.txt
```

# Cleanup

Stop all processes and remove all files:

```sh
# Hit Ctrl+C in F1r3Drive app

# Or kill if stuck
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9

# Force unmount ~/demo-f1r3drive if stuck
sudo diskutil umount force ~/demo-f1r3drive

# Stop Shard (if using Option 2 manual start)
cd system-integration/compose
docker-compose -f f1r3node-rust.yml down

# ~/demo-f1r3drive has to be empty folder
# Delete ~/demo-f1r3drive before running the next time
rm -rf ~/demo-f1r3drive

# Remove large_data.txt
rm -f large_data.txt
```

**Note:** If using `run.sh`, just press Ctrl+C to stop. The script handles cleanup automatically on next run.
