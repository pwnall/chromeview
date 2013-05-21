// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.widget.DatePicker;
import android.widget.TimePicker;

import org.chromium.content.browser.input.DateTimePickerDialog.OnDateTimeSetListener;
import org.chromium.content.browser.input.MonthPickerDialog.OnMonthSetListener;
import org.chromium.content.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class InputDialogContainer {

    interface InputActionDelegate {
        void cancelDateTimeDialog();
        void replaceDateTime(int dialogType,
                int year, int month, int day, int hour, int minute, int second);
    }

    // Default values used in Time representations of selected date/time before formatting.
    // They are never displayed to the user.
    private static final int YEAR_DEFAULT = 1970;
    private static final int MONTH_DEFAULT = 0;
    private static final int MONTHDAY_DEFAULT = 1;
    private static final int HOUR_DEFAULT = 0;
    private static final int MINUTE_DEFAULT = 0;

    // Date formats as accepted by Time.format.
    private static final String HTML_DATE_FORMAT = "%Y-%m-%d";
    private static final String HTML_TIME_FORMAT = "%H:%M";
    // For datetime we always send selected time as UTC, as we have no timezone selector.
    // This is consistent with other browsers.
    private static final String HTML_DATE_TIME_FORMAT = "%Y-%m-%dT%H:%MZ";
    private static final String HTML_DATE_TIME_LOCAL_FORMAT = "%Y-%m-%dT%H:%M";
    private static final String HTML_MONTH_FORMAT = "%Y-%m";

    private static int sTextInputTypeDate;
    private static int sTextInputTypeDateTime;
    private static int sTextInputTypeDateTimeLocal;
    private static int sTextInputTypeMonth;
    private static int sTextInputTypeTime;

    private Context mContext;

    // Prevents sending two notifications (from onClick and from onDismiss)
    private boolean mDialogAlreadyDismissed;

    private AlertDialog mDialog;
    private InputActionDelegate mInputActionDelegate;

    static void initializeInputTypes(int textInputTypeDate, int textInputTypeDateTime,
            int textInputTypeDateTimeLocal, int textInputTypeMonth, int textInputTypeTime) {
        sTextInputTypeDate = textInputTypeDate;
        sTextInputTypeDateTime = textInputTypeDateTime;
        sTextInputTypeDateTimeLocal = textInputTypeDateTimeLocal;
        sTextInputTypeMonth = textInputTypeMonth;
        sTextInputTypeTime = textInputTypeTime;
    }

    static boolean isDialogInputType(int type) {
        return type == sTextInputTypeDate || type == sTextInputTypeTime
                || type == sTextInputTypeDateTime || type == sTextInputTypeDateTimeLocal
                || type == sTextInputTypeMonth;
    }

    InputDialogContainer(Context context, InputActionDelegate inputActionDelegate) {
        mContext = context;
        mInputActionDelegate = inputActionDelegate;
    }

    private Time normalizeTime(int year, int month, int monthDay,
            int hour, int minute, int second)  {
        Time result = new Time();
        if (year == 0 && month == 0 && monthDay == 0 && hour == 0 &&
                minute == 0 && second == 0) {
            Calendar cal = Calendar.getInstance();
            result.set(cal.get(Calendar.SECOND), cal.get(Calendar.MINUTE),
                    cal.get(Calendar.HOUR), cal.get(Calendar.DATE),
                    cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
        } else {
            result.set(second, minute, hour, monthDay, month, year);
        }
        return result;
    }

    void showDialog(final int dialogType, int year, int month, int monthDay,
            int hour, int minute, int second) {
        if (isDialogShowing()) mDialog.dismiss();

        Time time = normalizeTime(year, month, monthDay, hour, minute, second);
        if (dialogType == sTextInputTypeDate) {
            mDialog = new DatePickerDialog(mContext, new DateListener(dialogType),
                    time.year, time.month, time.monthDay);
            mDialog.setTitle(mContext.getText(R.string.date_picker_dialog_title));
        } else if (dialogType == sTextInputTypeTime) {
            mDialog = new TimePickerDialog(mContext, new TimeListener(dialogType),
                    time.hour, time.minute, DateFormat.is24HourFormat(mContext));
        } else if (dialogType == sTextInputTypeDateTime ||
                dialogType == sTextInputTypeDateTimeLocal) {
            mDialog = new DateTimePickerDialog(mContext,
                    new DateTimeListener(dialogType),
                    time.year, time.month, time.monthDay,
                    time.hour, time.minute, DateFormat.is24HourFormat(mContext));
        } else if (dialogType == sTextInputTypeMonth) {
            mDialog = new MonthPickerDialog(mContext, new MonthListener(dialogType),
                    time.year, time.month);
        }

        mDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getText(R.string.date_picker_dialog_set),
                (DialogInterface.OnClickListener) mDialog);

        mDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                mContext.getText(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDialogAlreadyDismissed = true;
                        mInputActionDelegate.cancelDateTimeDialog();
                    }
                });

        mDialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                mContext.getText(R.string.date_picker_dialog_clear),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDialogAlreadyDismissed = true;
                        mInputActionDelegate.replaceDateTime(dialogType, 0, 0, 0, 0, 0, 0);
                    }
                });

        mDialogAlreadyDismissed = false;
        mDialog.show();
    }

    boolean isDialogShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    void dismissDialog() {
        if (isDialogShowing()) mDialog.dismiss();
    }

    private class DateListener implements OnDateSetListener {
        private final int mDialogType;

        DateListener(int dialogType) {
            mDialogType = dialogType;
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int monthDay) {
            if (!mDialogAlreadyDismissed) {
                setFieldDateTimeValue(mDialogType,
                        year, month, monthDay, HOUR_DEFAULT, MINUTE_DEFAULT,
                        HTML_DATE_FORMAT);
            }
        }
    }

    private class TimeListener implements OnTimeSetListener {
        private final int mDialogType;

        TimeListener(int dialogType) {
            mDialogType = dialogType;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            if (!mDialogAlreadyDismissed) {
                setFieldDateTimeValue(mDialogType,
                        YEAR_DEFAULT, MONTH_DEFAULT, MONTHDAY_DEFAULT,
                        hourOfDay, minute, HTML_TIME_FORMAT);
            }
        }
    }

    private class DateTimeListener implements OnDateTimeSetListener {
        private final boolean mLocal;
        private final int mDialogType;

        public DateTimeListener(int dialogType) {
            mLocal = dialogType == sTextInputTypeDateTimeLocal;
            mDialogType = dialogType;
        }

        @Override
        public void onDateTimeSet(DatePicker dateView, TimePicker timeView,
                int year, int month, int monthDay,
                int hourOfDay, int minute) {
            if (!mDialogAlreadyDismissed) {
                setFieldDateTimeValue(mDialogType, year, month, monthDay, hourOfDay, minute,
                        mLocal ? HTML_DATE_TIME_LOCAL_FORMAT : HTML_DATE_TIME_FORMAT);
            }
        }
    }

    private class MonthListener implements OnMonthSetListener {
        private final int mDialogType;

        MonthListener(int dialogType) {
            mDialogType = dialogType;
        }

        @Override
        public void onMonthSet(MonthPicker view, int year, int month) {
            if (!mDialogAlreadyDismissed) {
                setFieldDateTimeValue(mDialogType, year, month, MONTHDAY_DEFAULT,
                        HOUR_DEFAULT, MINUTE_DEFAULT, HTML_MONTH_FORMAT);
            }
        }
    }

    private void setFieldDateTimeValue(int dialogType,
            int year, int month, int monthDay, int hourOfDay,
            int minute, String dateFormat) {
        // Prevents more than one callback being sent to the native
        // side when the dialog triggers multiple events.
        mDialogAlreadyDismissed = true;

        mInputActionDelegate.replaceDateTime(dialogType,
                year, month, monthDay, hourOfDay, minute, 0 /* second */);
    }
}
