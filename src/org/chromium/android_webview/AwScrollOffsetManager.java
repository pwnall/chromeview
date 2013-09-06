// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.widget.OverScroller;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;

/**
 * Takes care of syncing the scroll offset between the Android View system and the
 * InProcessViewRenderer.
 *
 * Unless otherwise values (sizes, scroll offsets) are in physical pixels.
 */
@VisibleForTesting
public class AwScrollOffsetManager {
    // The unit of all the values in this delegate are physical pixels.
    public interface Delegate {
        // Call View#overScrollBy on the containerView.
        void overScrollContainerViewBy(int deltaX, int deltaY, int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY, boolean isTouchEvent);
        // Call View#scrollTo on the containerView.
        void scrollContainerViewTo(int x, int y);
        // Store the scroll offset in the native side. This should really be a simple store
        // operation, the native side shouldn't synchronously alter the scroll offset from within
        // this call.
        void scrollNativeTo(int x, int y);

        int getContainerViewScrollX();
        int getContainerViewScrollY();

        void invalidate();
    }

    private final Delegate mDelegate;

    // Scroll offset as seen by the native side.
    private int mNativeScrollX;
    private int mNativeScrollY;

    // Content size.
    private int mContentWidth;
    private int mContentHeight;

    // Size of the container view.
    private int mContainerViewWidth;
    private int mContainerViewHeight;

    // Whether we're in the middle of processing a touch event.
    private boolean mProcessingTouchEvent;

    // Whether (and to what value) to update the native side scroll offset after we've finished
    // provessing a touch event.
    private boolean mApplyDeferredNativeScroll;
    private int mDeferredNativeScrollX;
    private int mDeferredNativeScrollY;

    // The velocity of the last recorded fling,
    private int mLastFlingVelocityX;
    private int mLastFlingVelocityY;

    private OverScroller mScroller;

    public AwScrollOffsetManager(Delegate delegate, OverScroller overScroller) {
        mDelegate = delegate;
        mScroller = overScroller;
    }

    //----- Scroll range and extent calculation methods -------------------------------------------

    public int computeHorizontalScrollRange() {
        return Math.max(mContainerViewWidth, mContentWidth);
    }

    public int computeMaximumHorizontalScrollOffset() {
        return computeHorizontalScrollRange() - mContainerViewWidth;
    }

    public int computeHorizontalScrollOffset() {
        return mDelegate.getContainerViewScrollX();
    }

    public int computeVerticalScrollRange() {
        return Math.max(mContainerViewHeight, mContentHeight);
    }

    public int computeMaximumVerticalScrollOffset() {
        return computeVerticalScrollRange() - mContainerViewHeight;
    }

    public int computeVerticalScrollOffset() {
        return mDelegate.getContainerViewScrollY();
    }

    public int computeVerticalScrollExtent() {
        return mContainerViewHeight;
    }

    //---------------------------------------------------------------------------------------------

    // Called when the content size changes. This needs to be the size of the on-screen content and
    // therefore we can't use the WebContentsDelegate preferred size.
    public void setContentSize(int width, int height) {
        mContentWidth = width;
        mContentHeight = height;
    }

    // Called when the physical size of the view changes.
    public void setContainerViewSize(int width, int height) {
        mContainerViewWidth = width;
        mContainerViewHeight = height;
    }

    public void syncScrollOffsetFromOnDraw() {
        // Unfortunately apps override onScrollChanged without calling super which is why we need
        // to sync the scroll offset on every onDraw.
        onContainerViewScrollChanged(mDelegate.getContainerViewScrollX(),
                mDelegate.getContainerViewScrollY());
    }

    public void setProcessingTouchEvent(boolean processingTouchEvent) {
        assert mProcessingTouchEvent != processingTouchEvent;
        mProcessingTouchEvent = processingTouchEvent;

        if (!mProcessingTouchEvent && mApplyDeferredNativeScroll) {
            mApplyDeferredNativeScroll = false;
            scrollNativeTo(mDeferredNativeScrollX, mDeferredNativeScrollY);
        }
    }

