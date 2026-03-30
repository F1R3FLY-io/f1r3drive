#!/bin/bash

# F1r3Drive Surgical Launch Script (macOS M4 Pro Max)
# This script maintains the life of the driver.

export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
JAR_FILE="build/libs/f1r3drive-macos-0.1.1.jar"
MOUNT_POINT="$HOME/demo-f1r3drive"
CIPHER_KEY="$HOME/cipher.key"
RUST_COMPOSE="/Users/jedoan/projects/f1r3fly.io/system-integration/compose/f1r3node-rust.yml"

# Ensure docker nodes are running
echo "🐳 CHECKING NODES: Ensuring f1r3node-rust is up..."
if ! docker ps | grep -q "rnode.validator1"; then
    echo "🐳 STARTING: Validator1 and Validator2..."
    docker compose -f "$RUST_COMPOSE" up -d validator1 validator2 readonly
    sleep 5
fi

# Ensure mount point exists and is clean
mkdir -p "$MOUNT_POINT"
# Pre-start cleanup: remove any stale placeholders and local app cache
rm -rf "$MOUNT_POINT"/*
rm -rf "$HOME/.f1r3drive/cache"/*

echo "👁️🛡️🛰️ DEEP CLEAN: Local cache and mount point sterilized."
echo "👁️🛡️🛰️ IGNITION: Starting F1r3Drive on M4..."

# Run the driver with manual-propose=false
$JAVA_HOME/bin/java -jar "$JAR_FILE" "$MOUNT_POINT" \
    --cipher-key-path "$CIPHER_KEY" \
    --validator-host localhost --validator-port 40412 \
    --observer-host localhost --observer-port 40452 \
    --manual-propose=false \
    --rev-address 111127RX5ZgiAdRaQy4AWy57RdvAAckdELReEBxzvWYVvdnR32PiHA \
    --private-key 357cdc4201a5650830e0bc5a03299a30038d9934ba4c7ab73ec164ad82471ff9 2>&1
