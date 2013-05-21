// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/**
 * This class provides the locale related methods for the native library.
 */
@JNINamespace("l10n_util")
public class LocalizationUtils {

    // This is mirrored from base/i18n/rtl.h. Please keep in sync.
    public static final int UNKNOWN_DIRECTION = 0;
    public static final int RIGHT_TO_LEFT = 1;
    public static final int LEFT_TO_RIGHT = 2;

    private LocalizationUtils() { /* cannot be instantiated */ }

    /**
     * @return the default locale, translating Android deprecated
     * language codes into the modern ones used by Chromium.
     */
    @CalledByNative
    public static String getDefaultLocale() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();

        // Android uses deprecated lanuages codes for Hebrew and Indonesian but Chromium uses the
        // updated codes. Also, Android uses "tl" while Chromium uses "fil" for Tagalog/Filipino.
        // So apply a mapping.
        // See http://developer.android.com/reference/java/util/Locale.html
        if ("iw".equals(language)) {
            language = "he";
        } else if ("in".equals(language)) {
            language = "id";
        } else if ("tl".equals(language)) {
            language = "fil";
        }
        return country.isEmpty() ? language : language + "-" + country;
    }

    @CalledByNative
    private static Locale getJavaLocale(String language, String country, String variant) {
        return new Locale(language, country, variant);
    }

    @CalledByNative
    private static String getDisplayNameForLocale(Locale locale, Locale displayLocale) {
        return locale.getDisplayName(displayLocale);
    }

    /**
     * @return true if the system default layout direction is RTL, false otherwise.
     *         RTL layout support is from Jelly Bean MR1, so if the version is lower
     *         than that, it is always false.
     */
    public static boolean isSystemLayoutDirectionRtl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
                    == View.LAYOUT_DIRECTION_RTL;
        }
        return false;
    }

    /**
     * Jni binding to base::i18n::IsRTL.
     * @return true if the current locale is right to left.
     */
    public static boolean isRtl() {
        return nativeIsRTL();
    }

    /**
     * Jni binding to base::i18n::GetFirstStrongCharacterDirection
     * @param string String to decide the direction.
     * @return One of the UNKNOWN_DIRECTION, RIGHT_TO_LEFT, and LEFT_TO_RIGHT.
     */
    public static int getFirstStrongCharacterDirection(String string) {
        return nativeGetFirstStrongCharacterDirection(string);
    }

    private static native boolean nativeIsRTL();

    private static native int nativeGetFirstStrongCharacterDirection(String string);
}
