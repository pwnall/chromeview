// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Provides auxiliary methods related to Picture objects and native SkPictures.
 */
@JNINamespace("android_webview")
public class JavaBrowserViewRendererHelper {

    /**
     * Provides a Bitmap object with a given width and height used for auxiliary rasterization.
     */
    @CalledByNative
    private static Bitmap createBitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    /**
     * Draws a provided bitmap into a canvas.
     * Used for convenience from the native side and other static helper methods.
     */
    @CalledByNative
    private static void drawBitmapIntoCanvas(Bitmap bitmap, Canvas canvas) {
        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    /**
     * Creates a new Picture that records drawing a provided bitmap.
     * Will return an empty Picture if the Bitmap is null.
     */
    @CalledByNative
    private static Picture recordBitmapIntoPicture(Bitmap bitmap) {
        Picture picture = new Picture();
        if (bitmap != null) {
            Canvas recordingCanvas = picture.beginRecording(bitmap.getWidth(), bitmap.getHeight());
            drawBitmapIntoCanvas(bitmap, recordingCanvas);
            picture.endRecording();
        }
        return picture;
    }

    // Should never be instantiated.
    private JavaBrowserViewRendererHelper() {
    }
}
