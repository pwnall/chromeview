// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * View that displays a selection or insertion handle for text editing.
 */
public class HandleView extends View {
    private static final float FADE_DURATION = 200.f;

    private Drawable mDrawable;
    private final PopupWindow mContainer;
    private int mPositionX;
    private int mPositionY;
    private final CursorController mController;
    private boolean mIsDragging;
    private float mTouchToWindowOffsetX;
    private float mTouchToWindowOffsetY;
    private float mHotspotX;
    private float mHotspotY;
    private int mLineOffsetY;
    private int mLastParentX;
    private int mLastParentY;
    private float mDownPositionX, mDownPositionY;
    private int mContainerPositionX, mContainerPositionY;
    private long mTouchTimer;
    private boolean mIsInsertionHandle = false;
    private float mAlpha;
    private long mFadeStartTime;

    private View mParent;
    private InsertionHandleController.PastePopupMenu mPastePopupWindow;

    private final int mTextSelectHandleLeftRes;
    private final int mTextSelectHandleRightRes;
    private final int mTextSelectHandleRes;

    private Drawable mSelectHandleLeft;
    private Drawable mSelectHandleRight;
    private Drawable mSelectHandleCenter;

    private final int[] mTempCoords = new int[2];
    private final Rect mTempRect = new Rect();

    static final int LEFT = 0;
    static final int CENTER = 1;
    static final int RIGHT = 2;

    // Number of dips to subtract from the handle's y position to give a suitable
    // y coordinate for the corresponding text position. This is to compensate for the fact
    // that the handle position is at the base of the line of text.
    private static final float LINE_OFFSET_Y_DIP = 5.0f;

    private static final int[] TEXT_VIEW_HANDLE_ATTRS = {
        android.R.attr.textSelectHandleLeft,
        android.R.attr.textSelectHandle,
        android.R.attr.textSelectHandleRight,
    };

    HandleView(CursorController controller, int pos, View parent) {
        super(parent.getContext());
        Context context = parent.getContext();
        mParent = parent;
        mController = controller;
        mContainer = new PopupWindow(context, null, android.R.attr.textSelectHandleWindowStyle);
        mContainer.setSplitTouchEnabled(true);
        mContainer.setClippingEnabled(false);

        TypedArray a = context.obtainStyledAttributes(TEXT_VIEW_HANDLE_ATTRS);
        mTextSelectHandleLeftRes = a.getResourceId(a.getIndex(LEFT), 0);
        mTextSelectHandleRes = a.getResourceId(a.getIndex(CENTER), 0);
        mTextSelectHandleRightRes = a.getResourceId(a.getIndex(RIGHT), 0);
        a.recycle();

        setOrientation(pos);

        // Convert line offset dips to pixels.
        mLineOffsetY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LINE_OFFSET_Y_DIP, context.getResources().getDisplayMetrics());

