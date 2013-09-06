// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

import java.util.List;

/**
 * Subclass of BrowserAccessibilityManager for JellyBean that creates an
 * AccessibilityNodeProvider and delegates its implementation to this object.
 */
@JNINamespace("content")
public class JellyBeanBrowserAccessibilityManager extends BrowserAccessibilityManager {
    private AccessibilityNodeProvider mAccessibilityNodeProvider;

    JellyBeanBrowserAccessibilityManager(int nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        super(nativeBrowserAccessibilityManagerAndroid, contentViewCore);

        final BrowserAccessibilityManager delegate = this;
        mAccessibilityNodeProvider = new AccessibilityNodeProvider() {
            @Override
            public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                return delegate.createAccessibilityNodeInfo(virtualViewId);
            }

            @Override
            public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,
                    int virtualViewId) {
                return delegate.findAccessibilityNodeInfosByText(text, virtualViewId);
            }

            @Override
            public boolean performAction(int virtualViewId, int action, Bundle arguments) {
                return delegate.performAction(virtualViewId, action, arguments);
            }
        };
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return mAccessibilityNodeProvider;
    }
}
