#include <jni.h>
#include <CoreServices/CoreServices.h>
#include <CoreFoundation/CoreFoundation.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

// JNI method signatures
#define FSEVENTS_MONITOR_CLASS "io/f1r3fly/f1r3drive/platform/macos/FSEventsMonitor"

// Global references for callback
static JavaVM *g_jvm = NULL;
static jobject g_monitor_instance = NULL;
static jmethodID g_callback_method = NULL;

// Structure to hold stream information
typedef struct {
    FSEventStreamRef stream;
    CFRunLoopRef runloop;
    pthread_t thread;
    int should_stop;
} FSEventsContext;

// Callback function for FSEvents
static void fsevents_callback(
    ConstFSEventStreamRef streamRef,
    void *clientCallBackInfo,
    size_t numEvents,
    void *eventPaths,
    const FSEventStreamEventFlags eventFlags[],
    const FSEventStreamEventId eventIds[]) {
    
    if (g_jvm == NULL || g_monitor_instance == NULL || g_callback_method == NULL) {
        return;
    }
    
    JNIEnv *env = NULL;
    int attach_result = (*g_jvm)->AttachCurrentThread(g_jvm, (void**)&env, NULL);
    if (attach_result != JNI_OK || env == NULL) {
        return;
    }
    
    // Create arrays for the callback
    jobjectArray pathArray = (*env)->NewObjectArray(env, numEvents, 
        (*env)->FindClass(env, "java/lang/String"), NULL);
    jintArray flagsArray = (*env)->NewIntArray(env, numEvents);
    jlongArray eventIdArray = (*env)->NewLongArray(env, numEvents);
    
    if (pathArray == NULL || flagsArray == NULL || eventIdArray == NULL) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
        return;
    }
    
    // Fill the arrays
    char **paths = (char **)eventPaths;
    jint *flags = (jint *)malloc(numEvents * sizeof(jint));
    jlong *eventIdValues = (jlong *)malloc(numEvents * sizeof(jlong));
    
    for (size_t i = 0; i < numEvents; i++) {
        // Set path
        jstring pathStr = (*env)->NewStringUTF(env, paths[i]);
        (*env)->SetObjectArrayElement(env, pathArray, i, pathStr);
        (*env)->DeleteLocalRef(env, pathStr);
        
        // Set flags and event IDs
        flags[i] = (jint)eventFlags[i];
        eventIdValues[i] = (jlong)eventIds[i];
    }
    
    (*env)->SetIntArrayRegion(env, flagsArray, 0, numEvents, flags);
    (*env)->SetLongArrayRegion(env, eventIdArray, 0, numEvents, eventIdValues);
    
    // Call the Java callback method
    (*env)->CallVoidMethod(env, g_monitor_instance, g_callback_method, 
                          pathArray, flagsArray, eventIdArray);
    
    // Cleanup
    (*env)->DeleteLocalRef(env, pathArray);
    (*env)->DeleteLocalRef(env, flagsArray);
    (*env)->DeleteLocalRef(env, eventIdArray);
    free(flags);
    free(eventIdValues);
    
    (*g_jvm)->DetachCurrentThread(g_jvm);
}

