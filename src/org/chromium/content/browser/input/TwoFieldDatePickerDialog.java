// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import org.chromium.content.browser.input.TwoFieldDatePicker.OnMonthOrWeekChangedListener;
import org.chromium.content.R;

public abstract class TwoFieldDatePickerDialog extends AlertDialog implements OnClickListener,
        OnMonthOrWeekChangedListener {

    private static final String YEAR = "year";
    private static final String POSITION_IN_YEAR = "position_in_year";

    protected final TwoFieldDatePicker mPicker;
    protected final  OnValueSetListener mCallBack;

    /**
     * The callback used to indicate the user is done filling in the date.
     */
    public interface OnValueSetListener {

        /**
         * @param year The year that was set.
         * @param positionInYear The week in year.
         *  with {@link java.util.Calendar}.
         */
        void onValueSet(int year, int positionInYear);
     }

    /**
     * @param context The context the dialog is to run in.
     * @param callBack How the parent is notified that the date is set.
     * @param year The initial year of the dialog.
     * @param weekOfYear The initial week of the dialog.
     */
    public TwoFieldDatePickerDialog(Context context,
             OnValueSetListener callBack,
            int year,
            int positionInYear,
            long minValue,
            long maxValue) {
        this(context, 0, callBack, year, positionInYear, minValue, maxValue);
    }

    /**
     * @param context The context the dialog is to run in.
     * @param theme the theme to apply to this dialog
     * @param callBack How the parent is notified that the date is set.
     * @param year The initial year of the dialog.
     * @param weekOfYear The initial week of the dialog.
     */
    public TwoFieldDatePickerDialog(Context context,
            int theme,
             OnValueSetListener callBack,
            int year,
            int positionInYear,
            long minValue,
            long maxValue) {
        super(context, theme);

        mCallBack = callBack;

        setButton(BUTTON_POSITIVE, context.getText(
                R.string.date_picker_dialog_set), this);
        setButton(BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);
        setIcon(0);

        mPicker = createPicker(context, minValue, maxValue);
        setView(mPicker);
        mPicker.init(year, positionInYear, this);
    }

    protected TwoFieldDatePicker createPicker(Context context, long minValue, long maxValue) {
        return null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        tryNotifyDateSet();
    }

    /**
     * Notifies the listener, if such, that a date has been set.
     */
    protected abstract void tryNotifyDateSet();

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT >= 16) {
            // The default behavior of dialogs changed in JellyBean and onwards.
            // Dismissing a dialog (by pressing back for example)
            // applies the chosen date. This code is added here so that the custom
            // pickers behave the same as the internal DatePickerDialog.
            tryNotifyDateSet();
        }
        super.onStop();
    }

    @Override
    public void onMonthOrWeekChanged(TwoFieldDatePicker view, int year, int positionInYear) {
        mPicker.init(year, positionInYear, null);
    }

    /**
     * Sets the current date.
     *
     * @param year The date week year.
     * @param weekOfYear The date week.
     */
    public void updateDate(int year, int weekOfYear) {
        mPicker.updateDate(year, weekOfYear);
    }
}
