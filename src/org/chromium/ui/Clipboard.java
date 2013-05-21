// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

/**
 * Simple proxy that provides C++ code with an access pathway to the Android
 * clipboard.
 */
@JNINamespace("ui")
public class Clipboard {
    // Necessary for coercing clipboard contents to text if they require
    // access to network resources, etceteras (e.g., URI in clipboard)
    private final Context mContext;

    private final ClipboardManager mClipboardManager;

    /**
     * Use the factory constructor instead.
     *
     * @param context for accessing the clipboard
     */
    private Clipboard(final Context context) {
        mContext = context;
        mClipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Returns a new Clipboard object bound to the specified context.
     *
     * @param context for accessing the clipboard
     * @return the new object
     */
    @CalledByNative
    private static Clipboard create(final Context context) {
        return new Clipboard(context);
    }

    /**
     * Emulates the behavior of the now-deprecated
     * {@link android.text.ClipboardManager#getText()} by invoking
     * {@link android.content.ClipData.Item#coerceToText(Context)} on the first
     * item in the clipboard (if any) and returning the result as a string.
     * <p>
     * This is quite different than simply calling {@link Object#toString()} on
     * the clip; consumers of this API should familiarize themselves with the
     * process described in
     * {@link android.content.ClipData.Item#coerceToText(Context)} before using
     * this method.
     *
     * @return a string representation of the first item on the clipboard, if
     *         the clipboard currently has an item and coercion of the item into
     *         a string is possible; otherwise, <code>null</code>
     */
    @SuppressWarnings("javadoc")
    @CalledByNative
    private String getCoercedText() {
        final ClipData clip = mClipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            final CharSequence sequence = clip.getItemAt(0).coerceToText(mContext);
            if (sequence != null) {
                return sequence.toString();
            }
        }
        return null;
    }

    /**
     * Emulates the behavior of the now-deprecated
     * {@link android.text.ClipboardManager#setText(CharSequence)}, setting the
     * clipboard's current primary clip to a plain-text clip that consists of
     * the specified string.
     *
     * @param text will become the content of the clipboard's primary clip
     */
    @SuppressWarnings("javadoc")
    @CalledByNative
    private void setText(final String text) {
        mClipboardManager.setPrimaryClip(ClipData.newPlainText(null, text));
    }

    /**
     * Approximates the behavior of the now-deprecated
     * {@link android.text.ClipboardManager#hasText()}, returning true if and
     * only if the clipboard has a primary clip and that clip contains a plain
     * non-empty text entry (without attempting coercion - URLs and intents
     * will cause this method to return false).
     *
     * @return as described above
     */
    @SuppressWarnings("javadoc")
    @CalledByNative
    private boolean hasPlainText() {
        final ClipData clip = mClipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            final CharSequence text = clip.getItemAt(0).getText();
            return !TextUtils.isEmpty(text);
        }
        return false;
    }
}
