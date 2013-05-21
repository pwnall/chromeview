// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.chromium.content.browser.third_party.GestureDetector;
import org.chromium.content.browser.third_party.GestureDetector.OnGestureListener;
import org.chromium.content.browser.LongPressDetector.LongPressDelegate;
import org.chromium.content.browser.SnapScrollController;
import org.chromium.content.common.TraceEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * This class handles all MotionEvent handling done in ContentViewCore including the gesture
 * recognition. It sends all related native calls through the interface MotionEventDelegate.
 */
class ContentViewGestureHandler implements LongPressDelegate {

    private static final String TAG = ContentViewGestureHandler.class.toString();
    /**
     * Used for GESTURE_FLING_START x velocity
     */
    static final String VELOCITY_X = "Velocity X";
    /**
     * Used for GESTURE_FLING_START y velocity
     */
    static final String VELOCITY_Y = "Velocity Y";
    /**
     * Used for GESTURE_SCROLL_BY x distance
     */
    static final String DISTANCE_X = "Distance X";
    /**
     * Used for GESTURE_SCROLL_BY y distance
     */
    static final String DISTANCE_Y = "Distance Y";
    /**
     * Used in GESTURE_SINGLE_TAP_CONFIRMED to check whether ShowPress has been called before.
     */
    static final String SHOW_PRESS = "ShowPress";
    /**
     * Used for GESTURE_PINCH_BY delta
     */
    static final String DELTA = "Delta";

    private final Bundle mExtraParamBundle;
    private GestureDetector mGestureDetector;
    private final ZoomManager mZoomManager;
    private LongPressDetector mLongPressDetector;
    private OnGestureListener mListener;
    private MotionEvent mCurrentDownEvent;
    private final MotionEventDelegate mMotionEventDelegate;

    // Queue of motion events.
    private final Deque<MotionEvent> mPendingMotionEvents = new ArrayDeque<MotionEvent>();

    // Has WebKit told us the current page requires touch events.
    private boolean mHasTouchHandlers = false;

    // True if the down event for the current gesture was returned back to the browser with
    // INPUT_EVENT_ACK_STATE_NO_CONSUMER_EXISTS
    private boolean mNoTouchHandlerForGesture = false;

    // True if JavaScript touch event handlers returned an ACK with
    // INPUT_EVENT_ACK_STATE_CONSUMED. In this case we should avoid, sending events from
    // this gesture to the Gesture Detector since it will have already missed at least
    // one event.
    private boolean mJavaScriptIsConsumingGesture = false;

    // Remember whether onShowPress() is called. If it is not, in onSingleTapConfirmed()
    // we will first show the press state, then trigger the click.
    private boolean mShowPressIsCalled;

    // TODO(klobag): this is to avoid a bug in GestureDetector. With multi-touch,
    // mAlwaysInTapRegion is not reset. So when the last finger is up, onSingleTapUp()
    // will be mistakenly fired.
    private boolean mIgnoreSingleTap;

    // Does native think we are scrolling?  True from right before we
    // send the first scroll event until the last finger is raised.
    // Call nativeScrollBegin() when setting this to true, and use
    // tellNativeScrollingHasEnded() to set it to false.
    private boolean mNativeScrolling;

    private boolean mFlingMayBeActive;

    private boolean mSeenFirstScrollEvent;

    private boolean mPinchInProgress = false;

    // Tracks whether a touch cancel event has been sent as a result of switching
    // into scrolling or pinching mode.
    private boolean mTouchCancelEventSent = false;

    // Last cancelled touch event as a result of scrolling or pinching.
    private MotionEvent mLastCancelledEvent = null;

    private static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

    //On single tap this will store the x, y coordinates of the touch.
    private int mSingleTapX;
    private int mSingleTapY;

    // Used to track the last rawX/Y coordinates for moves.  This gives absolute scroll distance.
    // Useful for full screen tracking.
    private float mLastRawX = 0;
    private float mLastRawY = 0;

    // Cache of square of the scaled touch slop so we don't have to calculate it on every touch.
    private int mScaledTouchSlopSquare;

    // Object that keeps track of and updates scroll snapping behavior.
    private SnapScrollController mSnapScrollController;

    // Used to track the accumulated scroll error over time. This is used to remove the
    // rounding error we introduced by passing integers to webkit.
    private float mAccumulatedScrollErrorX = 0;
    private float mAccumulatedScrollErrorY = 0;

