// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;

/**
 * WebView-specific WebContentsDelegate.
 * This file is the Java version of the native class of the same name.
 * It should contain abstract WebContentsDelegate methods to be implemented by the embedder.
 * These methods belong to WebView but are not shared with the Chromium Android port.
 */
@JNINamespace("android_webview")
public abstract class AwWebContentsDelegate extends WebContentsDelegateAndroid {
    @CalledByNative
    public abstract boolean addNewContents(boolean isDialog, boolean isUserGesture);

    @CalledByNative
    public abstract void closeContents();

    @CalledByNative
    public abstract void activateContents();

    /**
     * Report a change in the preferred size.
     * @param width preferred width in CSS pixels.
     * @param height scroll height of the document element in CSS pixels.
     */
    @CalledByNative
    public void updatePreferredSize(int widthCss, int heightCss) {
    }
}
