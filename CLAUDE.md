# F1R3FLYFS Project Guidelines

## Project Context
- This is F1R3FLYFS, a FUSE client for a blockchain-based filesystem implementation in Java.
- The project leverages a decentralized storage solution that mounts as a standard filesystem using FUSE.
- Built with Java 17, Gradle, and integrates with F1r3fly blockchain nodes via gRPC.
- Uses JNR-FFI for native system calls and FUSE operations across Windows, macOS, and Linux.
- If the user does not provide enough information with their prompts, ask the user to clarify before executing the task. This should be included in all tasks including writing unit tests, scaffolding the project, as well as implementation of individual modules. In general, follow a test driven development approach whereby unit tests are developed in parallel with the individual components, and features.

## Commands
- Development: `./gradlew run` to start the filesystem
- Build: `./gradlew build` (includes compilation, tests, and fat JAR creation)
- Test: `./gradlew test` (unit tests) or `./gradlew integrationTest` (integration tests)
- Clean: `./gradlew clean`
- Generate Protobuf: `./gradlew generateProto`
- Fat JAR: `./gradlew shadowJar` (creates executable JAR in `build/libs/`)
- DO NOT ever `git add`, `git rm` or `git commit` code. Allow the Claude user to always manually review git changes. `git mv` is permitted and inform the developer.
- DO NOT ever remove tests from gradle configurations or test coverage.
- Run `./gradlew test build` to test code changes before proceeding to a prompt for more instructions or the next task.
- **Operating outside of local repository (with .git/ directory root)**: Not permitted and any file or other operations require user approval and notification

## Code Style
- **Java**: Use Java 17+ features. Follow Oracle Java conventions.
- Organize imports: Java standard library first, third-party libraries next, local imports last.
- **Package Structure**: Follow existing package hierarchy (`app/`, `filesystem/`, `blockchain/`, `encryption/`, `background/`).
- **Class Structure**: Use clear separation of concerns with proper encapsulation.
- **Naming**: PascalCase for classes/interfaces, camelCase for methods/variables, UPPER_SNAKE_CASE for constants.
- **Error Handling**: Use SLF4J logging with MDC context, avoid System.out/err in production.
- **Documentation**: Use JavaDoc for public APIs, inline comments for complex logic.
- Follow existing patterns for concurrent programming and thread safety.
- Follow existing error handling patterns with proper exception propagation.
- When adding source code or new files, enhance, update, and provide new unit tests using the existing JUnit 5 patterns.
- If unused variables are required, use `@SuppressWarnings("unused")` annotations appropriately.

## Best Practices
- Use SLF4J logging with appropriate levels (debug, info, warn, error)
- Follow Java 17+ best practices and modern concurrent programming patterns
- Use JNR-FFI patterns for native system interaction
- Implement proper error handling for filesystem operations
- Maintain thread safety for concurrent file operations
- Test filesystem operations on multiple platforms (Windows, macOS, Linux)
- Use dependency injection and modular design patterns
- Follow defensive programming practices for blockchain integration

## Testing Best Practices
- Use JUnit 5 for unit tests with proper test lifecycle management
- Prefer these testing approaches:
  - Test filesystem operations with temporary directories
  - Use Mockito for mocking external dependencies (blockchain, native calls)
  - Test concurrent operations with proper synchronization
  - Use TestContainers for integration tests with blockchain nodes
  - Test actual filesystem operations and their effects
  - Mock gRPC calls to verify blockchain communication
  - Test error handling and recovery scenarios
- Always mock external dependencies consistently (blockchain nodes, native filesystem)
- Write tests that focus on behavior over implementation details
- Use CompletableFuture and async testing patterns for reactive operations
- Create test fixtures for complex filesystem scenarios and blockchain states
- Separate unit tests from integration tests (src/test/java vs src/e2e/java)

## Common Tasks
- Review `git history` to determine how code base evolved or history for particular files and functions.

## Project Specifics
- F1R3FLYFS provides decentralized storage through blockchain integration with F1r3fly nodes.
- Cryptographic security uses AES encryption, secp256k1 signatures, Blake2b, and Keccak-256 hashing.
- The filesystem supports standard POSIX operations (read, write, mkdir, etc.) through FUSE.
- State management uses concurrent queues and thread-safe operations for filesystem events.
- DO NOT change core filesystem behavior without understanding FUSE semantics and blockchain implications.
- Observe the Gradle build configuration and dependency management.
- Use existing logging patterns with MDC context for operation tracking.
- The project uses Protocol Buffers for blockchain communication - regenerate proto files when updating schemas.
- Nix flake provides reproducible development environment with GraalVM.
- Fat JAR deployment enables single-file distribution with embedded dependencies.