    // Called by the native side to attempt to scroll the container view.
    public void scrollContainerViewTo(int x, int y) {
        mNativeScrollX = x;
        mNativeScrollY = y;

        final int scrollX = mDelegate.getContainerViewScrollX();
        final int scrollY = mDelegate.getContainerViewScrollY();
        final int deltaX = x - scrollX;
        final int deltaY = y - scrollY;
        final int scrollRangeX = computeMaximumHorizontalScrollOffset();
        final int scrollRangeY = computeMaximumVerticalScrollOffset();

        // We use overScrollContainerViewBy to be compatible with WebViewClassic which used this
        // method for handling both over-scroll as well as in-bounds scroll.
        mDelegate.overScrollContainerViewBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, mProcessingTouchEvent);
    }

    // Called by the native side to over-scroll the container view.
    public void overScrollBy(int deltaX, int deltaY) {
        // TODO(mkosiba): Once http://crbug.com/260663 and http://crbug.com/261239 are fixed it
        // should be possible to uncomment the following asserts:
        // if (deltaX < 0) assert mDelegate.getContainerViewScrollX() == 0;
        // if (deltaX > 0) assert mDelegate.getContainerViewScrollX() ==
        //          computeMaximumHorizontalScrollOffset();
        scrollBy(deltaX, deltaY);
    }

    private void scrollBy(int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) return;

        final int scrollX = mDelegate.getContainerViewScrollX();
        final int scrollY = mDelegate.getContainerViewScrollY();
        final int scrollRangeX = computeMaximumHorizontalScrollOffset();
        final int scrollRangeY = computeMaximumVerticalScrollOffset();

        // The android.view.View.overScrollBy method is used for both scrolling and over-scrolling
        // which is why we use it here.
        mDelegate.overScrollContainerViewBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, mProcessingTouchEvent);
    }

    private int clampHorizontalScroll(int scrollX) {
        scrollX = Math.max(0, scrollX);
        scrollX = Math.min(computeMaximumHorizontalScrollOffset(), scrollX);
        return scrollX;
    }

    private int clampVerticalScroll(int scrollY) {
        scrollY = Math.max(0, scrollY);
        scrollY = Math.min(computeMaximumVerticalScrollOffset(), scrollY);
        return scrollY;
    }

    // Called by the View system as a response to the mDelegate.overScrollContainerViewBy call.
    public void onContainerViewOverScrolled(int scrollX, int scrollY, boolean clampedX,
            boolean clampedY) {
        // Clamp the scroll offset at (0, max).
        scrollX = clampHorizontalScroll(scrollX);
        scrollY = clampVerticalScroll(scrollY);

        mDelegate.scrollContainerViewTo(scrollX, scrollY);

        // This is only necessary if the containerView scroll offset ends up being different
        // than the one set from native in which case we want the value stored on the native side
        // to reflect the value stored in the containerView (and not the other way around).
        scrollNativeTo(mDelegate.getContainerViewScrollX(), mDelegate.getContainerViewScrollY());
    }

    // Called by the View system when the scroll offset had changed. This might not get called if
    // the embedder overrides WebView#onScrollChanged without calling super.onScrollChanged. If
    // this method does get called it is called both as a response to the embedder scrolling the
    // view as well as a response to mDelegate.scrollContainerViewTo.
    public void onContainerViewScrollChanged(int x, int y) {
        scrollNativeTo(x, y);
    }

    private void scrollNativeTo(int x, int y) {
        x = clampHorizontalScroll(x);
        y = clampVerticalScroll(y);

        // We shouldn't do the store to native while processing a touch event since that confuses
        // the gesture processing logic.
        if (mProcessingTouchEvent) {
            mDeferredNativeScrollX = x;
            mDeferredNativeScrollY = y;
            mApplyDeferredNativeScroll = true;
            return;
        }

        if (x == mNativeScrollX && y == mNativeScrollY)
            return;

        // The scrollNativeTo call should be a simple store, so it's OK to assume it always
        // succeeds.
        mNativeScrollX = x;
        mNativeScrollY = y;

        mDelegate.scrollNativeTo(x, y);
    }

    // Called at the beginning of every fling gesture.
    public void onFlingStartGesture(int velocityX, int velocityY) {
        mLastFlingVelocityX = velocityX;
        mLastFlingVelocityY = velocityY;
    }

    // Called whenever some other touch interaction requires the fling gesture to be canceled.
    public void onFlingCancelGesture() {
        // TODO(mkosiba): Support speeding up a fling by flinging again.
        // http://crbug.com/265841
        mScroller.forceFinished(true);
    }

    // Called when a fling gesture is not handled by the renderer.
    // We explicitly ask the renderer not to handle fling gestures targeted at the root
    // scroll layer.
    public void onUnhandledFlingStartEvent() {
        flingScroll(-mLastFlingVelocityX, -mLastFlingVelocityY);
    }

    // Starts the fling animation. Called both as a response to a fling gesture and as via the
    // public WebView#flingScroll(int, int) API.
    public void flingScroll(int velocityX, int velocityY) {
        final int scrollX = mDelegate.getContainerViewScrollX();
        final int scrollY = mDelegate.getContainerViewScrollY();
        final int rangeX = computeMaximumHorizontalScrollOffset();
        final int rangeY = computeMaximumVerticalScrollOffset();

        mScroller.fling(scrollX, scrollY, velocityX, velocityY,
                0, rangeX, 0, rangeY);
        mDelegate.invalidate();
    }

    // Called immediately before the draw to update the scroll offset.
    public void computeScrollAndAbsorbGlow(OverScrollGlow overScrollGlow) {
        final boolean stillAnimating = mScroller.computeScrollOffset();
        if (!stillAnimating) return;

        final int oldX = mDelegate.getContainerViewScrollX();
        final int oldY = mDelegate.getContainerViewScrollY();
        int x = mScroller.getCurrX();
        int y = mScroller.getCurrY();

        int rangeX = computeMaximumHorizontalScrollOffset();
        int rangeY = computeMaximumVerticalScrollOffset();

        if (overScrollGlow != null) {
            overScrollGlow.absorbGlow(x, y, oldX, oldY, rangeX, rangeY,
                    mScroller.getCurrVelocity());
        }

        // The mScroller is configured not to go outside of the scrollable range, so this call
        // should never result in attempting to scroll outside of the scrollable region.
        scrollBy(x - oldX, y - oldY);

        mDelegate.invalidate();
    }
}
