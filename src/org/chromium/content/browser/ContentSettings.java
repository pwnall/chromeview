// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

/**
 * Manages settings state for a ContentView. A ContentSettings instance is obtained
 * from ContentViewCore.getContentSettings().
 */
@JNINamespace("content")
public class ContentSettings {

    private static final String TAG = "ContentSettings";

    // The native side of this object. Ownership is retained native-side by the WebContents
    // instance that backs the associated ContentViewCore.
    private int mNativeContentSettings = 0;

    private ContentViewCore mContentViewCore;

    /**
     * Package constructor to prevent clients from creating a new settings
     * instance. Must be called on the UI thread.
     */
    ContentSettings(ContentViewCore contentViewCore, int nativeContentView) {
        ThreadUtils.assertOnUiThread();
        mContentViewCore = contentViewCore;
        mNativeContentSettings = nativeInit(nativeContentView);
        assert mNativeContentSettings != 0;
    }

    /**
     * Notification from the native side that it is being destroyed.
     * @param nativeContentSettings the native instance that is going away.
     */
    @CalledByNative
    private void onNativeContentSettingsDestroyed(int nativeContentSettings) {
        assert mNativeContentSettings == nativeContentSettings;
        mNativeContentSettings = 0;
    }

    /**
     * Return true if JavaScript is enabled. Must be called on the UI thread.
     *
     * @return True if JavaScript is enabled.
     */
    public boolean getJavaScriptEnabled() {
        ThreadUtils.assertOnUiThread();
        return mNativeContentSettings != 0 ?
                nativeGetJavaScriptEnabled(mNativeContentSettings) : false;
    }

    // Initialize the ContentSettings native side.
    private native int nativeInit(int contentViewPtr);

    private native boolean nativeGetJavaScriptEnabled(int nativeContentSettings);
}
