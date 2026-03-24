# F1r3Drive

F1r3Drive is a FUSE-based filesystem that stores data on the F1r3fly blockchain. It is built in Java using [JNR-FUSE](https://github.com/SerCeMan/jnr-fuse) and connects to a running F1r3fly node via gRPC.

## Prerequisites

The only strict prerequisite is having the FUSE libraries installed for your operating system:

- **macOS** — install [macFUSE](https://github.com/macfuse/macfuse/wiki/Getting-Started).
- **Linux / Windows** — see the [jnr-fuse installation guide](INSTALLATION.md).

### macOS Finder Extension (Optional)

For advanced file and folder operations in Finder (such as custom sync badges and context menus), you can install the [F1r3Drive Extension](https://github.com/F1R3FLY-io/f1r3drive-extension). 

This extension is **optional**. Without it, the basic FUSE functionality works perfectly out of the box:
- Unlocking a single root folder
- Basic read/write operations for files and folders
- Accessing the hidden `.token` configuration folder

## Getting Started

There are two parts to using F1r3Drive: running a F1r3fly shard and running the F1r3Drive application.

### 1. Running a F1r3fly Shard

F1r3Drive connects to a F1r3fly node's gRPC API. You can either run a local shard or connect to a remote one.

#### Local Shard with Docker (Recommended)

**Requirement:** [Docker and Docker Compose](https://www.docker.com/).

Two Docker Compose configurations are provided in the `local-shard/` directory:

| Config | Nodes | Use case |
|--------|-------|----------|
| `shard-with-autopropose.yml` | 1 bootstrap + 3 validators + 1 readonly observer + autopropose service | Full multi-validator shard for realistic testing |
| `singleton.yml` | 1 bootstrap + 1 readonly observer | Minimal single-node setup for quick development |

```bash
cd local-shard
docker-compose -f shard-with-autopropose.yml up -d
```

> **Note**: Make sure you have a `.env` file in the `local-shard/` directory with the required environment variables before running docker-compose. See `local-shard/README.md` for wallet information and genesis configuration.

Wait for the "Listening for traffic" log message before connecting F1r3Drive:

```bash
docker-compose -f shard-with-autopropose.yml logs -f
```

**Default port mappings (multi-validator shard):**

| Node | Internal gRPC (deploy) | Observer gRPC |
|------|----------------------|---------------|
| Bootstrap | `localhost:40402` | `localhost:40403` |
| Validator 1 | `localhost:40412` | `localhost:40413` |
| Readonly Observer | `localhost:40442` | `localhost:40443` |

#### Connect to a Remote Shard

If you have access to a remote shard, use the `--host`, `--port`, `--observer-host`, and `--observer-port` flags to point F1r3Drive at it.

### 2. Running F1r3Drive

#### Option A: Use the Pre-built JAR

Download the latest `f1r3drive-*.jar` from the [GitHub Releases page](https://github.com/f1r3fly-io/F1R3FLYFS/releases). Requires **Java 17**.

#### Option B: Build from Source

**Requirements:** [Nix](https://nixos.org/download/), [direnv](https://direnv.net/#basic-installation), [Protobuf Compiler](https://grpc.io/docs/protoc-installation/)

```bash
# 1. Clone and enter the repository
git clone https://github.com/f1r3fly-io/F1R3FLYFS.git && cd F1R3FLYFS

# 2. Set up the development environment via Nix
direnv allow

# 3. Build the fat JAR
./gradlew shadowJar -x test
```

The resulting JAR will be at `build/libs/f1r3drive-<version>.jar`.

## Usage

```bash
java -jar f1r3drive-<version>.jar <mount-point> \
  --key-file <path-to-key> \
  [--manual-propose] \
  [--host <host>] [--port <port>] \
  [--observer-host <host>] [--observer-port <port>] \
  [--address <rev-address> --private-key <key>] \
  [--debug]
```

**Quick start example** (connecting to the local multi-validator shard):

> ⚠️ The credentials below are **test-only** keys from `local-shard/README.md`. Do not use them on public shards.

```bash
java -jar build/libs/f1r3drive-0.1.1.jar ~/demo-f1r3drive \
  --key-file ~/cipher.key \
  --host localhost --port 40402 \
  --observer-host localhost --observer-port 40403 \
  --address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
  --private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9
```

For the full CLI reference, see **[docs/configuration.md](docs/configuration.md)**.
For a step-by-step demo walkthrough, see **[Demo.md](Demo.md)**.

## Running Tests

```bash
# Unit tests
./gradlew test

# End-to-end tests (requires a running shard)
./gradlew e2eTest --rerun-tasks
```

## License

[MIT](LICENSE)
