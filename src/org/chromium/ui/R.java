// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

/**
 * Provide Android internal resources to Chrome's ui layer.  This allows classes
 * that access resources via org.chromium.ui.R to function properly in webview.
 * In a normal Chrome build, ui resources live in a res folder in the ui layer
 * and the org.chromium.ui.R class is generated at build time based on these
 * resources.  In webview, resources live in the Android framework and can't be
 * accessed directly from the ui layer.  Instead, we copy resources needed by ui
 * into the Android framework and use this R class to map resources IDs from
 * org.chromium.ui.R to com.android.internal.R.
 */
public final class R {
    public static final class string {
        public static int low_memory_error;
        public static int opening_file_error;
    }
}
