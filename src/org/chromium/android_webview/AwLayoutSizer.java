// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.util.Pair;
import android.view.View.MeasureSpec;
import android.view.View;

import org.chromium.content.browser.ContentViewCore;

/**
 * Helper methods used to manage the layout of the View that contains AwContents.
 */
public class AwLayoutSizer {
    // These are used to prevent a re-layout if the content size changes within a dimension that is
    // fixed by the view system.
    private boolean mWidthMeasurementIsFixed;
    private boolean mHeightMeasurementIsFixed;

    // Size of the rendered content, as reported by native.
    private int mContentHeightCss;
    private int mContentWidthCss;

    // Page scale factor. This is set to zero initially so that we don't attempt to do a layout if
    // we get the content size change notification first and a page scale change second.
    private double mPageScaleFactor = 0.0;

    // Whether to postpone layout requests.
    private boolean mFreezeLayoutRequests;
    // Did we try to request a layout since the last time mPostponeLayoutRequests was set to true.
    private boolean mFrozenLayoutRequestPending;

    private double mDIPScale;

    // Callback object for interacting with the View.
    private Delegate mDelegate;

    public interface Delegate {
        void requestLayout();
        void setMeasuredDimension(int measuredWidth, int measuredHeight);
    }

    /**
     * Default constructor. Note: both setDelegate and setDIPScale must be called before the class
     * is ready for use.
     */
    public AwLayoutSizer() {
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    public void setDIPScale(double dipScale) {
        mDIPScale = dipScale;
    }

    /**
     * This is used to register the AwLayoutSizer to preferred content size change notifications in
     * the AwWebContentsDelegate.
     */
    public AwWebContentsDelegateAdapter.PreferredSizeChangedListener
            getPreferredSizeChangedListener() {
        return new AwWebContentsDelegateAdapter.PreferredSizeChangedListener() {
            @Override
            public void updatePreferredSize(int widthCss, int heightCss) {
                onContentSizeChanged(widthCss, heightCss);
            }
        };
    }

    /**
     * Postpone requesting layouts till unfreezeLayoutRequests is called.
     */
    public void freezeLayoutRequests() {
        mFreezeLayoutRequests = true;
        mFrozenLayoutRequestPending = false;
    }

    /**
     * Stop postponing layout requests and request layout if such a request would have been made
     * had the freezeLayoutRequests method not been called before.
     */
    public void unfreezeLayoutRequests() {
        mFreezeLayoutRequests = false;
        if (mFrozenLayoutRequestPending) {
            mFrozenLayoutRequestPending = false;
            mDelegate.requestLayout();
        }
    }

    /**
     * Update the contents size.
     * This should be called whenever the content size changes (due to DOM manipulation or page
     * load, for example).
     * The width and height should be in CSS pixels.
     */
    public void onContentSizeChanged(int widthCss, int heightCss) {
        doUpdate(widthCss, heightCss, mPageScaleFactor);
    }

    /**
     * Update the contents page scale.
     * This should be called whenever the content page scale factor changes (due to pinch zoom, for
     * example).
     */
    public void onPageScaleChanged(double pageScaleFactor) {
        doUpdate(mContentWidthCss, mContentHeightCss, pageScaleFactor);
    }

    private void doUpdate(int widthCss, int heightCss, double pageScaleFactor) {
        // We want to request layout only if the size or scale change, however if any of the
        // measurements are 'fixed', then changing the underlying size won't have any effect, so we
        // ignore changes to dimensions that are 'fixed'.
        boolean anyMeasurementNotFixed = !mWidthMeasurementIsFixed || !mHeightMeasurementIsFixed;
        boolean layoutNeeded = (mContentWidthCss != widthCss && !mWidthMeasurementIsFixed) ||
            (mContentHeightCss != heightCss && !mHeightMeasurementIsFixed) ||
            (mPageScaleFactor != pageScaleFactor && anyMeasurementNotFixed);

        mContentWidthCss = widthCss;
        mContentHeightCss = heightCss;
        mPageScaleFactor = pageScaleFactor;

        if (layoutNeeded) {
            if (mFreezeLayoutRequests) {
                mFrozenLayoutRequestPending = true;
            } else {
                mDelegate.requestLayout();
            }
        }
    }

    /**
     * Calculate the size of the view.
     * This is designed to be used to implement the android.view.View#onMeasure() method.
     */
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int measuredHeight = heightSize;
        int measuredWidth = widthSize;

        int contentHeightPix = (int) (mContentHeightCss * mPageScaleFactor * mDIPScale);
        int contentWidthPix = (int) (mContentWidthCss * mPageScaleFactor * mDIPScale);

        // Always use the given size unless unspecified. This matches WebViewClassic behavior.
        mWidthMeasurementIsFixed = (widthMode != MeasureSpec.UNSPECIFIED);
        // Freeze the height if an exact size is given by the parent or if the content size has
        // exceeded the maximum size specified by the parent.
        // TODO(mkosiba): Actually we'd like the reduction in content size to cause the WebView to
        // shrink back again but only as a result of a page load.
        mHeightMeasurementIsFixed = (heightMode == MeasureSpec.EXACTLY) ||
            (heightMode == MeasureSpec.AT_MOST && contentHeightPix > heightSize);

        if (!mHeightMeasurementIsFixed) {
            measuredHeight = contentHeightPix;
        }

        if (!mWidthMeasurementIsFixed) {
            measuredWidth = contentWidthPix;
        }

        if (measuredHeight < contentHeightPix) {
            measuredHeight |= View.MEASURED_STATE_TOO_SMALL;
        }

        if (measuredWidth < contentWidthPix) {
            measuredWidth |= View.MEASURED_STATE_TOO_SMALL;
        }

        mDelegate.setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