    // Whether input events are delivered right before vsync.
    private final boolean mInputEventsDeliveredAtVSync;

    static final int GESTURE_SHOW_PRESSED_STATE = 0;
    static final int GESTURE_DOUBLE_TAP = 1;
    static final int GESTURE_SINGLE_TAP_UP = 2;
    static final int GESTURE_SINGLE_TAP_CONFIRMED = 3;
    static final int GESTURE_SINGLE_TAP_UNCONFIRMED = 4;
    static final int GESTURE_LONG_PRESS = 5;
    static final int GESTURE_SCROLL_START = 6;
    static final int GESTURE_SCROLL_BY = 7;
    static final int GESTURE_SCROLL_END = 8;
    static final int GESTURE_FLING_START = 9;
    static final int GESTURE_FLING_CANCEL = 10;
    static final int GESTURE_PINCH_BEGIN = 11;
    static final int GESTURE_PINCH_BY = 12;
    static final int GESTURE_PINCH_END = 13;
    static final int GESTURE_SHOW_PRESS_CANCEL = 14;
    static final int GESTURE_LONG_TAP = 15;

    // These have to be kept in sync with content/port/common/input_event_ack_state.h
    static final int INPUT_EVENT_ACK_STATE_UNKNOWN = 0;
    static final int INPUT_EVENT_ACK_STATE_CONSUMED = 1;
    static final int INPUT_EVENT_ACK_STATE_NOT_CONSUMED = 2;
    static final int INPUT_EVENT_ACK_STATE_NO_CONSUMER_EXISTS = 3;

    // Return values of sendTouchEventToNative();
    static final int EVENT_FORWARDED_TO_NATIVE = 0;
    static final int EVENT_CONVERTED_TO_CANCEL = 1;
    static final int EVENT_NOT_FORWARDED = 2;

    /**
     * This is an interface to handle MotionEvent related communication with the native side also
     * access some ContentView specific parameters.
     */
    public interface MotionEventDelegate {
        /**
         * Send a raw {@link MotionEvent} to the native side
         * @param timeMs Time of the event in ms.
         * @param action The action type for the event.
         * @param pts The TouchPoint array to be sent for the event.
         * @return Whether the event was sent to the native side successfully or not.
         */
        public boolean sendTouchEvent(long timeMs, int action, TouchPoint[] pts);

        /**
         * Send a gesture event to the native side.
         * @param type The type of the gesture event.
         * @param timeMs The time the gesture event occurred at.
         * @param x The x location for the gesture event.
         * @param y The y location for the gesture event.
         * @param lastInputEventForVSync Indicates that this gesture event is the last input
         * to be event sent during the current vsync interval.
         * @param extraParams A bundle that holds specific extra parameters for certain gestures.
         * Refer to gesture type definition for more information.
         * @return Whether the gesture was sent successfully.
         */
        boolean sendGesture(
                int type, long timeMs, int x, int y, boolean lastInputEventForVSync,
                Bundle extraParams);

        /**
         * Gives the UI the chance to override each scroll event.
         * @param x The amount scrolled in the X direction.
         * @param y The amount scrolled in the Y direction.
         * @return Whether or not the UI consumed and handled this event.
         */
        boolean didUIStealScroll(float x, float y);

        /**
         * Show the zoom picker UI.
         */
        public void invokeZoomPicker();

        /**
         * @return Whether changing the page scale is not possible on the current page.
         */
        public boolean hasFixedPageScale();
    }

    ContentViewGestureHandler(
            Context context, MotionEventDelegate delegate, ZoomManager zoomManager,
            int inputEventDeliveryMode) {
        mExtraParamBundle = new Bundle();
        mLongPressDetector = new LongPressDetector(context, this);
        mMotionEventDelegate = delegate;
        mZoomManager = zoomManager;
        mSnapScrollController = new SnapScrollController(context, mZoomManager);
        mInputEventsDeliveredAtVSync =
                inputEventDeliveryMode == ContentViewCore.INPUT_EVENTS_DELIVERED_AT_VSYNC;

        initGestureDetectors(context);
    }