        mAlpha = 1.f;
    }

    void setOrientation(int pos) {
        int handleWidth;
        switch (pos) {
        case LEFT: {
            if (mSelectHandleLeft == null) {
                mSelectHandleLeft = getContext().getResources().getDrawable(
                        mTextSelectHandleLeftRes);
            }
            mDrawable = mSelectHandleLeft;
            handleWidth = mDrawable.getIntrinsicWidth();
            mHotspotX = (handleWidth * 3) / 4f;
            break;
        }

        case RIGHT: {
            if (mSelectHandleRight == null) {
                mSelectHandleRight = getContext().getResources().getDrawable(
                        mTextSelectHandleRightRes);
            }
            mDrawable = mSelectHandleRight;
            handleWidth = mDrawable.getIntrinsicWidth();
            mHotspotX = handleWidth / 4f;
            break;
        }

        case CENTER:
        default: {
            if (mSelectHandleCenter == null) {
                mSelectHandleCenter = getContext().getResources().getDrawable(
                        mTextSelectHandleRes);
            }
            mDrawable = mSelectHandleCenter;
            handleWidth = mDrawable.getIntrinsicWidth();
            mHotspotX = handleWidth / 2f;
            mIsInsertionHandle = true;
            break;
        }
        }

        mHotspotY = 0;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mDrawable.getIntrinsicWidth(),
                mDrawable.getIntrinsicHeight());
    }

    void show() {
        if (!isPositionVisible()) {
            hide();
            return;
        }
        mContainer.setContentView(this);
        final int[] coords = mTempCoords;
        mParent.getLocationInWindow(coords);
        mContainerPositionX = coords[0] + mPositionX;
        mContainerPositionY = coords[1] + mPositionY;
        mContainer.showAtLocation(mParent, 0, mContainerPositionX, mContainerPositionY);

        // Hide paste view when handle is moved on screen.
        if (mPastePopupWindow != null) {
            mPastePopupWindow.hide();
        }
    }

    void hide() {
        mIsDragging = false;
        mContainer.dismiss();
        if (mPastePopupWindow != null) {
            mPastePopupWindow.hide();
        }
    }

    boolean isShowing() {
        return mContainer.isShowing();
    }

    private boolean isPositionVisible() {
        // Always show a dragging handle.
        if (mIsDragging) {
            return true;
        }

        final Rect clip = mTempRect;
        clip.left = 0;
        clip.top = 0;
        clip.right = mParent.getWidth();
        clip.bottom = mParent.getHeight();

        final ViewParent parent = mParent.getParent();
        if (parent == null || !parent.getChildVisibleRect(mParent, clip, null)) {
            return false;
        }

        final int[] coords = mTempCoords;
        mParent.getLocationInWindow(coords);
        final int posX = coords[0] + mPositionX + (int) mHotspotX;
        final int posY = coords[1] + mPositionY + (int) mHotspotY;

        return posX >= clip.left && posX <= clip.right &&
                posY >= clip.top && posY <= clip.bottom;
    }

    // x and y are in physical pixels.
    void moveTo(int x, int y) {
        mPositionX = x;
        mPositionY = y;
        if (isPositionVisible()) {
            int[] coords = null;
            if (mContainer.isShowing()) {
                coords = mTempCoords;
                mParent.getLocationInWindow(coords);
                final int containerPositionX = coords[0] + mPositionX;
                final int containerPositionY = coords[1] + mPositionY;

                if (containerPositionX != mContainerPositionX ||
                    containerPositionY != mContainerPositionY) {
                    mContainerPositionX = containerPositionX;
                    mContainerPositionY = containerPositionY;

                    mContainer.update(mContainerPositionX, mContainerPositionY,
                            getRight() - getLeft(), getBottom() - getTop());

                    // Hide paste popup window as soon as a scroll occurs.
                    if (mPastePopupWindow != null) {
                        mPastePopupWindow.hide();
                    }
                }
            } else {
                show();
            }

            if (mIsDragging) {
                if (coords == null) {
                    coords = mTempCoords;
                    mParent.getLocationInWindow(coords);
                }
                if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                    mTouchToWindowOffsetX += coords[0] - mLastParentX;
                    mTouchToWindowOffsetY += coords[1] - mLastParentY;
                    mLastParentX = coords[0];
                    mLastParentY = coords[1];
                }
                // Hide paste popup window as soon as the handle is dragged.
                if (mPastePopupWindow != null) {
                    mPastePopupWindow.hide();
                }
            }
        } else {
            hide();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        updateAlpha();
        mDrawable.setBounds(0, 0, getRight() - getLeft(), getBottom() - getTop());
        mDrawable.draw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownPositionX = ev.getRawX();
                mDownPositionY = ev.getRawY();
                mTouchToWindowOffsetX = mDownPositionX - mPositionX;
                mTouchToWindowOffsetY = mDownPositionY - mPositionY;
                final int[] coords = mTempCoords;
                mParent.getLocationInWindow(coords);
                mLastParentX = coords[0];
                mLastParentY = coords[1];
                mIsDragging = true;
                mController.beforeStartUpdatingPosition(this);
                mTouchTimer = SystemClock.uptimeMillis();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                updatePosition(ev.getRawX(), ev.getRawY());
                break;
            }

            case MotionEvent.ACTION_UP:
                if (mIsInsertionHandle) {
                    long delay = SystemClock.uptimeMillis() - mTouchTimer;
                    if (delay < ViewConfiguration.getTapTimeout()) {
                        if (mPastePopupWindow != null && mPastePopupWindow.isShowing()) {
                            // Tapping on the handle dismisses the displayed paste view,
                            mPastePopupWindow.hide();
                        } else {
                            showPastePopupWindow();
                        }
                    }
                }
                mIsDragging = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                break;

            default:
                return false;
        }
        return true;
    }

    boolean isDragging() {
        return mIsDragging;
    }

    /**
     * @return Returns the x position of the handle
     */
    int getPositionX() {
        return mPositionX;
    }

    /**
     * @return Returns the y position of the handle
     */
    int getPositionY() {
        return mPositionY;
    }

    private void updatePosition(float rawX, float rawY) {
        final float newPosX = rawX - mTouchToWindowOffsetX + mHotspotX;
        final float newPosY = rawY - mTouchToWindowOffsetY + mHotspotY - mLineOffsetY;

        mController.updatePosition(this, Math.round(newPosX), Math.round(newPosY));
    }

    // x and y are in physical pixels.
    void positionAt(int x, int y) {
        moveTo((int)(x - mHotspotX), (int)(y - mHotspotY));
    }

    // Returns the x coordinate of the position that the handle appears to be pointing to.
    int getAdjustedPositionX() {
        return (int) (mPositionX + mHotspotX);
    }

    // Returns the y coordinate of the position that the handle appears to be pointing to.
    int getAdjustedPositionY() {
        return (int) (mPositionY + mHotspotY);
    }

    // Returns a suitable y coordinate for the text position corresponding to the handle.
    // As the handle points to a position on the base of the line of text, this method
    // returns a coordinate a small number of pixels higher (i.e. a slightly smaller number)
    // than getAdjustedPositionY.
    int getLineAdjustedPositionY() {
        return (int) (mPositionY + mHotspotY - mLineOffsetY);
    }

    Drawable getDrawable() {
        return mDrawable;
    }

    private void updateAlpha() {
        if (mAlpha == 1.f) return;
        mAlpha = Math.min(1.f, (System.currentTimeMillis() - mFadeStartTime) / FADE_DURATION);
        mDrawable.setAlpha((int) (255 * mAlpha));
        invalidate();
    }

    /**
     * If the handle is not visible, sets its visibility to View.VISIBLE and begins fading it in.
     */
    void beginFadeIn() {
        if (getVisibility() == VISIBLE) return;
        mAlpha = 0.f;
        mFadeStartTime = System.currentTimeMillis();
        setVisibility(VISIBLE);
    }

    void showPastePopupWindow() {
        InsertionHandleController ihc = (InsertionHandleController) mController;
        if (mIsInsertionHandle && ihc.canPaste()) {
            if (mPastePopupWindow == null) {
                // Lazy initialization: create when actually shown only.
                mPastePopupWindow = ihc.new PastePopupMenu();
            }
            mPastePopupWindow.show();
        }
    }
}
