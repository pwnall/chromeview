// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.gfx;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("ui")
public class BitmapHelper {
    @CalledByNative
    public static Bitmap createBitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    /**
     * Decode and sample down a bitmap resource to the requested width and height.
     *
     * @param name The resource name of the bitmap to decode.
     * @param reqWidth The requested width of the resulting bitmap.
     * @param reqHeight The requested height of the resulting bitmap.
     * @return A bitmap sampled down from the original with the same aspect ratio and dimensions.
     *         that are equal to or greater than the requested width and height.
     */
    @CalledByNative
    private static Bitmap decodeDrawableResource(String name,
                                                 int reqWidth,
                                                 int reqHeight) {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier(name, null, null);

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    // http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth,
                                             int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }
}
