// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import org.chromium.base.CalledByNative;

/**
 * Takes care of syncing the scroll offset between the Android View system and the
 * InProcessViewRenderer.
 *
 * Unless otherwise values (sizes, scroll offsets) are in physical pixels.
 */
public class AwScrollOffsetManager {
    // The unit of all the values in this delegate are physical pixels.
    public interface Delegate {
        // Call View#overScrollBy on the containerView.
        void overScrollContainerViewBy(int deltaX, int deltaY, int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY);
        // Call View#scrollTo on the containerView.
        void scrollContainerViewTo(int x, int y);
        // Store the scroll offset in the native side. This should really be a simple store
        // operation, the native side shouldn't synchronously alter the scroll offset from within
        // this call.
        void scrollNativeTo(int x, int y);
        int getContainerViewScrollX();
        int getContainerViewScrollY();
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

    public AwScrollOffsetManager(Delegate delegate) {
        mDelegate = delegate;
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
                scrollRangeX, scrollRangeY);
    }

    // Called by the native side to over-scroll the container view.
    public void overscrollBy(int deltaX, int deltaY) {
        final int scrollX = mDelegate.getContainerViewScrollX();
        final int scrollY = mDelegate.getContainerViewScrollY();
        final int scrollRangeX = computeMaximumHorizontalScrollOffset();
        final int scrollRangeY = computeMaximumVerticalScrollOffset();

        mDelegate.overScrollContainerViewBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY);
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
        // This will only do anything if the containerView scroll offset ends up being different
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

        if (x == mNativeScrollX && y == mNativeScrollY)
            return;

        // The scrollNativeTo call should be a simple store, so it's OK to assume it always
        // succeeds.
        mNativeScrollX = x;
        mNativeScrollY = y;

        mDelegate.scrollNativeTo(x, y);
    }
}
