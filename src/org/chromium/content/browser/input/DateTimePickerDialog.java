// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

import org.chromium.content.R;

class DateTimePickerDialog extends AlertDialog implements OnClickListener,
        OnDateChangedListener, OnTimeChangedListener {

    private static final String YEAR = "year";
    private static final String MONTH = "month";
    private static final String DAY = "day";
    private static final String HOUR = "hour";
    private static final String MINUTE = "minute";
    private static final String IS_24_HOUR = "is24hour";

    private final DatePicker mDatePicker;
    private final TimePicker mTimePicker;
    private final OnDateTimeSetListener mCallBack;

    private final long mMinTimeMillis;
    private final long mMaxTimeMillis;

    /**
     * The callback used to indicate the user is done filling in the date.
     */
    public interface OnDateTimeSetListener {

        /**
         * @param dateView The DatePicker view associated with this listener.
         * @param timeView The TimePicker view associated with this listener.
         * @param year The year that was set.
         * @param monthOfYear The month that was set (0-11) for compatibility
         *            with {@link java.util.Calendar}.
         * @param dayOfMonth The day of the month that was set.
         * @param hourOfDay The hour that was set.
         * @param minute The minute that was set.
         */
        void onDateTimeSet(DatePicker dateView, TimePicker timeView, int year, int monthOfYear,
                int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * @param context The context the dialog is to run in.
     * @param callBack How the parent is notified that the date is set.
     * @param year The initial year of the dialog.
     * @param monthOfYear The initial month of the dialog.
     * @param dayOfMonth The initial day of the dialog.
     */
    public DateTimePickerDialog(Context context,
            OnDateTimeSetListener callBack,
            int year,
            int monthOfYear,
            int dayOfMonth,
            int hourOfDay, int minute, boolean is24HourView,
            long min, long max) {
        super(context, 0);

        mMinTimeMillis = min;
        mMaxTimeMillis = max;

        mCallBack = callBack;

        setButton(BUTTON_POSITIVE, context.getText(
                R.string.date_picker_dialog_set), this);
        setButton(BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);
        setIcon(0);
        setTitle(context.getText(R.string.date_time_picker_dialog_title));

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.date_time_picker_dialog, null);
        setView(view);
        mDatePicker = (DatePicker) view.findViewById(R.id.date_picker);
        DateDialogNormalizer.normalize(mDatePicker, this,
                year, monthOfYear, dayOfMonth, hourOfDay, minute, min, max);

        mTimePicker = (TimePicker) view.findViewById(R.id.time_picker);
        mTimePicker.setIs24HourView(is24HourView);
        mTimePicker.setCurrentHour(hourOfDay);
        mTimePicker.setCurrentMinute(minute);
        mTimePicker.setOnTimeChangedListener(this);
        onTimeChanged(mTimePicker, mTimePicker.getCurrentHour(),
                mTimePicker.getCurrentMinute());
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        tryNotifyDateTimeSet();
    }

    private void tryNotifyDateTimeSet() {
        if (mCallBack != null) {
            mDatePicker.clearFocus();
            mCallBack.onDateTimeSet(mDatePicker, mTimePicker, mDatePicker.getYear(),
                    mDatePicker.getMonth(), mDatePicker.getDayOfMonth(),
                    mTimePicker.getCurrentHour(), mTimePicker.getCurrentMinute());
        }
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT >= 16) {
            // The default behavior of dialogs changed in JellyBean and onwards.
            // Dismissing a dialog (by pressing back for example)
            // applies the chosen date. This code is added here so that the custom
            // pickers behave the same as the internal DatePickerDialog.
            tryNotifyDateTimeSet();
        }
        super.onStop();
    }

    @Override
    public void onDateChanged(DatePicker view, int year,
            int month, int day) {
        // Signal a time change so the max/min checks can be applied.
        if (mTimePicker != null) {
            onTimeChanged(mTimePicker, mTimePicker.getCurrentHour(),
                    mTimePicker.getCurrentMinute());
        }
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        Time time = new Time();
        time.set(0, mTimePicker.getCurrentMinute(),
                mTimePicker.getCurrentHour(), mDatePicker.getDayOfMonth(),
                mDatePicker.getMonth(), mDatePicker.getYear());

        if (time.toMillis(true) < mMinTimeMillis) {
            time.set(mMinTimeMillis);
        } else if (time.toMillis(true) > mMaxTimeMillis) {
            time.set(mMaxTimeMillis);
        }
        mTimePicker.setCurrentHour(time.hour);
        mTimePicker.setCurrentMinute(time.minute);
    }

    /**
     * Sets the current date.
     *
     * @param year The date year.
     * @param monthOfYear The date month.
     * @param dayOfMonth The date day of month.
     */
    public void updateDateTime(int year, int monthOfYear, int dayOfMonth,
            int hourOfDay, int minutOfHour) {
        mDatePicker.updateDate(year, monthOfYear, dayOfMonth);
        mTimePicker.setCurrentHour(hourOfDay);
        mTimePicker.setCurrentMinute(minutOfHour);
    }
}
