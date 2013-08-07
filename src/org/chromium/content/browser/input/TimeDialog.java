// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.Time;
import android.widget.TimePicker;

/**
 * Wrapper on {@code TimePickerDialog} to control min and max times.
 */
public class TimeDialog extends TimePickerDialog {

    private Time mMinTime;
    private Time mMaxTime;

    public static TimeDialog create(Context context, OnTimeSetListener callBack,
            int hour, int minute, boolean is24HourView, long min, long max) {
        Time time = getBoundedTime(hour, minute, min, max);
        return new TimeDialog(context, callBack, time.hour, time.minute,
                is24HourView, min, max);
    }

    private TimeDialog(
            Context context, OnTimeSetListener callBack,
            int hourOfDay, int minute, boolean is24HourView, long min, long max) {
        super(context, callBack, hourOfDay, minute, is24HourView);
        if (min >= max) {
            mMinTime = getTimeForHourAndMinute(0, 0);
            mMaxTime = getTimeForHourAndMinute(23, 59);
        } else {
            mMinTime = getTimeForMillis(min);
            mMaxTime = getTimeForMillis(max);
        }
     }

     @Override
     public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
         Time time = getBoundedTime(hourOfDay, minute,
                 mMinTime.toMillis(true), mMaxTime.toMillis(true));
         super.onTimeChanged(view, time.hour, time.minute);
         updateTime(time.hour, time.minute);
     }

     private static Time getBoundedTime(int hour, int minute,
             long min, long max) {
         Time time = getTimeForHourAndMinute(hour, minute);
         if (time.toMillis(true) < min) {
             return getTimeForMillis(min);
         } else if (time.toMillis(true) > max) {
             return getTimeForMillis(max);
         }
         return time;
     }

     private static Time getTimeForMillis(long millis) {
         Time time = new Time("GMT");
         time.set(millis);
         return time;
     }

     private static Time getTimeForHourAndMinute(int hour, int minute) {
         Time time = new Time("GMT");
         time.set(0, minute, hour, 1, 0, 1970);
         return time;
     }
}
