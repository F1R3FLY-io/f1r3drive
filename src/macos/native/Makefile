# Makefile for F1r3Drive FSEvents native library

# Configuration
FSEVENTS_TARGET = libf1r3drive-fsevents.dylib
FSEVENTS_SOURCES = fsevents_monitor.c
FSEVENTS_OBJECTS = $(FSEVENTS_SOURCES:.c=.o)

FILEPROVIDER_TARGET = libf1r3drive-fileprovider.dylib
FILEPROVIDER_SOURCES = fileprovider_integration.m
FILEPROVIDER_OBJECTS = $(FILEPROVIDER_SOURCES:.m=.o)

TARGETS = $(FSEVENTS_TARGET) $(FILEPROVIDER_TARGET)
ALL_SOURCES = $(FSEVENTS_SOURCES) $(FILEPROVIDER_SOURCES)
ALL_OBJECTS = $(FSEVENTS_OBJECTS) $(FILEPROVIDER_OBJECTS)

# Java configuration
JAVA_HOME ?= $(shell /usr/libexec/java_home)
JNI_INCLUDES = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/darwin

# Compiler configuration
CC = clang
CFLAGS = -Wall -Wextra -O2 -fPIC $(JNI_INCLUDES)
OBJC_FLAGS = -Wall -Wextra -O2 -fPIC $(JNI_INCLUDES) -fobjc-arc
FSEVENTS_LDFLAGS = -dynamiclib -framework CoreServices -framework CoreFoundation
FILEPROVIDER_LDFLAGS = -dynamiclib -framework FileProvider -framework Foundation -framework CoreFoundation

# Build directory
BUILD_DIR = ../../../build/native/macos
INSTALL_DIR = ../../../build/resources/main

# Default target
all: $(BUILD_DIR)/$(FSEVENTS_TARGET) $(BUILD_DIR)/$(FILEPROVIDER_TARGET)

# Create build directory
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Build the FSEvents dynamic library
$(BUILD_DIR)/$(FSEVENTS_TARGET): $(FSEVENTS_OBJECTS) | $(BUILD_DIR)
	$(CC) $(FSEVENTS_LDFLAGS) -o $@ $(FSEVENTS_OBJECTS)
	@echo "Built $(FSEVENTS_TARGET) successfully"

# Build the File Provider dynamic library
$(BUILD_DIR)/$(FILEPROVIDER_TARGET): $(FILEPROVIDER_OBJECTS) | $(BUILD_DIR)
	$(CC) $(FILEPROVIDER_LDFLAGS) -o $@ $(FILEPROVIDER_OBJECTS)
	@echo "Built $(FILEPROVIDER_TARGET) successfully"

# Compile C source files
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@
	@echo "Compiled $<"

# Compile Objective-C source files
%.o: %.m
	$(CC) $(OBJC_FLAGS) -c $< -o $@
	@echo "Compiled $<"

# Install to resources directory (for JAR packaging)
install: $(BUILD_DIR)/$(FSEVENTS_TARGET) $(BUILD_DIR)/$(FILEPROVIDER_TARGET)
	mkdir -p $(INSTALL_DIR)
	cp $(BUILD_DIR)/$(FSEVENTS_TARGET) $(INSTALL_DIR)/
	cp $(BUILD_DIR)/$(FILEPROVIDER_TARGET) $(INSTALL_DIR)/
	@echo "Installed both libraries to $(INSTALL_DIR)"

# Clean build artifacts
clean:
	rm -f $(ALL_OBJECTS)
	rm -f $(BUILD_DIR)/$(FSEVENTS_TARGET)
	rm -f $(BUILD_DIR)/$(FILEPROVIDER_TARGET)
	@echo "Cleaned build artifacts"

# Clean everything including build directory
distclean: clean
	rm -rf $(BUILD_DIR)
	@echo "Cleaned everything"

# Development targets
debug: CFLAGS += -g -DDEBUG
debug: $(BUILD_DIR)/$(FSEVENTS_TARGET) $(BUILD_DIR)/$(FILEPROVIDER_TARGET)

# Check if Java is properly configured
check-java:
	@echo "JAVA_HOME: $(JAVA_HOME)"
	@echo "JNI includes: $(JNI_INCLUDES)"
	@if [ ! -d "$(JAVA_HOME)/include" ]; then \
		echo "ERROR: JNI headers not found. Make sure JAVA_HOME is set correctly."; \
		exit 1; \
	fi
	@echo "Java configuration OK"

# Test the library
test: $(BUILD_DIR)/$(FSEVENTS_TARGET) $(BUILD_DIR)/$(FILEPROVIDER_TARGET)
	@echo "Testing libraries..."
	@for lib in $(FSEVENTS_TARGET) $(FILEPROVIDER_TARGET); do \
		if [ -f "$(BUILD_DIR)/$$lib" ]; then \
			echo "Library exists: $(BUILD_DIR)/$$lib"; \
			file $(BUILD_DIR)/$$lib; \
			otool -L $(BUILD_DIR)/$$lib; \
		else \
			echo "ERROR: Library $$lib not found"; \
			exit 1; \
		fi; \
	done

# Show library symbols
symbols: $(BUILD_DIR)/$(FSEVENTS_TARGET) $(BUILD_DIR)/$(FILEPROVIDER_TARGET)
	@echo "FSEvents library symbols:"
	nm -D $(BUILD_DIR)/$(FSEVENTS_TARGET) | grep Java
	@echo "File Provider library symbols:"
	nm -D $(BUILD_DIR)/$(FILEPROVIDER_TARGET) | grep Java

# Help target
help:
	@echo "Available targets:"
	@echo "  all         - Build both native libraries (default)"
	@echo "  install     - Install libraries to resources directory"
	@echo "  clean       - Remove build artifacts"
	@echo "  distclean   - Remove all build files"
	@echo "  debug       - Build with debug symbols"
	@echo "  check-java  - Verify Java/JNI configuration"
	@echo "  test        - Test the built libraries"
	@echo "  symbols     - Show exported JNI symbols"
	@echo "  help        - Show this help message"

.PHONY: all install clean distclean debug check-java test symbols help
