// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Wrapper around Android's InputMethodManager
 */
class InputMethodManagerWrapper {
    private final Context mContext;

    public InputMethodManagerWrapper(Context context) {
        mContext = context;
    }

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#restartInput(View)
     */
    public void restartInput(View view) {
        getInputMethodManager().restartInput(view);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#showSoftInput(View, int, ResultReceiver)
     */
    public void showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        getInputMethodManager().showSoftInput(view, flags, resultReceiver);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#isActive(View)
     */
    public boolean isActive(View view) {
        return getInputMethodManager().isActive(view);
    }

    /**
     * @see InputMethodManager#hideSoftInputFromWindow(IBinder, int, ResultReceiver)
     */
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags,
            ResultReceiver resultReceiver) {
        return getInputMethodManager().hideSoftInputFromWindow(windowToken, flags, resultReceiver);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#updateSelection(View, int, int, int, int)
     */
    public void updateSelection(View view, int selStart, int selEnd,
            int candidatesStart, int candidatesEnd) {
        getInputMethodManager().updateSelection(view, selStart, selEnd, candidatesStart,
                candidatesEnd);
    }
}
