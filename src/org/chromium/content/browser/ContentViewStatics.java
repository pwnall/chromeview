// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.net.ProxyChangeListener;

/**
 * Implementations of various static methods.
 */
public class ContentViewStatics {

    /**
     * Return the first substring consisting of the address of a physical location.
     * @see {@link android.webkit.WebView#findAddress(String)}
     *
     * @param addr The string to search for addresses.
     * @return the address, or if no address is found, return null.
     */
    public static String findAddress(String addr) {
        if (addr == null) {
            throw new NullPointerException("addr is null");
        }
        String result = nativeFindAddress(addr);
        return result == null || result.isEmpty() ? null : result;
    }

    /**
     * Suspends Webkit timers in all renderers.
     * New renderers created after this call will be created with the
     * default options.
     *
     * @param suspend true if timers should be suspended.
     */
    public static void setWebKitSharedTimersSuspended(boolean suspend) {
        nativeSetWebKitSharedTimersSuspended(suspend);
    }

    /**
     * Enables platform notifications of data state and proxy changes.
     * Notifications are enabled by default.
     */
    public static void enablePlatformNotifications() {
        ProxyChangeListener.setEnabled(true);
    }

    /**
     * Disables platform notifications of data state and proxy changes.
     * Notifications are enabled by default.
     */
    public static void disablePlatformNotifications () {
        ProxyChangeListener.setEnabled(false);
    }

    // Native functions

    private static native String nativeFindAddress(String addr);

    private static native void nativeSetWebKitSharedTimersSuspended(boolean suspend);
}
