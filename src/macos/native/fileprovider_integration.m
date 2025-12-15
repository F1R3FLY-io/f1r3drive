#include <jni.h>
#include <Foundation/Foundation.h>
#include <CoreFoundation/CoreFoundation.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <dispatch/dispatch.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <unistd.h>

// JNI method signatures
#define FILEPROVIDER_CLASS "io/f1r3fly/f1r3drive/platform/macos/FileProviderIntegration"

// Global references for callbacks
static JavaVM *g_jvm = NULL;
static jobject g_integration_instance = NULL;
static jmethodID g_materialization_callback = NULL;
static jmethodID g_eviction_callback = NULL;

// Structure to hold simplified File Provider information
typedef struct {
    NSString *domainIdentifier;
    NSString *displayName;
    NSString *rootPath;
    NSMutableDictionary *placeholderFiles;
    dispatch_queue_t queue;
    int isActive;
} FileProviderContext;

// Helper function to convert NSString to jstring
static jstring NSStringToJString(JNIEnv *env, NSString *nsString) {
    if (nsString == nil) {
        return NULL;
    }
    const char *cString = [nsString UTF8String];
    return (*env)->NewStringUTF(env, cString);
}

// Helper function to convert jstring to NSString
static NSString *JStringToNSString(JNIEnv *env, jstring jStr) {
    if (jStr == NULL) {
        return nil;
    }
    const char *cString = (*env)->GetStringUTFChars(env, jStr, NULL);
    NSString *nsString = [NSString stringWithUTF8String:cString];
    (*env)->ReleaseStringUTFChars(env, jStr, cString);
    return nsString;
}

// Create extended attributes to mark files as placeholders
static BOOL markAsPlaceholder(const char *path, BOOL isPlaceholder) {
    const char *attrName = "user.f1r3drive.placeholder";
    const char *attrValue = isPlaceholder ? "true" : "false";
    
    int result = setxattr(path, attrName, attrValue, strlen(attrValue), 0, 0);
    return result == 0;
}

// Check if file is marked as placeholder
static BOOL isPlaceholder(const char *path) {
    const char *attrName = "user.f1r3drive.placeholder";
    char buffer[16];
    
    ssize_t result = getxattr(path, attrName, buffer, sizeof(buffer), 0, 0);
    if (result > 0) {
        buffer[result] = '\0';
        return strcmp(buffer, "true") == 0;
    }
    return NO;
}

