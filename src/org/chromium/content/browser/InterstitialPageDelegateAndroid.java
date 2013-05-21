// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Allows the specification and handling of Interstitial pages in java.
 */
@JNINamespace("content")
public class InterstitialPageDelegateAndroid {

    private int mNativePtr;

    /**
     * Constructs an interstitial with the given HTML content.
     *
     * @param htmlContent The HTML content for the interstitial.
     */
    public InterstitialPageDelegateAndroid(String htmlContent) {
        mNativePtr = nativeInit(htmlContent);
    }

    /**
     * @return The pointer to the underlying native counterpart.
     */
    public int getNative() {
        return mNativePtr;
    }

    /**
     * Called when "proceed" is triggered on the interstitial.
     */
    @CalledByNative
    protected void onProceed() {
    }

    /**
     * Called when "dont' proceed" is triggered on the interstitial.
     */
    @CalledByNative
    protected void onDontProceed() {
    }

    /**
     * Called when a command has been received from the interstitial.
     *
     * @param command The command that was received.
     */
    @CalledByNative
    protected void commandReceived(String command) {
    }

    @CalledByNative
    private void onNativeDestroyed() {
        mNativePtr = 0;
    }

    /**
     * Notifies the native interstitial to proceed.
     */
    protected void proceed() {
        if (mNativePtr != 0) nativeProceed(mNativePtr);
    }

    /**
     * Notifies the native interstitial to not proceed.
     */
    protected void dontProceed() {
        if (mNativePtr != 0) nativeDontProceed(mNativePtr);
    }

    private native int nativeInit(String htmlContent);
    private native void nativeProceed(int nativeInterstitialPageDelegateAndroid);
    private native void nativeDontProceed(int nativeInterstitialPageDelegateAndroid);
}
