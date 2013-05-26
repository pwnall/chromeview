// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.app;

import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.JNINamespace;
import org.chromium.content.common.CommandLine;
import org.chromium.content.common.ProcessInitException;
import org.chromium.content.common.ResultCodes;
import org.chromium.content.common.TraceEvent;

/**
 * This class provides functionality to load and register the native libraries.
 * Callers are allowed to separate loading the libraries from initializing them.
 * This may be an advantage for Android Webview, where the libraries can be loaded
 * by the zygote process, but then needs per process initialization after the
 * application processes are forked from the zygote process.
 *
 * The libraries may be loaded and initialized from any thread. Synchronization
 * primitives are used to ensure that overlapping requests from different
 * threads are handled sequentially.
 *
 * See also content/app/android/library_loader_hooks.cc, which contains
 * the native counterpart to this class.
 */
@JNINamespace("content")
public class LibraryLoader {
    private static final String TAG = "LibraryLoader";

    // Guards all access to the libraries
    private static final Object sLock = new Object();

    // One-way switch becomes true when the libraries are loaded.
    private static boolean sLoaded = false;

    // One-way switch becomes true when the libraries are initialized (
    // by calling nativeLibraryLoaded, which forwards to LibraryLoaded(...) in
    // library_loader_hooks.cc).
    private static boolean sInitialized = false;

    // TODO(cjhopman): Remove this once it's unused.
    /**
     * Doesn't do anything.
     */
    @Deprecated
    public static void setLibraryToLoad(String library) {
    }

    /**
     *  This method blocks until the library is fully loaded and initialized.
     */
    public static void ensureInitialized() throws ProcessInitException {
        synchronized (sLock) {
            if (sInitialized) {
                // Already initialized, nothing to do.
                return;
            }
            loadAlreadyLocked();
            initializeAlreadyLocked(CommandLine.getJavaSwitchesOrNull());
        }
    }


    /**
     * Loads the library and blocks until the load completes. The caller is responsible
     * for subsequently calling ensureInitialized().
     * May be called on any thread, but should only be called once. Note the thread
     * this is called on will be the thread that runs the native code's static initializers.
     * See the comment in doInBackground() for more considerations on this.
     *
     * @throws ProcessInitException if the native library failed to load.
     */
    public static void loadNow() throws ProcessInitException {
        synchronized (sLock) {
            loadAlreadyLocked();
        }
    }


    /**
     * initializes the library here and now: must be called on the thread that the
     * native will call its "main" thread. The library must have previously been
     * loaded with loadNow.
     * @param initCommandLine The command line arguments that native command line will
     * be initialized with.
     */
    static void initialize(String[] initCommandLine) throws ProcessInitException {
        synchronized (sLock) {
            initializeAlreadyLocked(initCommandLine);
        }
    }


    // Invoke System.loadLibrary(...), triggering JNI_OnLoad in native code
    private static void loadAlreadyLocked() throws ProcessInitException {
        try {
            if (!sLoaded) {
                assert !sInitialized;
                for (String sLibrary : NativeLibraries.libraries) {
                    Log.i(TAG, "loading: " + sLibrary);
                    System.loadLibrary(sLibrary);
                    Log.i(TAG, "loaded: " + sLibrary);
                }
                sLoaded = true;
            }
        } catch (UnsatisfiedLinkError e) {
            throw new ProcessInitException(ResultCodes.RESULT_CODE_NATIVE_LIBRARY_LOAD_FAILED, e);
        }
    }


    // Invoke content::LibraryLoaded in library_loader_hooks.cc
    private static void initializeAlreadyLocked(String[] initCommandLine)
            throws ProcessInitException {
        if (sInitialized) {
            return;
        }
        int resultCode = nativeLibraryLoaded(initCommandLine);
        if (resultCode != 0) {
            Log.e(TAG, "error calling nativeLibraryLoaded");
            throw new ProcessInitException(resultCode);
        }
        // From this point on, native code is ready to use and checkIsReady()
        // shouldn't complain from now on (and in fact, it's used by the
        // following calls).
        sInitialized = true;
        CommandLine.enableNativeProxy();
        TraceEvent.setEnabledToMatchNative();
    }

    // This is the only method that is registered during System.loadLibrary. We then call it
    // to register everything else. This process is called "initialization".
    // This method will be mapped (by generated code) to the LibraryLoaded
    // definition in content/app/android/library_loader_hooks.cc.
    //
    // Return 0 on success, otherwise return the error code from
    // content/public/common/result_codes.h.
    private static native int nativeLibraryLoaded(String[] initCommandLine);
}