// JNI method implementations

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_isNativeLibraryAvailable(
    JNIEnv *env, jobject obj) {
    
    // Always available for our simplified implementation
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeCreateProvider(
    JNIEnv *env, jobject obj, jstring domainId, jstring displayName, jstring rootPath) {
    
    // Store JVM reference and integration instance
    if (g_jvm == NULL) {
        (*env)->GetJavaVM(env, &g_jvm);
    }
    
    if (g_integration_instance == NULL) {
        g_integration_instance = (*env)->NewGlobalRef(env, obj);
        
        // Get callback method IDs
        jclass integrationClass = (*env)->GetObjectClass(env, obj);
        g_materialization_callback = (*env)->GetMethodID(env, integrationClass, 
            "onMaterializationRequest", "(Ljava/lang/String;)Z");
        g_eviction_callback = (*env)->GetMethodID(env, integrationClass, 
            "onFileEvicted", "(Ljava/lang/String;)V");
        (*env)->DeleteLocalRef(env, integrationClass);
    }
    
    // Convert Java strings to NSString
    NSString *nsDomainId = JStringToNSString(env, domainId);
    NSString *nsDisplayName = JStringToNSString(env, displayName);
    NSString *nsRootPath = JStringToNSString(env, rootPath);
    
    // Create File Provider context
    FileProviderContext *context = (FileProviderContext *)malloc(sizeof(FileProviderContext));
    context->domainIdentifier = nsDomainId;
    context->displayName = nsDisplayName;
    context->rootPath = nsRootPath;
    context->placeholderFiles = [[NSMutableDictionary alloc] init];
    context->queue = dispatch_queue_create("com.f1r3drive.fileprovider", DISPATCH_QUEUE_SERIAL);
    context->isActive = 0;
    
    // Create root directory if it doesn't exist
    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL isDir;
    if (![fileManager fileExistsAtPath:nsRootPath isDirectory:&isDir]) {
        NSError *error = nil;
        [fileManager createDirectoryAtPath:nsRootPath 
               withIntermediateDirectories:YES 
                                attributes:nil 
                                     error:&error];
        if (error) {
            NSLog(@"F1r3Drive: Failed to create root directory: %@", [error localizedDescription]);
        }
    }
    
    NSLog(@"F1r3Drive: Created simplified File Provider with domain: %@", nsDomainId);
    
    return (jlong)context;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeRegisterDomain(
    JNIEnv *env, jobject obj, jlong contextRef) {
    
    if (contextRef == 0) {
        return JNI_FALSE;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    context->isActive = 1;
    
    NSLog(@"F1r3Drive: Registered simplified File Provider domain: %@", context->domainIdentifier);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeUnregisterDomain(
    JNIEnv *env, jobject obj, jlong contextRef) {
    
    if (contextRef == 0) {
        return;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    context->isActive = 0;
    
    NSLog(@"F1r3Drive: Unregistered simplified File Provider domain: %@", context->domainIdentifier);
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeCreatePlaceholder(
    JNIEnv *env, jobject obj, jlong contextRef, jstring path, jlong size, 
    jlong lastModified, jint itemType, jint materializationPolicy) {
    
    if (contextRef == 0) {
        return JNI_FALSE;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    NSString *nsPath = JStringToNSString(env, path);
    
    // Create placeholder file with minimal content
    NSData *placeholderData = [@"F1r3Drive placeholder file - content will be loaded on access" 
                              dataUsingEncoding:NSUTF8StringEncoding];
    
    // Create file attributes
    NSMutableDictionary *attributes = [NSMutableDictionary dictionary];
    
    NSDate *modDate = [NSDate dateWithTimeIntervalSince1970:(lastModified / 1000.0)];
    [attributes setObject:modDate forKey:NSFileModificationDate];
    
    // Create parent directory if needed
    NSString *parentDir = [nsPath stringByDeletingLastPathComponent];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL isDir;
    if (![fileManager fileExistsAtPath:parentDir isDirectory:&isDir]) {
        NSError *error = nil;
        [fileManager createDirectoryAtPath:parentDir 
               withIntermediateDirectories:YES 
                                attributes:nil 
                                     error:&error];
        if (error) {
            NSLog(@"F1r3Drive: Failed to create parent directory: %@", [error localizedDescription]);
            return JNI_FALSE;
        }
    }
    
    // Create the placeholder file
    BOOL success = [placeholderData writeToFile:nsPath atomically:YES];
    
    if (success) {
        // Set file attributes
        NSError *error = nil;
        [fileManager setAttributes:attributes ofItemAtPath:nsPath error:&error];
        
        // Mark as placeholder using extended attributes
        markAsPlaceholder([nsPath UTF8String], YES);
        
        // Store placeholder info
        NSMutableDictionary *placeholderInfo = [NSMutableDictionary dictionary];
        [placeholderInfo setObject:@(size) forKey:@"size"];
        [placeholderInfo setObject:@(lastModified) forKey:@"lastModified"];
        [placeholderInfo setObject:@(itemType) forKey:@"itemType"];
        [placeholderInfo setObject:@(materializationPolicy) forKey:@"materializationPolicy"];
        [placeholderInfo setObject:@NO forKey:@"materialized"];
        
        [context->placeholderFiles setObject:placeholderInfo forKey:nsPath];
        
        NSLog(@"F1r3Drive: Created placeholder file: %@ (%lld bytes)", nsPath, size);
    } else {
        NSLog(@"F1r3Drive: Failed to create placeholder file: %@", nsPath);
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeMaterializePlaceholder(
    JNIEnv *env, jobject obj, jlong contextRef, jstring path, jbyteArray content) {
    
    if (contextRef == 0 || content == NULL) {
        return JNI_FALSE;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    NSString *nsPath = JStringToNSString(env, path);
    
    // Get content from Java byte array
    jsize contentLength = (*env)->GetArrayLength(env, content);
    jbyte *contentBytes = (*env)->GetByteArrayElements(env, content, NULL);
    NSData *contentData = [NSData dataWithBytes:contentBytes length:contentLength];
    
    // Write content to file
    BOOL success = [contentData writeToFile:nsPath atomically:YES];
    
    if (success) {
        // Mark as materialized
        markAsPlaceholder([nsPath UTF8String], NO);
        
        // Update placeholder info
        NSMutableDictionary *placeholderInfo = [context->placeholderFiles objectForKey:nsPath];
        if (placeholderInfo) {
            [placeholderInfo setObject:@YES forKey:@"materialized"];
        }
        
        NSLog(@"F1r3Drive: Materialized file: %@ (%ld bytes)", nsPath, (long)contentLength);
    } else {
        NSLog(@"F1r3Drive: Failed to materialize file: %@", nsPath);
    }
    
    // Release byte array
    (*env)->ReleaseByteArrayElements(env, content, contentBytes, JNI_ABORT);
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeUpdatePlaceholder(
    JNIEnv *env, jobject obj, jlong contextRef, jstring path, jlong size, jlong lastModified) {
    
    if (contextRef == 0) {
        return JNI_FALSE;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    NSString *nsPath = JStringToNSString(env, path);
    
    // Update file attributes
    NSMutableDictionary *attributes = [NSMutableDictionary dictionary];
    
    NSDate *modDate = [NSDate dateWithTimeIntervalSince1970:(lastModified / 1000.0)];
    [attributes setObject:modDate forKey:NSFileModificationDate];
    
    NSError *error = nil;
    BOOL success = [[NSFileManager defaultManager] setAttributes:attributes 
                                                    ofItemAtPath:nsPath 
                                                           error:&error];
    
    if (success) {
        // Update placeholder info
        NSMutableDictionary *placeholderInfo = [context->placeholderFiles objectForKey:nsPath];
        if (placeholderInfo) {
            [placeholderInfo setObject:@(size) forKey:@"size"];
            [placeholderInfo setObject:@(lastModified) forKey:@"lastModified"];
        }
        
        NSLog(@"F1r3Drive: Updated placeholder: %@", nsPath);
    } else {
        NSLog(@"F1r3Drive: Failed to update placeholder: %@ - %@", nsPath, [error localizedDescription]);
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeRemovePlaceholder(
    JNIEnv *env, jobject obj, jlong contextRef, jstring path) {
    
    if (contextRef == 0) {
        return JNI_FALSE;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    NSString *nsPath = JStringToNSString(env, path);
    
    NSError *error = nil;
    BOOL success = [[NSFileManager defaultManager] removeItemAtPath:nsPath error:&error];
    
    if (success) {
        // Remove from placeholder tracking
        [context->placeholderFiles removeObjectForKey:nsPath];
        
        NSLog(@"F1r3Drive: Removed placeholder: %@", nsPath);
    } else {
        NSLog(@"F1r3Drive: Failed to remove placeholder: %@ - %@", nsPath, [error localizedDescription]);
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeCleanup(
    JNIEnv *env, jobject obj, jlong contextRef) {
    
    if (contextRef == 0) {
        return;
    }
    
    FileProviderContext *context = (FileProviderContext *)contextRef;
    
    // Release objects (ARC will handle this automatically)
    context->domainIdentifier = nil;
    context->displayName = nil;
    context->rootPath = nil;
    context->placeholderFiles = nil;
    
    // Release dispatch queue
    if (context->queue) {
        // dispatch queues are reference counted in ARC
        context->queue = nil;
    }
    
    free(context);
    NSLog(@"F1r3Drive: Simplified File Provider context cleaned up");
}

JNIEXPORT jstring JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeGetVersion(
    JNIEnv *env, jobject obj) {
    
    return (*env)->NewStringUTF(env, "F1r3Drive Simplified File Provider Integration 1.0.0");
}

// Additional helper method to trigger materialization on file access
JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FileProviderIntegration_nativeCheckAndMaterialize(
    JNIEnv *env, jobject obj, jstring path) {
    
    NSString *nsPath = JStringToNSString(env, path);
    const char *cPath = [nsPath UTF8String];
    
    // Check if file is a placeholder
    if (isPlaceholder(cPath)) {
        NSLog(@"F1r3Drive: File access detected for placeholder: %@", nsPath);
        
        // Call Java materialization callback
        if (g_jvm && g_integration_instance && g_materialization_callback) {
            jboolean result = (*env)->CallBooleanMethod(env, g_integration_instance, 
                                                       g_materialization_callback, path);
            return result;
        }
    }
    
    return JNI_TRUE;
}

// JNI lifecycle methods

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    NSLog(@"F1r3Drive: Simplified File Provider JNI library loaded");
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (g_jvm != NULL) {
        JNIEnv *env = NULL;
        if ((*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_8) == JNI_OK) {
            if (g_integration_instance != NULL) {
                (*env)->DeleteGlobalRef(env, g_integration_instance);
                g_integration_instance = NULL;
            }
        }
        g_jvm = NULL;
    }
    NSLog(@"F1r3Drive: Simplified File Provider JNI library unloaded");
}