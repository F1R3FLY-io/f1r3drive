# F1r3fly Model Protobuf Files

This directory contains the protocol projections F1r3drive needs to interoperate
with F1r3node. They intentionally expose only the messages and fields used by
the filesystem client; protobuf's unknown-field rules preserve forward
compatibility with additional node fields.

## Origin
The authoritative source is the sibling
`../f1r3node-rust/models/src/main/protobuf` directory. In particular,
`DeployDataProto` follows the cost-accounted protocol: tags 7 and 8
(`phloPrice` and `phloLimit`) are reserved and must never be signed or sent.

## Contents
- **RhoTypes.proto**: Core Rholang type definitions
- **CasperMessage.proto**: Casper blockchain message structures
- **DeployService*.proto**: Deployment service definitions
- **ProposeService*.proto**: Proposal service definitions
- **ServiceError.proto**: Common service error definitions
- **routing.proto**: Network routing definitions

These files define the core data structures and service interfaces used throughout the F1r3fly ecosystem.
