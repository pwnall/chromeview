// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;

/**
 * CursorController for inserting text at the cursor position.
 */
public abstract class InsertionHandleController implements CursorController {

    /** The handle view, lazily created when first shown */
    private HandleView mHandle;

    /** The view over which the insertion handle should be shown */
    private View mParent;

    /** True iff the insertion handle is currently showing */
    private boolean mIsShowing;

    /** True iff the insertion handle can be shown automatically when selection changes */
    private boolean mAllowAutomaticShowing;

    private Context mContext;

    public InsertionHandleController(View parent) {
        mParent = parent;
        mContext = parent.getContext();
    }

    /** Allows the handle to be shown automatically when cursor position changes */
    public void allowAutomaticShowing() {
        mAllowAutomaticShowing = true;
    }

    /** Disallows the handle from being shown automatically when cursor position changes */
    public void hideAndDisallowAutomaticShowing() {
        hide();
        mAllowAutomaticShowing = false;
    }

    /**
     * Shows the handle.
     */
    public void showHandle() {
        createHandleIfNeeded();
        showHandleIfNeeded();
    }

    void showPastePopup() {
        if (mIsShowing) {
            mHandle.showPastePopupWindow();
        }
    }

    public void showHandleWithPastePopup() {
        showHandle();
        showPastePopup();
    }

    /** Shows the handle at the given coordinates, as long as automatic showing is allowed */
    public void onCursorPositionChanged() {
        if (mAllowAutomaticShowing) {
            showHandle();
        }
    }

    /**
     * Moves the handle so that it points at the given coordinates.
     * @param x Handle x in physical pixels.
     * @param y Handle y in physical pixels.
     */
    public void setHandlePosition(float x, float y) {
        mHandle.positionAt((int) x, (int) y);
    }

    /**
     * If the handle is not visible, sets its visibility to View.VISIBLE and begins fading it in.
     */
    public void beginHandleFadeIn() {
        mHandle.beginFadeIn();
    }

    /**
     * Sets the handle to the given visibility.
     */
    public void setHandleVisibility(int visibility) {
        mHandle.setVisibility(visibility);
    }

    int getHandleX() {
        return mHandle.getAdjustedPositionX();
    }

    int getHandleY() {
        return mHandle.getAdjustedPositionY();
    }

    public HandleView getHandleViewForTest() {
        return mHandle;
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
        if (!isInTouchMode) {
            hide();
        }
    }

    @Override
    public void hide() {
        if (mIsShowing) {
            if (mHandle != null) mHandle.hide();
            mIsShowing = false;
        }
    }

    @Override
    public boolean isShowing() {
        return mIsShowing;
    }

    @Override
    public void beforeStartUpdatingPosition(HandleView handle) {}

    @Override
    public void updatePosition(HandleView handle, int x, int y) {
        setCursorPosition(x, y);
    }

    /**
     * The concrete implementation must cause the cursor position to move to the given
     * coordinates and (possibly asynchronously) set the insertion handle position
     * after the cursor position change is made via setHandlePosition.
     * @param x
     * @param y
     */
    protected abstract void setCursorPosition(int x, int y);

    /** Pastes the contents of clipboard at the current insertion point */
    protected abstract void paste();

    /** Returns the current line height in pixels */
    protected abstract int getLineHeight();

    @Override
    public void onDetached() {}

    boolean canPaste() {
        return ((ClipboardManager)mContext.getSystemService(
                Context.CLIPBOARD_SERVICE)).hasPrimaryClip();
    }

    private void createHandleIfNeeded() {
        if (mHandle == null) mHandle = new HandleView(this, HandleView.CENTER, mParent);
    }

    private void showHandleIfNeeded() {
        if (!mIsShowing) {
            mIsShowing = true;
            mHandle.show();
            setHandleVisibility(HandleView.VISIBLE);
        }
    }

