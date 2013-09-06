// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
/**
 * This class controls how C++ browser main loop is run.
 */
@JNINamespace("content")
public class BrowserStartupConfig {
    public interface StartupCallback {
        void run(int startupResult);
    }

    private static boolean sBrowserMayStartAsynchronously = false;
    private static StartupCallback sBrowserStartupCompleteCallback = null;

    @CalledByNative
    private static boolean browserMayStartAsynchonously() {
        return sBrowserMayStartAsynchronously;
    }

    @CalledByNative
    private static void browserStartupComplete(int result) {
        if(sBrowserStartupCompleteCallback != null) {
            sBrowserStartupCompleteCallback.run(result);
        }
    }

    /**
     * Set browser to start asynchronously. May only be called before contentMain.start(). If it
     * has been called then contentMain.start() will queue up a series of UI tasks to complete
     * browser initialization.
     * @param browserStartupCompleteCallback If not null called when browser startup is complete.
     */
    public static void setAsync(StartupCallback browserStartupCompleteCallback) {
        sBrowserMayStartAsynchronously = true;
        sBrowserStartupCompleteCallback = browserStartupCompleteCallback;
    }
}
