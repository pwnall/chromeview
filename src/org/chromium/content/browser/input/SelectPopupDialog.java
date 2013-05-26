// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import org.chromium.content.browser.ContentViewCore;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

/**
 * Handles the popup dialog for the <select> HTML tag support.
 */
public class SelectPopupDialog {
    // The currently showing popup dialog, null if none is showing.
    private static SelectPopupDialog sShownDialog;

    // The dialog hosting the popup list view.
    private AlertDialog mListBoxPopup = null;

    private ContentViewCore mContentViewCore;

    /**
     * Subclass ArrayAdapter so we can disable OPTION_GROUP items.
     */
    private class SelectPopupArrayAdapter extends ArrayAdapter<String> {
        /**
         * Possible values for mItemEnabled.
         * Keep in sync with the value passed from content_view_core_impl.cc
         */
        final static int POPUP_ITEM_TYPE_GROUP = 0;
        final static int POPUP_ITEM_TYPE_DISABLED = 1;
        final static int POPUP_ITEM_TYPE_ENABLED = 2;

        // Indicates the POPUP_ITEM_TYPE of each item.
        private int[] mItemEnabled;

        // True if all items are POPUP_ITEM_TYPE_ENABLED.
        private boolean mAreAllItemsEnabled;

        public SelectPopupArrayAdapter(String[] labels, int[] enabled, boolean multiple) {
            super(mContentViewCore.getContext(), multiple ?
                  android.R.layout.select_dialog_multichoice :
                  android.R.layout.select_dialog_singlechoice, labels);
            mItemEnabled = enabled;
            mAreAllItemsEnabled = true;
            for (int item : mItemEnabled) {
                if (item != POPUP_ITEM_TYPE_ENABLED) {
                    mAreAllItemsEnabled = false;
                    break;
                }
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position < 0 || position >= getCount()) {
                return null;
            }

            // Always pass in null so that we will get a new CheckedTextView. Otherwise, an item
            // which was previously used as an <optgroup> element (i.e. has no check), could get
            // used as an <option> element, which needs a checkbox/radio, but it would not have
            // one.
            convertView = super.getView(position, null, parent);
            if (mItemEnabled[position] != POPUP_ITEM_TYPE_ENABLED) {
                if (mItemEnabled[position] == POPUP_ITEM_TYPE_GROUP) {
                    // Currently select_dialog_multichoice & select_dialog_multichoice use
                    // CheckedTextViews. If that changes, the class cast will no longer be valid.
                    ((CheckedTextView) convertView).setCheckMarkDrawable(null);
                } else {
                    // Draw the disabled element in a disabled state.
                    convertView.setEnabled(false);
                }
            }
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return mAreAllItemsEnabled;
        }

        @Override
        public boolean isEnabled(int position) {
            if (position < 0 || position >= getCount()) {
                return false;
            }
            return mItemEnabled[position] == POPUP_ITEM_TYPE_ENABLED;
        }
    }

    private SelectPopupDialog(ContentViewCore contentViewCore, String[] labels, int[] enabled,
            boolean multiple, int[] selected) {
        mContentViewCore = contentViewCore;

        final ListView listView = new ListView(mContentViewCore.getContext());
        AlertDialog.Builder b = new AlertDialog.Builder(mContentViewCore.getContext())
                .setView(listView)
                .setCancelable(true)
                .setInverseBackgroundForced(true);

        if (multiple) {
            b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mContentViewCore.selectPopupMenuItems(getSelectedIndices(listView));
                }});
            b.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mContentViewCore.selectPopupMenuItems(null);
            }});
        }
        mListBoxPopup = b.create();
        final SelectPopupArrayAdapter adapter = new SelectPopupArrayAdapter(labels, enabled,
                multiple);
        listView.setAdapter(adapter);
        listView.setFocusableInTouchMode(true);

        if (multiple) {
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            for (int i = 0; i < selected.length; ++i) {
                listView.setItemChecked(selected[i], true);
            }
        } else {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v,
                        int position, long id) {
                    mContentViewCore.selectPopupMenuItems(getSelectedIndices(listView));
                    mListBoxPopup.dismiss();
                }
            });
            if (selected.length > 0) {
                listView.setSelection(selected[0]);
                listView.setItemChecked(selected[0], true);
            }
        }
        mListBoxPopup.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mContentViewCore.selectPopupMenuItems(null);
            }
        });
        mListBoxPopup.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mListBoxPopup = null;
                sShownDialog = null;
            }
        });
    }

    private int[] getSelectedIndices(ListView listView) {
        SparseBooleanArray sparseArray = listView.getCheckedItemPositions();
        int selectedCount = 0;
        for (int i = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                selectedCount++;
            }
        }
        int[] indices = new int[selectedCount];
        for (int i = 0, j = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                indices[j++] = sparseArray.keyAt(i);
            }
        }
        return indices;
    }

    /**
     * Shows the popup menu triggered by the passed ContentView.
     * Hides any currently shown popup.
     * @param items           Items to show.
     * @param enabled         POPUP_ITEM_TYPEs for items.
     * @param multiple        Whether the popup menu should support multi-select.
     * @param selectedIndices Indices of selected items.
     */
    public static void show(ContentViewCore contentViewCore, String[] items, int[] enabled,
            boolean multiple, int[] selectedIndices) {
        // Hide the popup currently showing if any.  This could happen if the user pressed a select
        // and pressed it again before the popup was shown.  In that case, the previous popup is
        // irrelevant and can be hidden.
        hide(null);
        sShownDialog = new SelectPopupDialog(contentViewCore, items, enabled, multiple,
                selectedIndices);
        sShownDialog.mListBoxPopup.show();
    }

    /**
     * Hides the showing popup menu if any it was triggered by the passed ContentView. If
     * contentView is null, hides it regardless of which ContentView triggered it.
     * @param contentView
     */
    public static void hide(ContentViewCore contentView) {
        if (sShownDialog != null &&
                (contentView == null || sShownDialog.mContentViewCore == contentView)) {
            if (contentView != null) contentView.selectPopupMenuItems(null);
            sShownDialog.mListBoxPopup.dismiss();
        }
    }

    // The methods below are used by tests.
    public static SelectPopupDialog getCurrent() {
        return sShownDialog;
    }
}
