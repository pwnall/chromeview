// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;

import java.util.Calendar;

/**
 * Normalize a date dialog so that it respect min and max.
 */
class DateDialogNormalizer {
    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000;

    private static void setLimits(DatePicker picker, long min, long max) {
        // DatePicker intervals are non inclusive.
        long minTime = min > 0 ? min - 1 : 0;
        long maxTime = (max - ONE_DAY_MILLIS) >= (Long.MAX_VALUE - ONE_DAY_MILLIS) ?
                Long.MAX_VALUE : max + ONE_DAY_MILLIS;

        // While the widget is only able to display date, min/max can also contain time
        // information.
        // Trim min/max to date before adjusting the picker.
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeInMillis(max);

        picker.setMaxDate(trimToDate(maxTime));
        picker.setMinDate(trimToDate(minTime));
    }

    /**
     * Resets the hour, minute, second piece of a time stamp to 0, maintaining the remaining
     * components (year, month, day).
     */
    private static long trimToDate(long time) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeInMillis(time);
        Calendar result = Calendar.getInstance();
        result.clear();
        result.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                0, 0, 0);
        return result.getTimeInMillis();
    }

    /**
     * Normalizes an existing DateDialogPicker changing the default date if
     * needed to comply with the {@code min} and {@code max} attributes.
     */
    static void normalize(DatePicker picker, OnDateChangedListener listener,
            int year, int month, int day, int hour, int minute, long min, long max) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        if (calendar.getTimeInMillis() < min) {
            calendar.clear();
            calendar.setTimeInMillis(min);
        } else if (calendar.getTimeInMillis() > max) {
            calendar.clear();
            calendar.setTimeInMillis(max);
        }
        picker.init(
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH), listener);

        setLimits(picker, min, max);
    }
}
