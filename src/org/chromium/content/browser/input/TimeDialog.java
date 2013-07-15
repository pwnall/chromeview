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

    private int mYear;
    private int mMonth;
    private int mDay;

    public static TimeDialog create(Context context, OnTimeSetListener callBack,
            int year, int month, int day, int hour,
            int minute, boolean is24HourView, long min, long max) {
        Time time = getBoundedTime(year, month, day, hour, minute, min, max);
        return new TimeDialog(context, callBack, year, month, day, time.hour, time.minute,
                is24HourView, min, max);
    }

    private TimeDialog(
            Context context, OnTimeSetListener callBack, int year, int month, int day,
            int hourOfDay, int minute, boolean is24HourView, long min, long max) {
        super(context, callBack, hourOfDay, minute, is24HourView);
        mYear = year;
        mMonth = month;
        mDay = day;
        Time time = new Time();
        time.set(min);
        mMinTime = new Time(time);
        time.set(max);
        mMaxTime = new Time(time);
     }

    public void updateBaseDate(int year, int month, int day) {
        mYear = year;
        mMonth = month;
        mDay = day;
    }

     @Override
     public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
         Time time = getBoundedTime(mYear, mMonth, mDay, hourOfDay, minute,
                 mMinTime.toMillis(true), mMaxTime.toMillis(true));
         super.onTimeChanged(view, time.hour, time.minute);
         updateTime(time.hour, time.minute);
     }

     private static Time getBoundedTime(int year, int month, int day, int hour,
             int minute, long min, long max) {
         Time time = new Time();
         time.set(0, minute, hour, day, month, year);
         if (time.toMillis(true) < min) {
             time.set(min);
         } else if (time.toMillis(true) > max) {
             time.set(max);
         }
         return time;
     }
}
