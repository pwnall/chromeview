// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;

import java.util.Calendar;

import org.chromium.content.R;

// This class is heavily based on android.widget.DatePicker.
public class WeekPicker extends TwoFieldDatePicker {

    public WeekPicker(Context context, long minValue, long maxValue) {
        super(context, minValue, maxValue);

        getPositionInYearSpinner().setContentDescription(
                getResources().getString(R.string.accessibility_date_picker_week));

        // initialize to current date
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.setMinimalDaysInFirstWeek(4);
        cal.setTimeInMillis(System.currentTimeMillis());
        init(getISOWeekYearForDate(cal), getWeekForDate(cal), null);
    }

    private Calendar createDateFromWeek(int year, int week) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.setFirstDayOfWeek(Calendar.MONDAY);
        date.setMinimalDaysInFirstWeek(4);
        date.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        date.set(Calendar.YEAR, year);
        date.set(Calendar.WEEK_OF_YEAR, week);
        return date;
    }

    @Override
    protected Calendar createDateFromValue(long value) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.setFirstDayOfWeek(Calendar.MONDAY);
        date.setMinimalDaysInFirstWeek(4);
        date.setTimeInMillis(value);
        return date;
    }

    public static int getISOWeekYearForDate(Calendar date) {
        int year = date.get(Calendar.YEAR);
        int month = date.get(Calendar.MONTH);
        int week = date.get(Calendar.WEEK_OF_YEAR);
        if (month == 0 && week > 51) {
            year--;
        } else if (month == 11 && week == 1) {
            year++;
        }
        return year;
    }

    public static int getWeekForDate(Calendar date) {
        return date.get(Calendar.WEEK_OF_YEAR);
    }

    @Override
    protected void setCurrentDate(int year, int week) {
        Calendar date = createDateFromWeek(year, week);
        if (date.before(getMinDate())) {
            setCurrentDate(getMinDate());
        } else if (date.after(getMaxDate())) {
            setCurrentDate(getMaxDate());
        } else {
            setCurrentDate(date);
        }
    }

    private int getNumberOfWeeks() {
        // Create a date in the middle of the year, where the week year matches the year.
        Calendar date = createDateFromWeek(getYear(), 20);
        return date.getActualMaximum(Calendar.WEEK_OF_YEAR);
    }

    /**
     * @return The selected year.
     */
    @Override
    public int getYear() {
        return getISOWeekYearForDate(getCurrentDate());
    }

    /**
     * @return The selected week.
     */
    public int getWeek() {
        return getWeekForDate(getCurrentDate());
    }

    @Override
    public int getPositionInYear() {
        return getWeek();
    }

    @Override
    protected int getMaxYear() {
        return getISOWeekYearForDate(getMaxDate());
    }

    @Override
    protected int getMinYear() {
        return getISOWeekYearForDate(getMinDate());
    }

    @Override
    protected int getMaxPositionInYear() {
        if (getYear() == getISOWeekYearForDate(getMaxDate())) {
            return getWeekForDate(getMaxDate());
        }
        return getNumberOfWeeks();
    }

    @Override
    protected int getMinPositionInYear() {
        if (getYear() == getISOWeekYearForDate(getMinDate())) {
            return getWeekForDate(getMinDate());
        }
        return 1;
    }
}