    /*
     * This class is based on TextView.PastePopupMenu.
     */
    class PastePopupMenu implements OnClickListener {
        private final PopupWindow mContainer;
        private int mPositionX;
        private int mPositionY;
        private View[] mPasteViews;
        private int[] mPasteViewLayouts;

        public PastePopupMenu() {
            mContainer = new PopupWindow(mContext, null,
                    android.R.attr.textSelectHandleWindowStyle);
            mContainer.setSplitTouchEnabled(true);
            mContainer.setClippingEnabled(false);

            mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            final int[] POPUP_LAYOUT_ATTRS = {
                android.R.attr.textEditPasteWindowLayout,
                android.R.attr.textEditNoPasteWindowLayout,
                android.R.attr.textEditSidePasteWindowLayout,
                android.R.attr.textEditSideNoPasteWindowLayout,
            };

            mPasteViews = new View[POPUP_LAYOUT_ATTRS.length];
            mPasteViewLayouts = new int[POPUP_LAYOUT_ATTRS.length];

            TypedArray attrs = mContext.obtainStyledAttributes(POPUP_LAYOUT_ATTRS);
            for (int i = 0; i < attrs.length(); ++i) {
                mPasteViewLayouts[i] = attrs.getResourceId(attrs.getIndex(i), 0);
            }
            attrs.recycle();
        }

        private int viewIndex(boolean onTop) {
            return (onTop ? 0 : 1<<1) + (canPaste() ? 0 : 1 << 0);
        }

        private void updateContent(boolean onTop) {
            final int viewIndex = viewIndex(onTop);
            View view = mPasteViews[viewIndex];

            if (view == null) {
                final int layout = mPasteViewLayouts[viewIndex];
                LayoutInflater inflater = (LayoutInflater)mContext.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (inflater != null) {
                    view = inflater.inflate(layout, null);
                }

                if (view == null) {
                    throw new IllegalArgumentException("Unable to inflate TextEdit paste window");
                }

                final int size = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                view.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                view.measure(size, size);

                view.setOnClickListener(this);

                mPasteViews[viewIndex] = view;
            }

            mContainer.setContentView(view);
        }

        void show() {
            updateContent(true);
            positionAtCursor();
        }

        void hide() {
            mContainer.dismiss();
        }

        boolean isShowing() {
            return mContainer.isShowing();
        }

        @Override
        public void onClick(View v) {
            if (canPaste()) {
                paste();
            }
            hide();
        }

        void positionAtCursor() {
            View contentView = mContainer.getContentView();
            int width = contentView.getMeasuredWidth();
            int height = contentView.getMeasuredHeight();

            int lineHeight = getLineHeight();

            mPositionX = (int) (mHandle.getAdjustedPositionX() - width / 2.0f);
            mPositionY = mHandle.getAdjustedPositionY() - height - lineHeight;

            final int[] coords = new int[2];
            mParent.getLocationInWindow(coords);
            coords[0] += mPositionX;
            coords[1] += mPositionY;

            final int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
            if (coords[1] < 0) {
                updateContent(false);
                // Update dimensions from new view
                contentView = mContainer.getContentView();
                width = contentView.getMeasuredWidth();
                height = contentView.getMeasuredHeight();

                // Vertical clipping, move under edited line and to the side of insertion cursor
                // TODO bottom clipping in case there is no system bar
                coords[1] += height;
                coords[1] += lineHeight;

                // Move to right hand side of insertion cursor by default. TODO RTL text.
                final Drawable handle = mHandle.getDrawable();
                final int handleHalfWidth = handle.getIntrinsicWidth() / 2;

                if (mHandle.getAdjustedPositionX() + width < screenWidth) {
                    coords[0] += handleHalfWidth + width / 2;
                } else {
                    coords[0] -= handleHalfWidth + width / 2;
                }
            } else {
                // Horizontal clipping
                coords[0] = Math.max(0, coords[0]);
                coords[0] = Math.min(screenWidth - width, coords[0]);
            }

            mContainer.showAtLocation(mParent, Gravity.NO_GRAVITY, coords[0], coords[1]);
        }
    }
}
