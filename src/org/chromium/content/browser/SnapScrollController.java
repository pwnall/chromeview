// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;

/**
 * This objects controls the scroll snapping behavior based on scroll updates.
 */
class SnapScrollController {
    private static final String TAG = SnapScrollController.class.toString();
    private static final int SNAP_NONE = 0;
    private static final int SNAP_HORIZ = 1;
    private static final int SNAP_VERT = 2;
    private static final int SNAP_BOUND = 16;

    private float mChannelDistance = 16f;
    private int mSnapScrollMode = SNAP_NONE;
    private int mFirstTouchX = -1;
    private int mFirstTouchY = -1;
    private float mDistanceX = 0;
    private float mDistanceY = 0;
    private ZoomManager mZoomManager;

    SnapScrollController(Context context, ZoomManager zoomManager) {
        calculateChannelDistance(context);
        mZoomManager = zoomManager;
    }

    /**
     * Updates the snap scroll mode based on the given X and Y distance to be moved on scroll.
     * If the scroll update is above a threshold, the snapping behavior is reset.
     * @param distanceX X distance for the current scroll update.
     * @param distanceY Y distance for the current scroll update.
     */
    void updateSnapScrollMode(float distanceX, float distanceY) {
        if (mSnapScrollMode == SNAP_HORIZ || mSnapScrollMode == SNAP_VERT) {
            mDistanceX += Math.abs(distanceX);
            mDistanceY += Math.abs(distanceY);
            if (mSnapScrollMode == SNAP_HORIZ) {
                if (mDistanceY > mChannelDistance) {
                    mSnapScrollMode = SNAP_NONE;
                } else if (mDistanceX > mChannelDistance) {
                    mDistanceX = 0;
                    mDistanceY = 0;
                }
            } else {
                if (mDistanceX > mChannelDistance) {
                    mSnapScrollMode = SNAP_NONE;
                } else if (mDistanceY > mChannelDistance) {
                    mDistanceX = 0;
                    mDistanceY = 0;
                }
            }
        }
    }

    /**
     * Sets the snap scroll mode based on the event type.
     * @param event The received MotionEvent.
     */
    void setSnapScrollingMode(MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mSnapScrollMode = SNAP_NONE;
                mFirstTouchX = (int) event.getX();
                mFirstTouchY = (int) event.getY();
                break;
             // Set scrolling mode to SNAP_X if scroll towards x-axis exceeds SNAP_BOUND
             // and movement towards y-axis is trivial.
             // Set scrolling mode to SNAP_Y if scroll towards y-axis exceeds SNAP_BOUND
             // and movement towards x-axis is trivial.
             // Scrolling mode will remain in SNAP_NONE for other conditions.
            case MotionEvent.ACTION_MOVE:
                if (!mZoomManager.isScaleGestureDetectionInProgress() &&
                        mSnapScrollMode == SNAP_NONE) {
                    int xDiff = (int) Math.abs(event.getX() - mFirstTouchX);
                    int yDiff = (int) Math.abs(event.getY() - mFirstTouchY);
                    if (xDiff > SNAP_BOUND && yDiff < SNAP_BOUND) {
                        mSnapScrollMode = SNAP_HORIZ;
                    } else if (xDiff < SNAP_BOUND && yDiff > SNAP_BOUND) {
                        mSnapScrollMode = SNAP_VERT;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mFirstTouchX = -1;
                mFirstTouchY = -1;
                mDistanceX = 0;
                mDistanceY = 0;
                break;
            default:
                Log.i(TAG, "setSnapScrollingMode case-default no-op");
                break;
        }
    }

    private void calculateChannelDistance(Context context) {
        // The channel distance is adjusted for density and screen size.
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final double screenSize = Math.hypot((double) metrics.widthPixels / metrics.densityDpi,
                (double) metrics.heightPixels / metrics.densityDpi);
        if (screenSize < 3.0) {
            mChannelDistance = 16f;
        } else if (screenSize < 5.0) {
            mChannelDistance = 22f;
        } else if (screenSize < 7.0) {
            mChannelDistance = 28f;
        } else {
            mChannelDistance = 34f;
        }
        mChannelDistance = mChannelDistance * metrics.density;
        if (mChannelDistance < 16f) mChannelDistance = 16f;
    }

    /**
     * Resets the snap scroll mode to default.
     */
    void resetSnapScrollMode() {
        mSnapScrollMode = SNAP_NONE;
    }

    /**
     * @return Whether current snap scroll mode is vertical.
     */
    boolean isSnapVertical() {
        return mSnapScrollMode == SNAP_VERT;
    }

    /**
     * @return Whether current snap scroll mode is horizontal.
     */
    boolean isSnapHorizontal() {
        return mSnapScrollMode == SNAP_HORIZ;
    }

    /**
     * @return Whether currently snapping scrolls.
     */
    boolean isSnappingScrolls() {
        return mSnapScrollMode != SNAP_NONE;
    }
}
