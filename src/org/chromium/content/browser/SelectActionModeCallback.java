// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.SearchManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.provider.Browser;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import org.chromium.content.R;

/**
 * An ActionMode.Callback for in-page selection. This class handles both the editable and
 * non-editable cases.
 */
public class SelectActionModeCallback implements ActionMode.Callback {
    private static final int SELECT_ALL_ATTR_INDEX = 0;
    private static final int CUT_ATTR_INDEX = 1;
    private static final int COPY_ATTR_INDEX = 2;
    private static final int PASTE_ATTR_INDEX = 3;
    private static final int[] ACTION_MODE_ATTRS = {
        android.R.attr.actionModeSelectAllDrawable,
        android.R.attr.actionModeCutDrawable,
        android.R.attr.actionModeCopyDrawable,
        android.R.attr.actionModePasteDrawable,
    };

    private static final int ID_SELECTALL = 0;
    private static final int ID_COPY = 1;
    private static final int ID_SHARE = 2;
    private static final int ID_SEARCH = 3;
    private static final int ID_CUT = 4;
    private static final int ID_PASTE = 5;

    /**
     * An interface to retrieve information about the current selection, and also to perform
     * actions based on the selection or when the action bar is dismissed.
     */
    public interface ActionHandler {
        /**
         * Perform a select all action.
         * @return true iff the action was successful.
         */
        boolean selectAll();

        /**
         * Perform a copy (to clipboard) action.
         * @return true iff the action was successful.
         */
        boolean copy();

        /**
         * Perform a cut (to clipboard) action.
         * @return true iff the action was successful.
         */
        boolean cut();

        /**
         * Perform a paste action.
         * @return true iff the action was successful.
         */
        boolean paste();

        /**
         * @return true iff the current selection is editable (e.g. text within an input field).
         */
        boolean isSelectionEditable();

        /**
         * @return the currently selected text String.
         */
        String getSelectedText();

        /**
         * Called when the onDestroyActionMode of the SelectActionmodeCallback is called.
         */
        void onDestroyActionMode();
    }

    private Context mContext;
    private ActionHandler mActionHandler;
    private final boolean mIncognito;
    private boolean mEditable;

    protected SelectActionModeCallback(
            Context context, ActionHandler actionHandler, boolean incognito) {
        mContext = context;
        mActionHandler = actionHandler;
        mIncognito = incognito;
    }

    protected Context getContext() {
        return mContext;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setSubtitle(null);
        mEditable = mActionHandler.isSelectionEditable();
        createActionMenu(mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean isEditableNow = mActionHandler.isSelectionEditable();
        if (mEditable != isEditableNow) {
            mEditable = isEditableNow;
            menu.clear();
            createActionMenu(mode, menu);
            return true;
        }
        return false;
    }

    private void createActionMenu(ActionMode mode, Menu menu) {
        TypedArray styledAttributes = getContext().obtainStyledAttributes(ACTION_MODE_ATTRS);

        menu.add(Menu.NONE, ID_SELECTALL, Menu.NONE, android.R.string.selectAll).
            setAlphabeticShortcut('a').
            setIcon(styledAttributes.getResourceId(SELECT_ALL_ATTR_INDEX, 0)).
            setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        if (mEditable) {
            menu.add(Menu.NONE, ID_CUT, Menu.NONE, android.R.string.cut).
            setIcon(styledAttributes.getResourceId(CUT_ATTR_INDEX, 0)).
            setAlphabeticShortcut('x').
            setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        menu.add(Menu.NONE, ID_COPY, Menu.NONE, android.R.string.copy).
            setIcon(styledAttributes.getResourceId(COPY_ATTR_INDEX, 0)).
            setAlphabeticShortcut('c').
            setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        if (mEditable && canPaste()) {
            menu.add(Menu.NONE, ID_PASTE, Menu.NONE, android.R.string.paste).
                setIcon(styledAttributes.getResourceId(PASTE_ATTR_INDEX, 0)).
                setAlphabeticShortcut('v').
                setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        if (!mEditable) {
            if (isShareHandlerAvailable()) {
                menu.add(Menu.NONE, ID_SHARE, Menu.NONE, R.string.actionbar_share).
                    setIcon(R.drawable.ic_menu_share_holo_light).
                    setShowAsAction(
                            MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }

            if (!mIncognito && isWebSearchAvailable()) {
                menu.add(Menu.NONE, ID_SEARCH, Menu.NONE, R.string.actionbar_web_search).
                    setIcon(R.drawable.ic_menu_search_holo_light).
                    setShowAsAction(
                            MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }
        }

        styledAttributes.recycle();
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        String selection = mActionHandler.getSelectedText();
        switch(item.getItemId()) {
            case ID_SELECTALL:
                mActionHandler.selectAll();
                break;
            case ID_CUT:
                mActionHandler.cut();
                break;
            case ID_COPY:
                mActionHandler.copy();
                mode.finish();
                break;
            case ID_PASTE:
                mActionHandler.paste();
                break;
            case ID_SHARE:
                if (!TextUtils.isEmpty(selection)) {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, selection);
                    try {
                        Intent i = Intent.createChooser(send, getContext().getString(
                                R.string.actionbar_share));
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(i);
                    } catch (android.content.ActivityNotFoundException ex) {
                        // If no app handles it, do nothing.
                    }
                }
                mode.finish();
                break;
            case ID_SEARCH:
                if (!TextUtils.isEmpty(selection)) {
                    Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
                    i.putExtra(SearchManager.EXTRA_NEW_SEARCH, true);
                    i.putExtra(SearchManager.QUERY, selection);
                    i.putExtra(Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                    try {
                        getContext().startActivity(i);
                    } catch (android.content.ActivityNotFoundException ex) {
                        // If no app handles it, do nothing.
                    }
                }
                mode.finish();
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionHandler.onDestroyActionMode();
    }

    private boolean canPaste() {
        ClipboardManager clipMgr = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        return clipMgr.hasPrimaryClip();
    }

    private boolean isShareHandlerAvailable() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        return getContext().getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    private boolean isWebSearchAvailable() {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.EXTRA_NEW_SEARCH, true);
        return getContext().getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }
}