// JNI method implementations
JNIEXPORT jlong JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeCreateStream(
    JNIEnv *env, jobject obj, jstring path, jdouble latency, jint flags) {
    
    // Store JVM reference and monitor instance
    if (g_jvm == NULL) {
        (*env)->GetJavaVM(env, &g_jvm);
    }
    
    if (g_monitor_instance == NULL) {
        g_monitor_instance = (*env)->NewGlobalRef(env, obj);
        
        // Get the callback method ID
        jclass monitorClass = (*env)->GetObjectClass(env, obj);
        g_callback_method = (*env)->GetMethodID(env, monitorClass, 
            "onFileSystemEvent", "([Ljava/lang/String;[I[J)V");
        (*env)->DeleteLocalRef(env, monitorClass);
    }
    
    const char *pathStr = (*env)->GetStringUTFChars(env, path, NULL);
    if (pathStr == NULL) {
        return 0;
    }
    
    // Create path array for FSEvents
    CFStringRef cfPath = CFStringCreateWithCString(NULL, pathStr, kCFStringEncodingUTF8);
    CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void **)&cfPath, 1, &kCFTypeArrayCallBacks);
    
    // Create FSEventStream
    FSEventStreamContext context = {0, NULL, NULL, NULL, NULL};
    FSEventStreamRef stream = FSEventStreamCreate(
        NULL,                           // allocator
        &fsevents_callback,            // callback
        &context,                      // context
        pathsToWatch,                  // pathsToWatch
        kFSEventStreamEventIdSinceNow, // sinceWhen
        latency,                       // latency
        (FSEventStreamCreateFlags)flags // flags
    );
    
    // Create context structure
    FSEventsContext *fsContext = (FSEventsContext *)malloc(sizeof(FSEventsContext));
    fsContext->stream = stream;
    fsContext->runloop = NULL;
    fsContext->should_stop = 0;
    
    // Cleanup
    CFRelease(pathsToWatch);
    CFRelease(cfPath);
    (*env)->ReleaseStringUTFChars(env, path, pathStr);
    
    return (jlong)fsContext;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeScheduleStream(
    JNIEnv *env, jobject obj, jlong streamRef) {
    
    if (streamRef == 0) {
        return JNI_FALSE;
    }
    
    FSEventsContext *context = (FSEventsContext *)streamRef;
    if (context->stream == NULL) {
        return JNI_FALSE;
    }
    
    // Schedule on current run loop
    context->runloop = CFRunLoopGetCurrent();
    FSEventStreamScheduleWithRunLoop(context->stream, context->runloop, kCFRunLoopDefaultMode);
    
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeStartStream(
    JNIEnv *env, jobject obj, jlong streamRef) {
    
    if (streamRef == 0) {
        return JNI_FALSE;
    }
    
    FSEventsContext *context = (FSEventsContext *)streamRef;
    if (context->stream == NULL) {
        return JNI_FALSE;
    }
    
    Boolean result = FSEventStreamStart(context->stream);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeStopStream(
    JNIEnv *env, jobject obj, jlong streamRef) {
    
    if (streamRef == 0) {
        return;
    }
    
    FSEventsContext *context = (FSEventsContext *)streamRef;
    if (context->stream == NULL) {
        return;
    }
    
    context->should_stop = 1;
    FSEventStreamStop(context->stream);
    
    // Stop the run loop
    if (context->runloop != NULL) {
        CFRunLoopStop(context->runloop);
    }
}

JNIEXPORT void JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeRunLoop(
    JNIEnv *env, jobject obj, jlong streamRef) {
    
    if (streamRef == 0) {
        return;
    }
    
    FSEventsContext *context = (FSEventsContext *)streamRef;
    if (context->stream == NULL || context->runloop == NULL) {
        return;
    }
    
    // Run the CFRunLoop until stopped
    while (!context->should_stop) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.1, true);
    }
}

JNIEXPORT void JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeCleanup(
    JNIEnv *env, jobject obj, jlong streamRef) {
    
    if (streamRef == 0) {
        return;
    }
    
    FSEventsContext *context = (FSEventsContext *)streamRef;
    
    if (context->stream != NULL) {
        FSEventStreamUnscheduleFromRunLoop(context->stream, context->runloop, kCFRunLoopDefaultMode);
        FSEventStreamInvalidate(context->stream);
        FSEventStreamRelease(context->stream);
    }
    
    free(context);
}

JNIEXPORT jstring JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeGetVersion(
    JNIEnv *env, jobject obj) {
    
    return (*env)->NewStringUTF(env, "F1r3Drive FSEvents Monitor 1.0.0");
}

JNIEXPORT jboolean JNICALL
Java_io_f1r3fly_f1r3drive_platform_macos_FSEventsMonitor_nativeIsAvailable(
    JNIEnv *env, jobject obj) {
    
    // Check if FSEvents is available (it should be on all supported macOS versions)
    return JNI_TRUE;
}

// JNI_OnLoad - called when the library is loaded
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

// JNI_OnUnload - called when the library is unloaded
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (g_jvm != NULL) {
        JNIEnv *env = NULL;
        if ((*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_8) == JNI_OK) {
            if (g_monitor_instance != NULL) {
                (*env)->DeleteGlobalRef(env, g_monitor_instance);
                g_monitor_instance = NULL;
            }
        }
        g_jvm = NULL;
    }
}