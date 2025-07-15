# F1R3FLYFS Blockchain Filesystem (Gemini Guide)

This guide provides a Gemini-specific overview of the F1R3FLYFS project, its structure, and development conventions.

## 1. Project Overview

- **Core Functionality**: A FUSE client for a blockchain-based filesystem implementation in Java.
- **Primary Integration**: Leverages a decentralized storage solution that mounts as a standard filesystem using FUSE, allowing standard file operations to interact with F1r3fly blockchain nodes.
- **Key Technology**: Uses JNR-FFI for native system calls, gRPC for blockchain communication, and Protocol Buffers for data serialization.
- **Dependencies**: This project integrates with F1r3fly blockchain infrastructure and requires proper blockchain node connectivity for operation.

## 2. Project Structure

The codebase is organized into core application modules (`src/main/java/`), tests (`src/test/java/`), integration tests (`src/e2e/java/`), and configuration.

### `src/main/java/` - Core Implementation

-   **`app/`**: Application entry points and CLI interface.
    -   `Main.java`: Primary application entry point.
    -   `cli/`: Command-line interface using PicoCLI.
-   **`filesystem/`**: FUSE filesystem implementation.
    -   Core filesystem operations (read, write, mkdir, etc.).
    -   JNR-FFI integration for native system calls.
    -   Cross-platform FUSE library handling.
-   **`blockchain/`**: F1r3fly blockchain integration.
    -   gRPC client for blockchain node communication.
    -   Protocol Buffer message handling.
    -   Rholang smart contract interaction.
-   **`encryption/`**: Cryptographic operations.
    -   AES encryption for file data.
    -   secp256k1, Blake2b, Keccak-256 implementations.
-   **`background/`**: Event processing and state management.
    -   Concurrent queue systems for filesystem events.
    -   Thread-safe operation handling.

### `src/test/java/` - Unit Tests

-   JUnit 5 test suites for all core modules.
-   Mockito-based mocking for external dependencies.
-   TestContainers for blockchain integration testing.

### `src/e2e/java/` - Integration Tests

-   End-to-end filesystem operation testing.
-   Cross-platform compatibility validation.
-   Blockchain connectivity and data persistence tests.

### Configuration & Build

-   `build.gradle`: Gradle build configuration with dependencies.
-   `src/main/proto/`: Protocol Buffer definitions for blockchain communication.
-   `docker-compose.yml`: Local blockchain shard for development.
-   `flake.nix`: Nix development environment with GraalVM.

## 3. Development Workflow

-   **Setup**: Use Nix flake (`nix develop`) or ensure Java 17+ and Gradle are installed
-   **Development Environment**:
    -   Local blockchain: `docker-compose up` (starts local F1r3fly shard)
    -   Filesystem: `./gradlew run` (starts the filesystem)
-   **Build**: `./gradlew build` (compilation, tests, and fat JAR creation)
-   **Code Generation**: `./gradlew generateProto` (regenerate Protocol Buffer classes)
-   **Testing**: **`./gradlew test build`** - Run this command to test changes across unit and integration tests before completing a task.
-   **Distribution**: `./gradlew shadowJar` (creates executable JAR in `build/libs/`)

## 4. Code Style & Conventions

-   **Language**: Java 17+ with modern language features and best practices.
-   **Imports**: Organize: Java standard library, third-party libraries, then local packages.
-   **Classes**: Use clear separation of concerns with proper encapsulation. Name classes with `PascalCase`.
-   **Packages**: Follow existing hierarchy (`app`, `filesystem`, `blockchain`, `encryption`, `background`).
-   **Error Handling**: Use SLF4J logging with MDC context; avoid `System.out`/`System.err` in production code.
-   **Concurrency**: Follow thread-safety patterns for filesystem operations and blockchain communication.
-   **Testing**:
    -   Use JUnit 5 and Mockito for unit tests.
    -   Focus on behavior over implementation.
    -   Use TestContainers for integration tests requiring blockchain connectivity.
    -   Mock external dependencies (blockchain nodes, native filesystem calls).

## 5. Git & Commits

-   **No Direct Git Actions**: Do not use `git add`, `git rm`, or `git commit`. The user will review and manage all git changes manually.
-   `git mv` is permitted but requires user confirmation.

## 6. Common Tasks

-   Review `git history` to determine how code base evolved or history for particular files and functions.

## 7. Key Technologies & Dependencies

-   **JNR (Java Native Runtime)**: FFI, POSIX, and constants for native system integration
-   **gRPC & Protocol Buffers**: Blockchain node communication and data serialization
-   **Bouncy Castle**: Cryptographic operations and security
-   **Mutiny**: Reactive programming patterns
-   **Logback + SLF4J**: Structured logging with MDC context
-   **Gradle Shadow Plugin**: Fat JAR creation for distribution
-   **TestContainers**: Docker-based integration testing

## 8. Project Specifics

-   F1R3FLYFS provides decentralized storage through blockchain integration with F1r3fly nodes.
-   Cryptographic security uses AES encryption, secp256k1 signatures, Blake2b, and Keccak-256 hashing.
-   The filesystem supports standard POSIX operations (read, write, mkdir, etc.) through FUSE.
-   State management uses concurrent queues and thread-safe operations for filesystem events.
-   DO NOT change core filesystem behavior without understanding FUSE semantics and blockchain implications.
-   Observe the Gradle build configuration and dependency management.
-   Use existing logging patterns with MDC context for operation tracking.
-   The project uses Protocol Buffers for blockchain communication - regenerate proto files when updating schemas.
-   Nix flake provides reproducible development environment with GraalVM.
-   Fat JAR deployment enables single-file distribution with embedded dependencies.
