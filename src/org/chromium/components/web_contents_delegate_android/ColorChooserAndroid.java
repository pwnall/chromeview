// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.web_contents_delegate_android;

import android.content.Context;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.ColorPickerDialog;

/**
 * ColorChooserAndroid communicates with the java ColorPickerDialog and the
 * native color_chooser_android.cc
 */
@JNINamespace("components")
public class ColorChooserAndroid {
    private final ColorPickerDialog mDialog;
    private final int mNativeColorChooserAndroid;

    private ColorChooserAndroid(int nativeColorChooserAndroid,
            Context context, int initialColor) {
        ColorPickerDialog.OnColorChangedListener listener =
                new ColorPickerDialog.OnColorChangedListener() {

          @Override
          public void colorChanged(int color) {
              mDialog.dismiss();
              nativeOnColorChosen(mNativeColorChooserAndroid, color);
          }
        };

        mNativeColorChooserAndroid = nativeColorChooserAndroid;
        mDialog = new ColorPickerDialog(context, listener, initialColor);
    }

    private void openColorChooser() {
        mDialog.show();
    }

    @CalledByNative
    public void closeColorChooser() {
        mDialog.dismiss();
    }

    @CalledByNative
    public static ColorChooserAndroid createColorChooserAndroid(
            int nativeColorChooserAndroid,
            ContentViewCore contentViewCore,
            int initialColor) {
        ColorChooserAndroid chooser = new ColorChooserAndroid(nativeColorChooserAndroid,
            contentViewCore.getContext(), initialColor);
        chooser.openColorChooser();
        return chooser;
    }

    // Implemented in color_chooser_android.cc
    private native void nativeOnColorChosen(int nativeColorChooserAndroid, int color);
}
