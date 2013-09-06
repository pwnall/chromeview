// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.ui.WindowAndroid;

class PowerSaveBlocker {
    @CalledByNative
    private static void applyBlock(WindowAndroid windowAndroid) {
        windowAndroid.keepScreenOn(true);
    }

    @CalledByNative
    private static void removeBlock(WindowAndroid windowAndroid) {
        windowAndroid.keepScreenOn(false);
    }
}