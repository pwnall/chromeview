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

    @CalledByNative
    public static Bitmap decodeDrawableResource(String name) {
        Resources res = Resources.getSystem();
        int resource_id = res.getIdentifier(name, null, null);

        return BitmapFactory.decodeResource(res, resource_id);
    }
}
