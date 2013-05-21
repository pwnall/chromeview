// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.JNINamespace;

/**
 * Handler for tracing related intent.
 */
@JNINamespace("content")
public class TracingIntentHandler {

    /**
     * Begin recording trace events.
     *
     * @param path Specifies where the trace data will be saved when tracing is stopped.
     */
    public static void beginTracing(String path) {
        nativeBeginTracing(path);
    }

    /**
     * Stop recording trace events, and dump the data to the file
     */
    public static void endTracing() {
        nativeEndTracing();
    }

    private static native void nativeBeginTracing(String path);
    private static native void nativeEndTracing();
}
