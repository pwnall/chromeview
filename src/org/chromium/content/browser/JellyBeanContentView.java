// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.chromium.ui.WindowAndroid;

/**
 * A version of {@link ContentView} that supports JellyBean features.
 */
class JellyBeanContentView extends ContentView {
    JellyBeanContentView(Context context, int nativeWebContents, WindowAndroid windowAndroid,
            AttributeSet attrs, int defStyle) {
        super(context, nativeWebContents, windowAndroid, attrs, defStyle);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (getContentViewCore().supportsAccessibilityAction(action)) {
            return getContentViewCore().performAccessibilityAction(action, arguments);
        }

        return super.performAccessibilityAction(action, arguments);
    }
}