    /**
     * Used to override the default long press detector, gesture detector and listener.
     * This is used for testing only.
     * @param longPressDetector The new LongPressDetector to be assigned.
     * @param gestureDetector The new GestureDetector to be assigned.
     * @param listener The new onGestureListener to be assigned.
     */
    void setTestDependencies(
            LongPressDetector longPressDetector, GestureDetector gestureDetector,
            OnGestureListener listener) {
        mLongPressDetector = longPressDetector;
        mGestureDetector = gestureDetector;
        mListener = listener;
    }

    private void initGestureDetectors(final Context context) {
        final int scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScaledTouchSlopSquare = scaledTouchSlop * scaledTouchSlop;
        try {
            TraceEvent.begin();
            GestureDetector.SimpleOnGestureListener listener =
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        mShowPressIsCalled = false;
                        mIgnoreSingleTap = false;
                        mNativeScrolling = false;
                        mSeenFirstScrollEvent = false;
                        mSnapScrollController.resetSnapScrollMode();
                        mLastRawX = e.getRawX();
                        mLastRawY = e.getRawY();
                        mAccumulatedScrollErrorX = 0;
                        mAccumulatedScrollErrorY = 0;
                        // Return true to indicate that we want to handle touch
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                            float distanceX, float distanceY) {
                        if (!mSeenFirstScrollEvent) {
                            // Remove the touch slop region from the first scroll event to avoid a
                            // jump.
                            mSeenFirstScrollEvent = true;
                            double distance = Math.sqrt(
                                    distanceX * distanceX + distanceY * distanceY);
                            double epsilon = 1e-3;
                            if (distance > epsilon) {
                                double ratio = Math.max(0, distance - scaledTouchSlop) / distance;
                                distanceX *= ratio;
                                distanceY *= ratio;
                            }
                        }
                        mSnapScrollController.updateSnapScrollMode(distanceX, distanceY);
                        if (mSnapScrollController.isSnappingScrolls()) {
                            if (mSnapScrollController.isSnapHorizontal()) {
                                distanceY = 0;
                            } else {
                                distanceX = 0;
                            }
                        }

                        boolean didUIStealScroll = mMotionEventDelegate.didUIStealScroll(
                                e2.getRawX() - mLastRawX, e2.getRawY() - mLastRawY);

                        mLastRawX = e2.getRawX();
                        mLastRawY = e2.getRawY();
                        if (didUIStealScroll) return true;
                        if (!mNativeScrolling) {
                            sendShowPressCancelIfNecessary(e1);
                            endFling(e2.getEventTime());
                            if (sendMotionEventAsGesture(GESTURE_SCROLL_START, e1, null)) {
                                mNativeScrolling = true;
                            }
                        }
                        // distanceX and distanceY is the scrolling offset since last onScroll.
                        // Because we are passing integers to webkit, this could introduce
                        // rounding errors. The rounding errors will accumulate overtime.
                        // To solve this, we should be adding back the rounding errors each time
                        // when we calculate the new offset.
                        int x = (int) e2.getX();
                        int y = (int) e2.getY();
                        int dx = (int) (distanceX + mAccumulatedScrollErrorX);
                        int dy = (int) (distanceY + mAccumulatedScrollErrorY);
                        mAccumulatedScrollErrorX = distanceX + mAccumulatedScrollErrorX - dx;
                        mAccumulatedScrollErrorY = distanceY + mAccumulatedScrollErrorY - dy;
                        mExtraParamBundle.clear();
                        mExtraParamBundle.putInt(DISTANCE_X, dx);
                        mExtraParamBundle.putInt(DISTANCE_Y, dy);
                        if ((dx | dy) != 0) {
                            sendLastGestureForVSync(GESTURE_SCROLL_BY,
                                    e2.getEventTime(), x, y, mExtraParamBundle);
                        }

                        mMotionEventDelegate.invokeZoomPicker();

                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                            float velocityX, float velocityY) {
                        if (mSnapScrollController.isSnappingScrolls()) {
                            if (mSnapScrollController.isSnapHorizontal()) {
                                velocityY = 0;
                            } else {
                                velocityX = 0;
                            }
                        }

                        fling(e1.getEventTime(),(int) e1.getX(0), (int) e1.getY(0),
                                        (int) velocityX, (int) velocityY);
                        return true;
                    }

                    @Override
                    public void onShowPress(MotionEvent e) {
                        mShowPressIsCalled = true;
                        sendMotionEventAsGesture(GESTURE_SHOW_PRESSED_STATE, e, null);
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (isDistanceBetweenDownAndUpTooLong(e.getRawX(), e.getRawY())) {
                            mIgnoreSingleTap = true;
                            return true;
                        }
                        // This is a hack to address the issue where user hovers
                        // over a link for longer than DOUBLE_TAP_TIMEOUT, then
                        // onSingleTapConfirmed() is not triggered. But we still
                        // want to trigger the tap event at UP. So we override
                        // onSingleTapUp() in this case. This assumes singleTapUp
                        // gets always called before singleTapConfirmed.
                        if (!mIgnoreSingleTap && !mLongPressDetector.isInLongPress()) {
                            if (e.getEventTime() - e.getDownTime() > DOUBLE_TAP_TIMEOUT) {
                                float x = e.getX();
                                float y = e.getY();
                                if (sendMotionEventAsGesture(GESTURE_SINGLE_TAP_UP, e, null)) {
                                    mIgnoreSingleTap = true;
                                }
                                setClickXAndY((int) x, (int) y);
                                return true;
                            } else if (mMotionEventDelegate.hasFixedPageScale()) {
                                // If page is not user scalable, we don't need to wait
                                // for double tap timeout.
                                float x = e.getX();
                                float y = e.getY();
                                mExtraParamBundle.clear();
                                mExtraParamBundle.putBoolean(SHOW_PRESS, mShowPressIsCalled);
                                if (sendMotionEventAsGesture(GESTURE_SINGLE_TAP_CONFIRMED, e,
                                        mExtraParamBundle)) {
                                    mIgnoreSingleTap = true;
                                }
                                setClickXAndY((int) x, (int) y);
                            } else {
                                // Notify Blink about this tapUp event anyway,
                                // when none of the above conditions applied.
                                sendMotionEventAsGesture(GESTURE_SINGLE_TAP_UNCONFIRMED, e, null);
                            }
                        }

                        return triggerLongTapIfNeeded(e);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        // Long taps in the edges of the screen have their events delayed by
                        // ContentViewHolder for tab swipe operations. As a consequence of the delay
                        // this method might be called after receiving the up event.
                        // These corner cases should be ignored.
                        if (mLongPressDetector.isInLongPress() || mIgnoreSingleTap) return true;

                        int x = (int) e.getX();
                        int y = (int) e.getY();
                        mExtraParamBundle.clear();
                        mExtraParamBundle.putBoolean(SHOW_PRESS, mShowPressIsCalled);
                        sendMotionEventAsGesture(GESTURE_SINGLE_TAP_CONFIRMED, e,
                            mExtraParamBundle);
                        setClickXAndY(x, y);
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        sendShowPressCancelIfNecessary(e);
                        sendMotionEventAsGesture(GESTURE_DOUBLE_TAP, e, null);
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (!mZoomManager.isScaleGestureDetectionInProgress()) {
                            sendShowPressCancelIfNecessary(e);
                            sendMotionEventAsGesture(GESTURE_LONG_PRESS, e, null);
                        }
                    }

                    /**
                     * This method inspects the distance between where the user started touching
                     * the surface, and where she released. If the points are too far apart, we
                     * should assume that the web page has consumed the scroll-events in-between,
                     * and as such, this should not be considered a single-tap.
                     *
                     * We use the Android frameworks notion of how far a touch can wander before
                     * we think the user is scrolling.
                     *
                     * @param x the new x coordinate
                     * @param y the new y coordinate
                     * @return true if the distance is too long to be considered a single tap
                     */
                    private boolean isDistanceBetweenDownAndUpTooLong(float x, float y) {
                        double deltaX = mLastRawX - x;
                        double deltaY = mLastRawY - y;
                        return deltaX * deltaX + deltaY * deltaY > mScaledTouchSlopSquare;
                    }
                };
                mListener = listener;
                mGestureDetector = new GestureDetector(context, listener);
                mGestureDetector.setIsLongpressEnabled(false);
        } finally {
            TraceEvent.end();
        }
    }

    /**
     * @return LongPressDetector handling setting up timers for and canceling LongPress gestures.
     */
    LongPressDetector getLongPressDetector() {
        return mLongPressDetector;
    }

    /**
     * @param event Start a LongPress gesture event from the listener.
     */
    @Override
    public void onLongPress(MotionEvent event) {
        mListener.onLongPress(event);
    }

    /**
     * Cancels any ongoing LongPress timers.
     */
    void cancelLongPress() {
        mLongPressDetector.cancelLongPress();
    }

    /**
     * Fling the ContentView from the current position.
     * @param x Fling touch starting position
     * @param y Fling touch starting position
     * @param velocityX Initial velocity of the fling (X) measured in pixels per second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per second.
     */
    void fling(long timeMs, int x, int y, int velocityX, int velocityY) {
        boolean nativeScrolling = mNativeScrolling;
        endFling(timeMs);
        if (!nativeScrolling) {
            // The native side needs a GESTURE_SCROLL_BEGIN before GESTURE_FLING_START
            // to send the fling to the correct target. Send if it has not sent.
            sendGesture(GESTURE_SCROLL_START, timeMs, x, y, null);
        }

        mFlingMayBeActive = true;

        mExtraParamBundle.clear();
        mExtraParamBundle.putInt(VELOCITY_X, velocityX);
        mExtraParamBundle.putInt(VELOCITY_Y, velocityY);
        sendGesture(GESTURE_FLING_START, timeMs, x, y, mExtraParamBundle);
    }

    /**
     * Send a FlingCancel gesture event and also cancel scrolling if it is active.
     * @param timeMs The time in ms for the event initiating this gesture.
     */
    void endFling(long timeMs) {
        if (mFlingMayBeActive) {
            sendGesture(GESTURE_FLING_CANCEL, timeMs, 0, 0, null);
            tellNativeScrollingHasEnded(timeMs, false);
            mFlingMayBeActive = false;
        }
    }

    // If native thinks scrolling (or fling-scrolling) is going on, tell native
    // it has ended.
    private void tellNativeScrollingHasEnded(long timeMs, boolean sendScrollEndEvent) {
        if (mNativeScrolling) {
            mNativeScrolling = false;
            if (sendScrollEndEvent) {
                sendGesture(GESTURE_SCROLL_END, timeMs, 0, 0, null);
            }
        }
    }

    /**
     * @return Whether native is tracking a scroll (i.e. between sending GESTURE_SCROLL_START and
     *         GESTURE_SCROLL_END, or during a fling before sending GESTURE_FLING_CANCEL).
     */
    boolean isNativeScrolling() {
        return mNativeScrolling;
    }

    /**
     * @return Whether native is tracking a pinch (i.e. between sending GESTURE_PINCH_BEGIN and
     *         GESTURE_PINCH_END).
     */
    boolean isNativePinching() {
        return mPinchInProgress;
    }

    /**
     * Starts a pinch gesture.
     * @param timeMs The time in ms for the event initiating this gesture.
     * @param x The x coordinate for the event initiating this gesture.
     * @param y The x coordinate for the event initiating this gesture.
     */
    void pinchBegin(long timeMs, int x, int y) {
        sendGesture(GESTURE_PINCH_BEGIN, timeMs, x, y, null);
    }

    /**
     * Pinch by a given percentage.
     * @param timeMs The time in ms for the event initiating this gesture.
     * @param anchorX The x coordinate for the anchor point to be used in pinch.
     * @param anchorY The y coordinate for the anchor point to be used in pinch.
     * @param delta The percentage to pinch by.
     */
    void pinchBy(long timeMs, int anchorX, int anchorY, float delta) {
        mExtraParamBundle.clear();
        mExtraParamBundle.putFloat(DELTA, delta);
        sendLastGestureForVSync(GESTURE_PINCH_BY, timeMs, anchorX, anchorY, mExtraParamBundle);
        mPinchInProgress = true;
    }

    /**
     * End a pinch gesture.
     * @param timeMs The time in ms for the event initiating this gesture.
     */
    void pinchEnd(long timeMs) {
        sendGesture(GESTURE_PINCH_END, timeMs, 0, 0, null);
        mPinchInProgress = false;
    }

    /**
     * Ignore singleTap gestures.
     */
    void setIgnoreSingleTap(boolean value) {
        mIgnoreSingleTap = value;
    }

    private float calculateDragAngle(float dx, float dy) {
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        return (float) Math.atan2(dy, dx);
    }

    private void setClickXAndY(int x, int y) {
        mSingleTapX = x;
        mSingleTapY = y;
    }

    /**
     * @return The x coordinate for the last point that a singleTap gesture was initiated from.
     */
    public int getSingleTapX()  {
        return mSingleTapX;
    }

    /**
     * @return The y coordinate for the last point that a singleTap gesture was initiated from.
     */
    public int getSingleTapY()  {
        return mSingleTapY;
    }

    /**
     * Handle the incoming MotionEvent.
     * @return Whether the event was handled.
     */
    boolean onTouchEvent(MotionEvent event) {
        try {
            TraceEvent.begin("onTouchEvent");
            mLongPressDetector.cancelLongPressIfNeeded(event);
            mSnapScrollController.setSnapScrollingMode(event);
            // Notify native that scrolling has stopped whenever a down action is processed prior to
            // passing the event to native as it will drop them as an optimization if scrolling is
            // enabled.  Ending the fling ensures scrolling has stopped as well as terminating the
            // current fling if applicable.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mNoTouchHandlerForGesture = false;
                mJavaScriptIsConsumingGesture = false;
                endFling(event.getEventTime());
            }

            if (offerTouchEventToJavaScript(event)) {
                // offerTouchEventToJavaScript returns true to indicate the event was sent
                // to the render process. If it is not subsequently handled, it will
                // be returned via confirmTouchEvent(false) and eventually passed to
                // processTouchEvent asynchronously.
                return true;
            }
            return processTouchEvent(event);
        } finally {
            TraceEvent.end("onTouchEvent");
        }
    }

    private MotionEvent obtainActionCancelMotionEvent() {
        return MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, 0.0f,  0.0f,  0);
    }

    /**
     * Resets gesture handlers state; called on didStartLoading().
     * Note that this does NOT clear the pending motion events queue;
     * it gets cleared in hasTouchEventHandlers() called from WebKit
     * FrameLoader::transitionToCommitted iff the page ever had touch handlers.
     */
    void resetGestureHandlers() {
        {
            MotionEvent me = obtainActionCancelMotionEvent();
            mGestureDetector.onTouchEvent(me);
            me.recycle();
        }
        {
            MotionEvent me = obtainActionCancelMotionEvent();
            mZoomManager.processTouchEvent(me);
            me.recycle();
        }
        mLongPressDetector.cancelLongPress();
    }

    /**
     * Sets the flag indicating that the content has registered listeners for touch events.
     */
    void hasTouchEventHandlers(boolean hasTouchHandlers) {
        mHasTouchHandlers = hasTouchHandlers;
        // When mainframe is loading, FrameLoader::transitionToCommitted will
        // call this method to set mHasTouchHandlers to false. We use this as
        // an indicator to clear the pending motion events so that events from
        // the previous page will not be carried over to the new page.
        if (!mHasTouchHandlers) mPendingMotionEvents.clear();
    }

    private boolean offerTouchEventToJavaScript(MotionEvent event) {
        mLongPressDetector.onOfferTouchEventToJavaScript(event);

        if (!mHasTouchHandlers || mNoTouchHandlerForGesture) return false;

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Only send move events if the move has exceeded the slop threshold.
            if (!mLongPressDetector.confirmOfferMoveEventToJavaScript(event)) {
                return true;
            }
            // Avoid flooding the renderer process with move events: if the previous pending
            // command is also a move (common case), skip sending this event to the webkit
            // side and collapse it into the pending event.
            MotionEvent previousEvent = mPendingMotionEvents.peekLast();
            if (previousEvent != null
                    && previousEvent.getActionMasked() == MotionEvent.ACTION_MOVE
                    && previousEvent.getPointerCount() == event.getPointerCount()) {
                MotionEvent.PointerCoords[] coords =
                        new MotionEvent.PointerCoords[event.getPointerCount()];
                for (int i = 0; i < coords.length; ++i) {
                    coords[i] = new MotionEvent.PointerCoords();
                    event.getPointerCoords(i, coords[i]);
                }
                previousEvent.addBatch(event.getEventTime(), coords, event.getMetaState());
                return true;
            }
        }
        int forward = EVENT_NOT_FORWARDED;
        if (mPendingMotionEvents.isEmpty()) {
            forward = sendTouchEventToNative(event);
        }
        if (!mPendingMotionEvents.isEmpty() || forward != EVENT_NOT_FORWARDED) {
            // Copy the event, as the original may get mutated after this method returns.
            MotionEvent clone = MotionEvent.obtain(event);
            mPendingMotionEvents.add(clone);
            // If touch cancel was sent, remember the event.
            if (forward == EVENT_CONVERTED_TO_CANCEL) {
                mLastCancelledEvent = clone;
            }
            return true;
        }
        return false;
    }

    private int sendTouchEventToNative(MotionEvent event) {
        TouchPoint[] pts = new TouchPoint[event.getPointerCount()];
        int type = TouchPoint.createTouchPoints(event, pts);

        if (type != TouchPoint.CONVERSION_ERROR) {
            if (!mNativeScrolling && !mPinchInProgress) {
                mTouchCancelEventSent = false;
                if (mMotionEventDelegate.sendTouchEvent(event.getEventTime(), type, pts)) {
                    return EVENT_FORWARDED_TO_NATIVE;
                }
            } else if (!mTouchCancelEventSent) {
                mTouchCancelEventSent = true;
                if (mMotionEventDelegate.sendTouchEvent(event.getEventTime(),
                        TouchPoint.TOUCH_EVENT_TYPE_CANCEL, pts)) {
                    return EVENT_CONVERTED_TO_CANCEL;
                }
            }
        }
        return EVENT_NOT_FORWARDED;
    }

    private boolean processTouchEvent(MotionEvent event) {
        boolean handled = false;
        // The last "finger up" is an end to scrolling but may not be
        // an end to movement (e.g. fling scroll).  We do not tell
        // native code to end scrolling until we are sure we did not
        // fling.
        boolean possiblyEndMovement = false;
        // "Last finger raised" could be an end to movement.  However,
        // give the mSimpleTouchDetector a chance to continue
        // scrolling with a fling.
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (mNativeScrolling) {
                possiblyEndMovement = true;
            }
        }

        mLongPressDetector.cancelLongPressIfNeeded(event);
        mLongPressDetector.startLongPressTimerIfNeeded(event);

        // Use the framework's GestureDetector to detect pans and zooms not already
        // handled by the WebKit touch events gesture manager.
        if (canHandle(event)) {
            handled |= mGestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mCurrentDownEvent = MotionEvent.obtain(event);
            }
        }

        handled |= mZoomManager.processTouchEvent(event);

        if (possiblyEndMovement && !handled) {
            tellNativeScrollingHasEnded(event.getEventTime(), true);
        }

        return handled;
    }

    /**
     * Respond to a MotionEvent being returned from the native side.
     * @param handled Whether the MotionEvent was handled on the native side.
     */
    void confirmTouchEvent(int ackResult) {
        if (mPendingMotionEvents.isEmpty()) {
            Log.w(TAG, "confirmTouchEvent with Empty pending list!");
            return;
        }
        TraceEvent.begin();
        MotionEvent ackedEvent = mPendingMotionEvents.removeFirst();
        if (ackedEvent.equals(mLastCancelledEvent)) {
            // The event is canceled, just drain all the pending events until next
            // touch down.
            ackResult = INPUT_EVENT_ACK_STATE_NO_CONSUMER_EXISTS;
        }
        switch (ackResult) {
            case INPUT_EVENT_ACK_STATE_UNKNOWN:
                // This should never get sent.
                assert(false);
                break;
            case INPUT_EVENT_ACK_STATE_CONSUMED:
                mJavaScriptIsConsumingGesture = true;
                mZoomManager.passTouchEventThrough(ackedEvent);
                if (!mPendingMotionEvents.isEmpty()) {
                    trySendNextEventToNative(mPendingMotionEvents.peekFirst());
                }
                break;
            case INPUT_EVENT_ACK_STATE_NOT_CONSUMED:
                if (!mJavaScriptIsConsumingGesture) processTouchEvent(ackedEvent);
                if (!mPendingMotionEvents.isEmpty()) {
                    trySendNextEventToNative(mPendingMotionEvents.peekFirst());
                }
                break;
            case INPUT_EVENT_ACK_STATE_NO_CONSUMER_EXISTS:
                mNoTouchHandlerForGesture = true;
                processTouchEvent(ackedEvent);
                drainAllPendingEventsUntilNextDown();
                break;
            default:
                break;
        }

        mLongPressDetector.cancelLongPressIfNeeded(mPendingMotionEvents.iterator());

        ackedEvent.recycle();
        TraceEvent.end();
    }

    private void trySendNextEventToNative(MotionEvent nextEvent) {
        assert(nextEvent != null);

        int forward = sendTouchEventToNative(nextEvent);
        if (forward == EVENT_NOT_FORWARDED) {
            if (!mJavaScriptIsConsumingGesture) processTouchEvent(nextEvent);
            mPendingMotionEvents.removeFirst();
            // Even though we missed sending one event to native, as long as we haven't
            // received INPUT_EVENT_ACK_STATE_NO_CONSUMER_EXISTS, we should keep sending
            // events on the queue to native.
            if (!mPendingMotionEvents.isEmpty()) {
                trySendNextEventToNative(mPendingMotionEvents.peekFirst());
            }
        } else if (forward == EVENT_CONVERTED_TO_CANCEL) {
            mLastCancelledEvent = mPendingMotionEvents.peekFirst();
        }
    }

    private void drainAllPendingEventsUntilNextDown() {
        // Now process all events that are in the queue until the next down event.
        MotionEvent nextEvent = mPendingMotionEvents.peekFirst();
        while (nextEvent != null && nextEvent.getActionMasked() != MotionEvent.ACTION_DOWN) {
            processTouchEvent(nextEvent);
            mPendingMotionEvents.removeFirst();
            nextEvent.recycle();
            nextEvent = mPendingMotionEvents.peekFirst();
        }

        if (nextEvent == null) return;

        mNoTouchHandlerForGesture = false;
        trySendNextEventToNative(nextEvent);
    }

    private boolean sendMotionEventAsGesture(
            int type, MotionEvent event, Bundle extraParams) {
        return mMotionEventDelegate.sendGesture(type, event.getEventTime(),
            (int) event.getX(), (int) event.getY(), false, extraParams);
    }

    private boolean sendGesture(
            int type, long timeMs, int x, int y, Bundle extraParams) {
        return mMotionEventDelegate.sendGesture(type, timeMs, x, y, false, extraParams);
    }

    private boolean sendLastGestureForVSync(
            int type, long timeMs, int x, int y, Bundle extraParams) {
        return mMotionEventDelegate.sendGesture(
            type, timeMs, x, y, mInputEventsDeliveredAtVSync, extraParams);
    }

    void sendShowPressCancelIfNecessary(MotionEvent e) {
        if (!mShowPressIsCalled) return;

        if (sendMotionEventAsGesture(GESTURE_SHOW_PRESS_CANCEL, e, null)) {
            mShowPressIsCalled = false;
        }
    }

    /**
     * @return Whether the ContentViewGestureHandler can handle a MotionEvent right now. True only
     * if it's the start of a new stream (ACTION_DOWN), or a continuation of the current stream.
     */
    boolean canHandle(MotionEvent ev) {
        return ev.getAction() == MotionEvent.ACTION_DOWN ||
                (mCurrentDownEvent != null && mCurrentDownEvent.getDownTime() == ev.getDownTime());
    }

    /**
     * @return Whether the event can trigger a LONG_TAP gesture. True when it can and the event
     * will be consumed.
     */
    boolean triggerLongTapIfNeeded(MotionEvent ev) {
        if (mLongPressDetector.isInLongPress() && ev.getAction() == MotionEvent.ACTION_UP &&
                !mZoomManager.isScaleGestureDetectionInProgress()) {
            sendShowPressCancelIfNecessary(ev);
            sendMotionEventAsGesture(GESTURE_LONG_TAP, ev, null);
            return true;
        }
        return false;
    }

    /**
     * This is for testing only.
     * @return The first motion event on the pending motion events queue.
     */
    MotionEvent peekFirstInPendingMotionEventsForTesting() {
        return mPendingMotionEvents.peekFirst();
    }

    /**
     * This is for testing only.
     * @return Whether the motion event is cancelled.
     */
    boolean isEventCancelledForTesting(MotionEvent event) {
        return event != null && event.equals(mLastCancelledEvent);
    }

    /**
     * This is for testing only.
     * @return The number of motion events on the pending motion events queue.
     */
    int getNumberOfPendingMotionEventsForTesting() {
        return mPendingMotionEvents.size();
    }

    /**
     * This is for testing only.
     * Sends a show pressed state gesture through mListener. This should always be called after
     * a down event;
     */
    void sendShowPressedStateGestureForTesting() {
        if (mCurrentDownEvent == null) return;
        mListener.onShowPress(mCurrentDownEvent);
    }
}
