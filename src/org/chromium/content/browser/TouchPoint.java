// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.util.Log;
import android.view.MotionEvent;

import org.chromium.base.CalledByNative;

// This class converts android MotionEvent into an array of touch points so
// that they can be forwarded to the renderer process.
class TouchPoint {

    public static final int CONVERSION_ERROR = -1;

    // Type of motion event to send to the native side. The values originate from their
    // webkit WebInputEvent counterparts, and are set via initializeConstants().
    static int TOUCH_EVENT_TYPE_START;
    static int TOUCH_EVENT_TYPE_MOVE;
    static int TOUCH_EVENT_TYPE_END;
    static int TOUCH_EVENT_TYPE_CANCEL;

    // Type of motion event to send to the native side. The values originate from their
    // webkit WebTouchPoint counterparts, and are set via initializeConstants().
    private static int TOUCH_POINT_STATE_UNDEFINED;
    private static int TOUCH_POINT_STATE_RELEASED;
    private static int TOUCH_POINT_STATE_PRESSED;
    private static int TOUCH_POINT_STATE_MOVED;
    private static int TOUCH_POINT_STATE_STATIONARY;
    private static int TOUCH_POINT_STATE_CANCELLED;

    private final int mState;
    private final int mId;
    private final float mX;
    private final float mY;
    private final float mSize;
    private final float mPressure;

    TouchPoint(int state, int id, float x, float y, float size, float pressure) {
        mState = state;
        mId = id;
        mX = x;
        mY = y;
        mSize = size;
        mPressure = pressure;
    }

    // The following methods are called by native to parse the java TouchPoint
    // object it has received.
    @SuppressWarnings("unused")
    @CalledByNative
    public int getState() { return mState; }

    @SuppressWarnings("unused")
    @CalledByNative
    public int getId() { return mId; }

    @SuppressWarnings("unused")
    @CalledByNative
    public int getX() { return (int) mX; }

    @SuppressWarnings("unused")
    @CalledByNative
    public int getY() { return (int) mY; }

    @SuppressWarnings("unused")
    @CalledByNative
    public double getSize() { return mSize; }

    @SuppressWarnings("unused")
    @CalledByNative
    public double getPressure() { return mPressure; }

    // Converts a MotionEvent into an array of touch points.
    // Returns the WebTouchEvent::Type for the MotionEvent and -1 for failure.
    public static int createTouchPoints(MotionEvent event, TouchPoint[] pts) {
        int type;
        int defaultState;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                type = TOUCH_EVENT_TYPE_START;
                defaultState = TOUCH_POINT_STATE_PRESSED;
                break;
            case MotionEvent.ACTION_MOVE:
                type = TOUCH_EVENT_TYPE_MOVE;
                defaultState = TOUCH_POINT_STATE_MOVED;
                break;
            case MotionEvent.ACTION_UP:
                type = TOUCH_EVENT_TYPE_END;
                defaultState = TOUCH_POINT_STATE_RELEASED;
                break;
            case MotionEvent.ACTION_CANCEL:
                type = TOUCH_EVENT_TYPE_CANCEL;
                defaultState = TOUCH_POINT_STATE_CANCELLED;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:  // fall through.
            case MotionEvent.ACTION_POINTER_UP:
                type = TOUCH_EVENT_TYPE_MOVE;
                defaultState = TOUCH_POINT_STATE_STATIONARY;
                break;
            default:
                Log.e("Chromium", "Unknown motion event action: " + event.getActionMasked());
                return CONVERSION_ERROR;
        }

        for (int i = 0; i < pts.length; ++i) {
            int state = defaultState;
            if (defaultState == TOUCH_POINT_STATE_STATIONARY && event.getActionIndex() == i) {
                // An additional pointer has started or ended. Map this pointer state as
                // required, and all other pointers as "stationary".
                state = event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN ?
                    TOUCH_POINT_STATE_PRESSED : TOUCH_POINT_STATE_RELEASED;
            }
            pts[i] = new TouchPoint(state, event.getPointerId(i),
                                    event.getX(i), event.getY(i),
                                    event.getSize(i), event.getPressure(i));
        }

        return type;
    }

    // This method is called by native to initialize all the constants from
    // their counterparts in WebInputEvent and WebTouchPoint.
    @SuppressWarnings("unused")
    @CalledByNative
    private static void initializeConstants(
            int touchTypeStart, int touchTypeMove, int touchTypeEnd, int touchTypeCancel,
            int touchPointUndefined, int touchPointReleased, int touchPointPressed,
            int touchPointMoved, int touchPointStationary, int touchPointCancelled) {
        TOUCH_EVENT_TYPE_START = touchTypeStart;
        TOUCH_EVENT_TYPE_MOVE = touchTypeMove;
        TOUCH_EVENT_TYPE_END = touchTypeEnd;
        TOUCH_EVENT_TYPE_CANCEL = touchTypeCancel;
        TOUCH_POINT_STATE_UNDEFINED = touchPointUndefined;
        TOUCH_POINT_STATE_RELEASED = touchPointReleased;
        TOUCH_POINT_STATE_PRESSED = touchPointPressed;
        TOUCH_POINT_STATE_MOVED = touchPointMoved;
        TOUCH_POINT_STATE_STATIONARY = touchPointStationary;
        TOUCH_POINT_STATE_CANCELLED = touchPointCancelled;
    }
}
