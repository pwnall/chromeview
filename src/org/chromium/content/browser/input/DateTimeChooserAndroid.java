// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

import android.content.Context;

/**
 * Plumbing for the different date/time dialog adapters.
 */
@JNINamespace("content")
class DateTimeChooserAndroid {

    private final int mNativeDateTimeChooserAndroid;
    private final InputDialogContainer mInputDialogContainer;

    private DateTimeChooserAndroid(Context context,
            int nativeDateTimeChooserAndroid) {
        mNativeDateTimeChooserAndroid = nativeDateTimeChooserAndroid;
        mInputDialogContainer = new InputDialogContainer(context,
                new InputDialogContainer.InputActionDelegate() {

            @Override
            public void replaceDateTime(
                    int dialogType,
                    int year, int month, int day, int hour, int minute, int second) {
                nativeReplaceDateTime(mNativeDateTimeChooserAndroid,
                        dialogType,
                        year, month, day, hour, minute, second);
            }

            @Override
            public void cancelDateTimeDialog() {
                nativeCancelDialog(mNativeDateTimeChooserAndroid);
            }
        });
    }

    private void showDialog(int dialogType, int year, int month, int monthDay,
            int hour, int minute, int second) {
        mInputDialogContainer.showDialog(dialogType, year, month, monthDay,
                hour, minute, second);
    }

    @CalledByNative
    private static DateTimeChooserAndroid createDateTimeChooser(
            ContentViewCore contentViewCore,
            int nativeDateTimeChooserAndroid, int dialogType,
            int year, int month, int day,
            int hour, int minute, int second) {
        DateTimeChooserAndroid chooser =
                new DateTimeChooserAndroid(
                        contentViewCore.getContext(), nativeDateTimeChooserAndroid);
        chooser.showDialog(dialogType, year, month, day, hour, minute, second);
        return chooser;
    }

    @CalledByNative
    private static void initializeDateInputTypes(int textInputTypeDate, int textInputTypeDateTime,
            int textInputTypeDateTimeLocal, int textInputTypeMonth,
            int textInputTypeTime) {
        InputDialogContainer.initializeInputTypes(textInputTypeDate, textInputTypeDateTime,
                textInputTypeDateTimeLocal, textInputTypeMonth, textInputTypeTime);
    }

    private native void nativeReplaceDateTime(int nativeDateTimeChooserAndroid,
            int dialogType,
            int year, int month, int day, int hour, int minute, int second);

    private native void nativeCancelDialog(int nativeDateTimeChooserAndroid);
}
