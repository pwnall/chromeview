// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("android_webview")
public class AwHttpAuthHandler {

    private int mNativeAwHttpAuthHandler;
    private final boolean mFirstAttempt;

    public void proceed(String username, String password) {
        if (mNativeAwHttpAuthHandler != 0) {
            nativeProceed(mNativeAwHttpAuthHandler, username, password);
            mNativeAwHttpAuthHandler = 0;
        }
    }

    public void cancel() {
        if (mNativeAwHttpAuthHandler != 0) {
            nativeCancel(mNativeAwHttpAuthHandler);
            mNativeAwHttpAuthHandler = 0;
        }
    }

    public boolean isFirstAttempt() {
         return mFirstAttempt;
    }

    @CalledByNative
    public static AwHttpAuthHandler create(int nativeAwAuthHandler, boolean firstAttempt) {
        return new AwHttpAuthHandler(nativeAwAuthHandler, firstAttempt);
    }

    private AwHttpAuthHandler(int nativeAwHttpAuthHandler, boolean firstAttempt) {
        mNativeAwHttpAuthHandler = nativeAwHttpAuthHandler;
        mFirstAttempt = firstAttempt;
    }

    @CalledByNative
    void handlerDestroyed() {
        mNativeAwHttpAuthHandler = 0;
    }

    private native void nativeProceed(int nativeAwHttpAuthHandler,
            String username, String password);
    private native void nativeCancel(int nativeAwHttpAuthHandler);
}
