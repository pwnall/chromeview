// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.gfx;

import android.graphics.SurfaceTexture;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Listener to an android SurfaceTexture object for frame availability.
 */
@JNINamespace("gfx")
class SurfaceTextureListener implements SurfaceTexture.OnFrameAvailableListener {
    // Used to determine the class instance to dispatch the native call to.
    private int mNativeSurfaceTextureListener = 0;

    private SurfaceTextureListener(int nativeSurfaceTextureListener) {
        assert nativeSurfaceTextureListener != 0;
        mNativeSurfaceTextureListener = nativeSurfaceTextureListener;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        nativeFrameAvailable(mNativeSurfaceTextureListener);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            nativeDestroy(mNativeSurfaceTextureListener);
        } finally {
            super.finalize();
        }
    }

    @CalledByNative
    private static SurfaceTextureListener create(int nativeSurfaceTextureListener) {
        return new SurfaceTextureListener(nativeSurfaceTextureListener);
    }

    private native void nativeFrameAvailable(int nativeSurfaceTextureListener);
    private native void nativeDestroy(int nativeSurfaceTextureListener);
}
