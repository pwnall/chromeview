// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * ZoomManager is responsible for maintaining the ContentView's current zoom
 * level state and process scaling-related gestures.
 */
class ZoomManager {
    private static final String TAG = "ContentViewZoom";

    private ContentViewCore mContentViewCore;

    private class ScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
        // Completely silence scaling events. Used in WebView when zoom support
        // is turned off.
        private boolean mPermanentlyIgnoreDetectorEvents = false;
        // Bypass events through the detector to maintain its state. Used when
        // renderes already handles the touch event.
        private boolean mTemporarilyIgnoreDetectorEvents = false;

        // Whether any pinch zoom event has been sent to native.
        private boolean mPinchEventSent;

        boolean getPermanentlyIgnoreDetectorEvents() {
            return mPermanentlyIgnoreDetectorEvents;
        }

        void setPermanentlyIgnoreDetectorEvents(boolean value) {
            // Note that returning false from onScaleBegin / onScale makes the
            // gesture detector not to emit further scaling notifications
            // related to this gesture. Thus, if detector events are enabled in
            // the middle of the gesture, we don't need to do anything.
            mPermanentlyIgnoreDetectorEvents = value;
        }

        void setTemporarilyIgnoreDetectorEvents(boolean value) {
            mTemporarilyIgnoreDetectorEvents = value;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (ignoreDetectorEvents()) return false;
            mPinchEventSent = false;
            mContentViewCore.getContentViewGestureHandler().setIgnoreSingleTap(true);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (!mPinchEventSent || !mContentViewCore.isAlive()) return;
            mContentViewCore.getContentViewGestureHandler().pinchEnd(detector.getEventTime());
            mPinchEventSent = false;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (ignoreDetectorEvents()) return false;
            // It is possible that pinchBegin() was never called when we reach here.
            // This happens when webkit handles the 2nd touch down event. That causes
            // ContentView to ignore the onScaleBegin() call. And if webkit does not
            // handle the touch move events afterwards, we will face a situation
            // that pinchBy() is called without any pinchBegin().
            // To solve this problem, we call pinchBegin() here if it is never called.
            if (!mPinchEventSent) {
                mContentViewCore.getContentViewGestureHandler().pinchBegin(detector.getEventTime(),
                        (int) detector.getFocusX(), (int) detector.getFocusY());
                mPinchEventSent = true;
            }
            mContentViewCore.getContentViewGestureHandler().pinchBy(
                    detector.getEventTime(), (int) detector.getFocusX(), (int) detector.getFocusY(),
                    detector.getScaleFactor());
            return true;
        }

        private boolean ignoreDetectorEvents() {
            return mPermanentlyIgnoreDetectorEvents ||
                    mTemporarilyIgnoreDetectorEvents ||
                    !mContentViewCore.isAlive();
        }
    }

    private ScaleGestureDetector mMultiTouchDetector;
    private ScaleGestureListener mMultiTouchListener;

    ZoomManager(final Context context, ContentViewCore contentViewCore) {
        mContentViewCore = contentViewCore;
        mMultiTouchListener = new ScaleGestureListener();
        mMultiTouchDetector = new ScaleGestureDetector(context, mMultiTouchListener);
    }

    boolean isScaleGestureDetectionInProgress() {
        return !mMultiTouchListener.getPermanentlyIgnoreDetectorEvents()
                && mMultiTouchDetector.isInProgress();
    }

    // Passes the touch event to ScaleGestureDetector so that its internal
    // state won't go wrong, but instructs the listener to ignore the result
    // of processing, if any.
    void passTouchEventThrough(MotionEvent event) {
        mMultiTouchListener.setTemporarilyIgnoreDetectorEvents(true);
        try {
            mMultiTouchDetector.onTouchEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "ScaleGestureDetector got into a bad state!", e);
            assert(false);
        }
    }

    // Passes the touch event to ScaleGestureDetector so that its internal state
    // won't go wrong. ScaleGestureDetector needs two pointers in a MotionEvent
    // to recognize a zoom gesture.
    boolean processTouchEvent(MotionEvent event) {
        // TODO: Need to deal with multi-touch transition
        mMultiTouchListener.setTemporarilyIgnoreDetectorEvents(false);
        try {
            boolean inGesture = isScaleGestureDetectionInProgress();
            boolean retVal = mMultiTouchDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !inGesture) return false;
            return retVal;
        } catch (Exception e) {
            Log.e(TAG, "ScaleGestureDetector got into a bad state!", e);
            assert(false);
        }
        return false;
    }

    void updateMultiTouchSupport(boolean supportsMultiTouchZoom) {
        mMultiTouchListener.setPermanentlyIgnoreDetectorEvents(!supportsMultiTouchZoom);
    }
}
