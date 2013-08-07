// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.http.SslCertificate;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.widget.OverScroller;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.ContentSettings;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.browser.NavigationHistory;
import org.chromium.content.browser.PageTransitionTypes;
import org.chromium.content.common.CleanupReference;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.net.GURLUtils;
import org.chromium.ui.gfx.DeviceDisplayInfo;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * Exposes the native AwContents class, and together these classes wrap the ContentViewCore
 * and Browser components that are required to implement Android WebView API. This is the
 * primary entry point for the WebViewProvider implementation; it holds a 1:1 object
 * relationship with application WebView instances.
 * (We define this class independent of the hidden WebViewProvider interfaces, to allow
 * continuous build & test in the open source SDK-based tree).
 */
@JNINamespace("android_webview")
public class AwContents {
    private static final String TAG = "AwContents";

    private static final String WEB_ARCHIVE_EXTENSION = ".mht";

    /**
     * WebKit hit test related data strcutre. These are used to implement
     * getHitTestResult, requestFocusNodeHref, requestImageRef methods in WebView.
     * All values should be updated together. The native counterpart is
     * AwHitTestData.
     */
    public static class HitTestData {
        // Used in getHitTestResult.
        public int hitTestResultType;
        public String hitTestResultExtraData;

        // Used in requestFocusNodeHref (all three) and requestImageRef (only imgSrc).
        public String href;
        public String anchorText;
        public String imgSrc;
    }

    /**
     * Interface that consumers of {@link AwContents} must implement to allow the proper
     * dispatching of view methods through the containing view.
     */
    public interface InternalAccessDelegate extends ContentViewCore.InternalAccessDelegate {
        /**
         * @see View#onScrollChanged(int, int, int, int)
         *
         * TODO(mkosiba): WebViewClassic calls this, AwContents doesn't. Check if there
         * are any cases we're missing, if not - remove.
         */
        void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix);

        /**
         * @see View#overScrollBy(int, int, int, int, int, int, int, int, boolean);
         */
        void overScrollBy(int deltaX, int deltaY,
                int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY,
                int maxOverScrollX, int maxOverScrollY,
                boolean isTouchEvent);

        /**
         * @see View#scrollTo(int, int)
         */
        void super_scrollTo(int scrollX, int scrollY);

        /**
         * @see View#setMeasuredDimension(int, int)
         */
        void setMeasuredDimension(int measuredWidth, int measuredHeight);

        /**
         * Requests a callback on the native DrawGL method (see getAwDrawGLFunction)
         * if called from within onDraw, |canvas| will be non-null and hardware accelerated.
         * otherwise, |canvas| will be null, and the container view itself will be hardware
         * accelerated.
         *
         * @return false indicates the GL draw request was not accepted, and the caller
         *         should fallback to the SW path.
         */
        boolean requestDrawGL(Canvas canvas);
    }

    private int mNativeAwContents;
    private final AwBrowserContext mBrowserContext;
    private final ViewGroup mContainerView;
    private ContentViewCore mContentViewCore;
    private final AwContentsClient mContentsClient;
    private final AwContentsClientBridge mContentsClientBridge;
    private final AwWebContentsDelegate mWebContentsDelegate;
    private final AwContentsIoThreadClient mIoThreadClient;
    private final InterceptNavigationDelegateImpl mInterceptNavigationDelegate;
    private final InternalAccessDelegate mInternalAccessAdapter;
    private final AwLayoutSizer mLayoutSizer;
    private final AwZoomControls mZoomControls;
    private final AwScrollOffsetManager mScrollOffsetManager;
    private OverScrollGlow mOverScrollGlow;
    // This can be accessed on any thread after construction. See AwContentsIoThreadClient.
    private final AwSettings mSettings;

    private boolean mIsVisible;  // Equivalent to windowVisible && viewVisible.
    private boolean mIsAttachedToWindow;
    private Bitmap mFavicon;
    private boolean mHasRequestedVisitedHistoryFromClient;
    // TODO(boliu): This should be in a global context, not per webview.
    private final double mDIPScale;

    // The base background color, i.e. not accounting for any CSS body from the current page.
    private int mBaseBackgroundColor = Color.WHITE;

    // Must call nativeUpdateLastHitTestData first to update this before use.
    private final HitTestData mPossiblyStaleHitTestData = new HitTestData();

    private DefaultVideoPosterRequestHandler mDefaultVideoPosterRequestHandler;

    // Bound method for suppling Picture instances to the AwContentsClient. Will be null if the
    // picture listener API has not yet been enabled, or if it is using invalidation-only mode.
    private Callable<Picture> mPictureListenerContentProvider;

    private boolean mContainerViewFocused;
    private boolean mWindowFocused;

    private AwAutofillManagerDelegate mAwAutofillManagerDelegate;

    private static final class DestroyRunnable implements Runnable {
        private int mNativeAwContents;
        private DestroyRunnable(int nativeAwContents) {
            mNativeAwContents = nativeAwContents;
        }
        @Override
        public void run() {
            nativeDestroy(mNativeAwContents);
        }
    }

    private CleanupReference mCleanupReference;

    //--------------------------------------------------------------------------------------------
    private class IoThreadClientImpl implements AwContentsIoThreadClient {
        // All methods are called on the IO thread.

        @Override
        public int getCacheMode() {
            return mSettings.getCacheMode();
        }

        @Override
        public InterceptedRequestData shouldInterceptRequest(final String url,
                boolean isMainFrame) {
            InterceptedRequestData interceptedRequestData;
            // Return the response directly if the url is default video poster url.
            interceptedRequestData = mDefaultVideoPosterRequestHandler.shouldInterceptRequest(url);
            if (interceptedRequestData != null) return interceptedRequestData;

            interceptedRequestData = mContentsClient.shouldInterceptRequest(url);

            if (interceptedRequestData == null) {
                mContentsClient.getCallbackHelper().postOnLoadResource(url);
            }

            if (isMainFrame && interceptedRequestData != null &&
                    interceptedRequestData.getData() == null) {
                // In this case the intercepted URLRequest job will simulate an empty response
                // which doesn't trigger the onReceivedError callback. For WebViewClassic
                // compatibility we synthesize that callback. http://crbug.com/180950
                mContentsClient.getCallbackHelper().postOnReceivedError(
                        ErrorCodeConversionHelper.ERROR_UNKNOWN,
                        null /* filled in by the glue layer */, url);
            }
            return interceptedRequestData;
        }

        @Override
        public boolean shouldBlockContentUrls() {
            return !mSettings.getAllowContentAccess();
        }

        @Override
        public boolean shouldBlockFileUrls() {
            return !mSettings.getAllowFileAccess();
        }

        @Override
        public boolean shouldBlockNetworkLoads() {
            return mSettings.getBlockNetworkLoads();
        }

        @Override
        public void onDownloadStart(String url,
                                    String userAgent,
                                    String contentDisposition,
                                    String mimeType,
                                    long contentLength) {
            mContentsClient.getCallbackHelper().postOnDownloadStart(url, userAgent,
                    contentDisposition, mimeType, contentLength);
        }

        @Override
        public void newLoginRequest(String realm, String account, String args) {
            mContentsClient.getCallbackHelper().postOnReceivedLoginRequest(realm, account, args);
        }
    }

    //--------------------------------------------------------------------------------------------
    private class InterceptNavigationDelegateImpl implements InterceptNavigationDelegate {
        private String mLastLoadUrlAddress;

        public void onUrlLoadRequested(String url) {
            mLastLoadUrlAddress = url;
        }

        @Override
        public boolean shouldIgnoreNavigation(NavigationParams navigationParams) {
            final String url = navigationParams.url;
            final int transitionType = navigationParams.pageTransitionType;
            final boolean isLoadUrl =
                    (transitionType & PageTransitionTypes.PAGE_TRANSITION_FROM_API) != 0;
            final boolean isBackForward =
                    (transitionType & PageTransitionTypes.PAGE_TRANSITION_FORWARD_BACK) != 0;
            final boolean isReload =
                    (transitionType & PageTransitionTypes.PAGE_TRANSITION_CORE_MASK) ==
                    PageTransitionTypes.PAGE_TRANSITION_RELOAD;
            final boolean isRedirect = navigationParams.isRedirect;

            boolean ignoreNavigation = false;

            // Any navigation from loadUrl, goBack/Forward, or reload, are considered application
            // initiated and hence will not yield a shouldOverrideUrlLoading() callback.
            // TODO(joth): Using PageTransitionTypes should be sufficient to determine all app
            // initiated navigations, and so mLastLoadUrlAddress should be removed.
            if ((isLoadUrl && !isRedirect) || isBackForward || isReload ||
                    mLastLoadUrlAddress != null && mLastLoadUrlAddress.equals(url)) {
                // Support the case where the user clicks on a link that takes them back to the
                // same page.
                mLastLoadUrlAddress = null;

                // If the embedder requested the load of a certain URL via the loadUrl API, then we
                // do not offer it to AwContentsClient.shouldOverrideUrlLoading.
                // The embedder is also not allowed to intercept POST requests because of
                // crbug.com/155250.
            } else if (!navigationParams.isPost) {
                ignoreNavigation = mContentsClient.shouldOverrideUrlLoading(url);
            }

            // The existing contract is that shouldOverrideUrlLoading callbacks are delivered before
            // onPageStarted callbacks; third party apps depend on this behavior.
            // Using a ResouceThrottle to implement the navigation interception feature results in
            // the WebContentsObserver.didStartLoading callback happening before the
            // ResourceThrottle has a chance to run.
            // To preserve the ordering the onPageStarted callback is synthesized from the
            // shouldOverrideUrlLoading, and only if the navigation was not ignored (this
            // balances out with the onPageFinished callback, which is suppressed in the
            // AwContentsClient if the navigation was ignored).
            if (!ignoreNavigation) {
                // The shouldOverrideUrlLoading call might have resulted in posting messages to the
                // UI thread. Using sendMessage here (instead of calling onPageStarted directly)
                // will allow those to run in order.
                mContentsClient.getCallbackHelper().postOnPageStarted(url);
            }

            return ignoreNavigation;
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwLayoutSizerDelegate implements AwLayoutSizer.Delegate {
        @Override
        public void requestLayout() {
            mContainerView.requestLayout();
        }

        @Override
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            mInternalAccessAdapter.setMeasuredDimension(measuredWidth, measuredHeight);
        }
    }

    //--------------------------------------------------------------------------------------------
    // NOTE: This content size change notification comes from the compositor and reflects the size
    // of the content on screen (but not neccessarily in the renderer main thread).
    private class AwContentUpdateFrameInfoListener
                implements ContentViewCore.UpdateFrameInfoListener {
        @Override
        public void onFrameInfoUpdated(float widthCss, float heightCss, float pageScaleFactor) {
            int widthPix = (int) Math.floor(widthCss * mDIPScale * pageScaleFactor);
            int heightPix = (int) Math.floor(heightCss * mDIPScale * pageScaleFactor);
            mScrollOffsetManager.setContentSize(widthPix, heightPix);

            nativeSetDisplayedPageScaleFactor(mNativeAwContents, pageScaleFactor);
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwScrollOffsetManagerDelegate implements AwScrollOffsetManager.Delegate {
        @Override
        public void overScrollContainerViewBy(int deltaX, int deltaY, int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY, boolean isTouchEvent) {
            mInternalAccessAdapter.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                    scrollRangeX, scrollRangeY, 0, 0, isTouchEvent);
        }

        @Override
        public void scrollContainerViewTo(int x, int y) {
            mInternalAccessAdapter.super_scrollTo(x, y);
        }

        @Override
        public void scrollNativeTo(int x, int y) {
            nativeScrollTo(mNativeAwContents, x, y);
        }

        @Override
        public int getContainerViewScrollX() {
            return mContainerView.getScrollX();
        }

        @Override
        public int getContainerViewScrollY() {
            return mContainerView.getScrollY();
        }

        @Override
        public void invalidate() {
            mContainerView.invalidate();
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwGestureStateListener implements ContentViewCore.GestureStateListener {
        @Override
        public void onPinchGestureStart() {
            // While it's possible to re-layout the view during a pinch gesture, the effect is very
            // janky (especially that the page scale update notification comes from the renderer
            // main thread, not from the impl thread, so it's usually out of sync with what's on
            // screen). It's also quite expensive to do a re-layout, so we simply postpone
            // re-layout for the duration of the gesture. This is compatible with what
            // WebViewClassic does.
            mLayoutSizer.freezeLayoutRequests();
        }

        @Override
        public void onPinchGestureEnd() {
            mLayoutSizer.unfreezeLayoutRequests();
        }

        @Override
        public void onFlingStartGesture(int velocityX, int velocityY) {
            mScrollOffsetManager.onFlingStartGesture(velocityX, velocityY);
        }


        @Override
        public void onFlingCancelGesture() {
            mScrollOffsetManager.onFlingCancelGesture();
        }

        @Override
        public void onUnhandledFlingStartEvent() {
            mScrollOffsetManager.onUnhandledFlingStartEvent();
        }
    }

    /**
     * @param browserContext the browsing context to associate this view contents with.
     * @param containerView the view-hierarchy item this object will be bound to.
     * @param internalAccessAdapter to access private methods on containerView.
     * @param contentsClient will receive API callbacks from this WebView Contents
     * @param isAccessFromFileURLsGrantedByDefault passed to AwSettings.
     *
     * This constructor uses the default view sizing policy.
     */
    public AwContents(AwBrowserContext browserContext, ViewGroup containerView,
            InternalAccessDelegate internalAccessAdapter, AwContentsClient contentsClient,
            boolean isAccessFromFileURLsGrantedByDefault) {
        this(browserContext, containerView, internalAccessAdapter, contentsClient,
                isAccessFromFileURLsGrantedByDefault, new AwLayoutSizer());
    }

    private static ContentViewCore createAndInitializeContentViewCore(ViewGroup containerView,
            InternalAccessDelegate internalDispatcher, int nativeWebContents,
            ContentViewCore.GestureStateListener pinchGestureStateListener,
            ContentViewClient contentViewClient,
            ContentViewCore.ZoomControlsDelegate zoomControlsDelegate) {
      ContentViewCore contentViewCore = new ContentViewCore(containerView.getContext());
      // Note INPUT_EVENTS_DELIVERED_IMMEDIATELY is passed to avoid triggering vsync in the
      // compositor, not because input events are delivered immediately.
      contentViewCore.initialize(containerView, internalDispatcher, nativeWebContents, null,
                ContentViewCore.INPUT_EVENTS_DELIVERED_IMMEDIATELY);
      contentViewCore.setGestureStateListener(pinchGestureStateListener);
      contentViewCore.setContentViewClient(contentViewClient);
      contentViewCore.setZoomControlsDelegate(zoomControlsDelegate);
      return contentViewCore;
    }

    /**
     * @param layoutSizer the AwLayoutSizer instance implementing the sizing policy for the view.
     *
     * This version of the constructor is used in test code to inject test versions of the above
     * documented classes
     */
    public AwContents(AwBrowserContext browserContext, ViewGroup containerView,
            InternalAccessDelegate internalAccessAdapter, AwContentsClient contentsClient,
            boolean isAccessFromFileURLsGrantedByDefault, AwLayoutSizer layoutSizer) {
        mBrowserContext = browserContext;
        mContainerView = containerView;
        mInternalAccessAdapter = internalAccessAdapter;
        mContentsClient = contentsClient;
        mLayoutSizer = layoutSizer;
        mDIPScale = DeviceDisplayInfo.create(containerView.getContext()).getDIPScale();
        mLayoutSizer.setDelegate(new AwLayoutSizerDelegate());
        mLayoutSizer.setDIPScale(mDIPScale);
        mWebContentsDelegate = new AwWebContentsDelegateAdapter(contentsClient,
                mLayoutSizer.getPreferredSizeChangedListener());
        mContentsClientBridge = new AwContentsClientBridge(contentsClient);
        mZoomControls = new AwZoomControls(this);
        mIoThreadClient = new IoThreadClientImpl();
        mInterceptNavigationDelegate = new InterceptNavigationDelegateImpl();

        boolean hasInternetPermission = containerView.getContext().checkPermission(
                    android.Manifest.permission.INTERNET,
                    Process.myPid(),
                    Process.myUid()) == PackageManager.PERMISSION_GRANTED;
        AwSettings.ZoomSupportChangeListener zoomListener =
                new AwSettings.ZoomSupportChangeListener() {
                    @Override
                    public void onMultiTouchZoomSupportChanged(boolean supportsMultiTouchZoom) {
                        mContentViewCore.updateMultiTouchZoomSupport(supportsMultiTouchZoom);
                    }
                };
        mSettings = new AwSettings(mContainerView.getContext(), hasInternetPermission, zoomListener,
                isAccessFromFileURLsGrantedByDefault, mDIPScale);
        mDefaultVideoPosterRequestHandler = new DefaultVideoPosterRequestHandler(mContentsClient);
        mSettings.setDefaultVideoPosterURL(
                mDefaultVideoPosterRequestHandler.getDefaultVideoPosterURL());
        mContentsClient.setDIPScale(mDIPScale);
        mScrollOffsetManager = new AwScrollOffsetManager(new AwScrollOffsetManagerDelegate(),
                new OverScroller(mContainerView.getContext()));

        setOverScrollMode(mContainerView.getOverScrollMode());

        setNewAwContents(nativeInit(browserContext));
    }

    /**
     * Common initialization routine for adopting a native AwContents instance into this
     * java instance.
     *
     * TAKE CARE! This method can get called multiple times per java instance. Code accordingly.
     * ^^^^^^^^^  See the native class declaration for more details on relative object lifetimes.
     */
    private void setNewAwContents(int newAwContentsPtr) {
        if (mNativeAwContents != 0) {
            destroy();
            mContentViewCore = null;
        }

        assert mNativeAwContents == 0 && mCleanupReference == null && mContentViewCore == null;

        mNativeAwContents = newAwContentsPtr;
        // TODO(joth): when the native and java counterparts of AwBrowserContext are hooked up to
        // each other, we should update |mBrowserContext| according to the newly received native
        // WebContent's browser context.

        // The native side object has been bound to this java instance, so now is the time to
        // bind all the native->java relationships.
        mCleanupReference = new CleanupReference(this, new DestroyRunnable(mNativeAwContents));

        int nativeWebContents = nativeGetWebContents(mNativeAwContents);
        mContentViewCore = createAndInitializeContentViewCore(
                mContainerView, mInternalAccessAdapter, nativeWebContents,
                new AwGestureStateListener(), mContentsClient.getContentViewClient(),
                mZoomControls);
        nativeSetJavaPeers(mNativeAwContents, this, mWebContentsDelegate, mContentsClientBridge,
                mIoThreadClient, mInterceptNavigationDelegate);
        mContentsClient.installWebContentsObserver(mContentViewCore);
        mContentViewCore.setUpdateFrameInfoListener(new AwContentUpdateFrameInfoListener());
        mSettings.setWebContents(nativeWebContents);
        nativeSetDipScale(mNativeAwContents, (float) mDIPScale);
        updateGlobalVisibleRect();

        // The only call to onShow. onHide should never be called.
        mContentViewCore.onShow();
   }

    /**
     * Called on the "source" AwContents that is opening the popup window to
     * provide the AwContents to host the pop up content.
     */
    public void supplyContentsForPopup(AwContents newContents) {
        int popupNativeAwContents = nativeReleasePopupAwContents(mNativeAwContents);
        if (popupNativeAwContents == 0) {
            Log.w(TAG, "Popup WebView bind failed: no pending content.");
            if (newContents != null) newContents.destroy();
            return;
        }
        if (newContents == null) {
            nativeDestroy(popupNativeAwContents);
            return;
        }

        newContents.receivePopupContents(popupNativeAwContents);
    }

    // Recap: supplyContentsForPopup() is called on the parent window's content, this method is
    // called on the popup window's content.
    private void receivePopupContents(int popupNativeAwContents) {
        // Save existing view state.
        final boolean wasAttached = mIsAttachedToWindow;
        final boolean wasVisible = getContainerViewVisible();
        final boolean wasPaused = mUnimplementedIsPaused;
        final boolean wasFocused = mWindowFocused;

        // Properly clean up existing mContentViewCore and mNativeAwContents.
        if (wasFocused) onWindowFocusChanged(false);
        if (wasVisible) setVisibilityInternal(false);
        // TODO(boliu): This may destroy GL resources outside of functor.
        if (wasAttached) onDetachedFromWindow();
        if (!wasPaused) onPause();

        setNewAwContents(popupNativeAwContents);

        // Finally refresh all view state for mContentViewCore and mNativeAwContents.
        if (!wasPaused) onResume();
        if (wasAttached) onAttachedToWindow();
        onSizeChanged(mContainerView.getWidth(), mContainerView.getHeight(), 0, 0);
        if (wasVisible) setVisibilityInternal(true);
        if (wasFocused) onWindowFocusChanged(true);
    }

    public void destroy() {
        mContentViewCore.destroy();
        // We explicitly do not null out the mContentViewCore reference here
        // because ContentViewCore already has code to deal with the case
        // methods are called on it after it's been destroyed, and other
        // code relies on AwContents.mContentViewCore to be non-null.

        if (mCleanupReference != null) mCleanupReference.cleanupNow();
        mNativeAwContents = 0;
        mCleanupReference = null;
    }

    @VisibleForTesting
    public ContentViewCore getContentViewCore() {
        return mContentViewCore;
    }

    // Can be called from any thread.
    public AwSettings getSettings() {
        return mSettings;
    }

    public static void setAwDrawSWFunctionTable(int functionTablePointer) {
        nativeSetAwDrawSWFunctionTable(functionTablePointer);
    }

    public static void setAwDrawGLFunctionTable(int functionTablePointer) {
        nativeSetAwDrawGLFunctionTable(functionTablePointer);
    }

    public static int getAwDrawGLFunction() {
        return nativeGetAwDrawGLFunction();
    }

    /**
     * Intended for test code.
     * @return the number of native instances of this class.
     */
    @VisibleForTesting
    public static int getNativeInstanceCount() {
        return nativeGetNativeInstanceCount();
    }

    public int getAwDrawGLViewContext() {
        // Using the native pointer as the returned viewContext. This is matched by the
        // reinterpret_cast back to BrowserViewRenderer pointer in the native DrawGLFunction.
        return nativeGetAwDrawGLViewContext(mNativeAwContents);
    }

    // This is only to avoid heap allocations inside updateGLobalVisibleRect. It should treated
    // as a local variable in the function and not used anywhere else.
    private static final Rect sLocalGlobalVisibleRect = new Rect();

    @CalledByNative
    private void updateGlobalVisibleRect() {
        mContainerView.getGlobalVisibleRect(sLocalGlobalVisibleRect);
        nativeSetGlobalVisibleRect(mNativeAwContents, sLocalGlobalVisibleRect.left,
                sLocalGlobalVisibleRect.top, sLocalGlobalVisibleRect.right,
                sLocalGlobalVisibleRect.bottom);
    }

    //--------------------------------------------------------------------------------------------
    //  WebView[Provider] method implementations (where not provided by ContentViewCore)
    //--------------------------------------------------------------------------------------------

    // Only valid within onDraw().
    private final Rect mClipBoundsTemporary = new Rect();

    public void onDraw(Canvas canvas) {
        if (mNativeAwContents == 0) {
            canvas.drawColor(getEffectiveBackgroundColor());
            return;
        }

        mScrollOffsetManager.syncScrollOffsetFromOnDraw();

        canvas.getClipBounds(mClipBoundsTemporary);
        if (!nativeOnDraw(mNativeAwContents, canvas, canvas.isHardwareAccelerated(),
                    mContainerView.getScrollX(), mContainerView.getScrollY(),
                    mClipBoundsTemporary.left, mClipBoundsTemporary.top,
                    mClipBoundsTemporary.right, mClipBoundsTemporary.bottom)) {
            Log.w(TAG, "nativeOnDraw failed; clearing to background color.");
            canvas.drawColor(getEffectiveBackgroundColor());
        }

        if (mOverScrollGlow != null && mOverScrollGlow.drawEdgeGlows(canvas,
                    mScrollOffsetManager.computeMaximumHorizontalScrollOffset(),
                    mScrollOffsetManager.computeMaximumVerticalScrollOffset())) {
            mContainerView.invalidate();
        }
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mLayoutSizer.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public int getContentHeightCss() {
        return (int) Math.ceil(mContentViewCore.getContentHeightCss());
    }

    public int getContentWidthCss() {
        return (int) Math.ceil(mContentViewCore.getContentWidthCss());
    }

    public Picture capturePicture() {
        return nativeCapturePicture(mNativeAwContents,
                mScrollOffsetManager.computeHorizontalScrollRange(),
                mScrollOffsetManager.computeVerticalScrollRange());
    }

    /**
     * Enable the onNewPicture callback.
     * @param enabled Flag to enable the callback.
     * @param invalidationOnly Flag to call back only on invalidation without providing a picture.
     */
    public void enableOnNewPicture(boolean enabled, boolean invalidationOnly) {
        if (invalidationOnly) {
            mPictureListenerContentProvider = null;
        } else if (enabled && mPictureListenerContentProvider == null) {
            mPictureListenerContentProvider = new Callable<Picture>() {
                @Override
                public Picture call() {
                    return capturePicture();
                }
            };
        }
        nativeEnableOnNewPicture(mNativeAwContents, enabled);
    }

    public void findAllAsync(String searchString) {
        if (mNativeAwContents == 0) return;
        nativeFindAllAsync(mNativeAwContents, searchString);
    }

    public void findNext(boolean forward) {
        if (mNativeAwContents == 0) return;
        nativeFindNext(mNativeAwContents, forward);
    }

    public void clearMatches() {
        if (mNativeAwContents == 0) return;
        nativeClearMatches(mNativeAwContents);
    }

    /**
     * @return load progress of the WebContents.
     */
    public int getMostRecentProgress() {
        // WebContentsDelegateAndroid conveniently caches the most recent notified value for us.
        return mWebContentsDelegate.getMostRecentProgress();
    }

    public Bitmap getFavicon() {
        return mFavicon;
    }

    private void requestVisitedHistoryFromClient() {
        ValueCallback<String[]> callback = new ValueCallback<String[]>() {
            @Override
            public void onReceiveValue(final String[] value) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mNativeAwContents == 0) return;
                        nativeAddVisitedLinks(mNativeAwContents, value);
                    }
                });
            }
        };
        mContentsClient.getVisitedHistory(callback);
    }

    /**
     * Load url without fixing up the url string. Consumers of ContentView are responsible for
     * ensuring the URL passed in is properly formatted (i.e. the scheme has been added if left
     * off during user input).
     *
     * @param pararms Parameters for this load.
     */
    public void loadUrl(LoadUrlParams params) {
        if (params.getLoadUrlType() == LoadUrlParams.LOAD_TYPE_DATA &&
            !params.isBaseUrlDataScheme()) {
            // This allows data URLs with a non-data base URL access to file:///android_asset/ and
            // file:///android_res/ URLs. If AwSettings.getAllowFileAccess permits, it will also
            // allow access to file:// URLs (subject to OS level permission checks).
            params.setCanLoadLocalResources(true);
        }

        // If we are reloading the same url, then set transition type as reload.
        if (params.getUrl() != null &&
            params.getUrl().equals(mContentViewCore.getUrl()) &&
            params.getTransitionType() == PageTransitionTypes.PAGE_TRANSITION_LINK) {
            params.setTransitionType(PageTransitionTypes.PAGE_TRANSITION_RELOAD);
        }
        params.setTransitionType(
                params.getTransitionType() | PageTransitionTypes.PAGE_TRANSITION_FROM_API);

        // For WebView, always use the user agent override, which is set
        // every time the user agent in AwSettings is modified.
        params.setOverrideUserAgent(LoadUrlParams.UA_OVERRIDE_TRUE);

        mContentViewCore.loadUrl(params);

        suppressInterceptionForThisNavigation();

        // The behavior of WebViewClassic uses the populateVisitedLinks callback in WebKit.
        // Chromium does not use this use code path and the best emulation of this behavior to call
        // request visited links once on the first URL load of the WebView.
        if (!mHasRequestedVisitedHistoryFromClient) {
          mHasRequestedVisitedHistoryFromClient = true;
          requestVisitedHistoryFromClient();
        }
    }

    private void suppressInterceptionForThisNavigation() {
        if (mInterceptNavigationDelegate != null) {
            // getUrl returns a sanitized address in the same format that will be used for
            // callbacks, so it's safe to use string comparison as an equality check later on.
            mInterceptNavigationDelegate.onUrlLoadRequested(mContentViewCore.getUrl());
        }
    }

    /**
     * Get the URL of the current page.
     *
     * @return The URL of the current page or null if it's empty.
     */
    public String getUrl() {
        String url =  mContentViewCore.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    public void requestFocus() {
        if (!mContainerView.isInTouchMode() && mSettings.shouldFocusFirstNode()) {
            nativeFocusFirstNode(mNativeAwContents);
        }
    }

    public void setBackgroundColor(int color) {
        mBaseBackgroundColor = color;
        if (mNativeAwContents != 0) nativeSetBackgroundColor(mNativeAwContents, color);
    }

    private int getEffectiveBackgroundColor() {
        // Do not ask the ContentViewCore for the background color, as it will always
        // report white prior to initial navigation or post destruction,  whereas we want
        // to use the client supplied base value in those cases.
        if (mNativeAwContents == 0 || !mContentsClient.isCachedRendererBackgroundColorValid()) {
            return mBaseBackgroundColor;
        }
        return mContentsClient.getCachedRendererBackgroundColor();
    }

    public boolean isMultiTouchZoomSupported() {
        return mSettings.supportsMultiTouchZoom();
    }

    public View getZoomControlsForTest() {
        return mZoomControls.getZoomControlsViewForTest();
    }

    /**
     * @see ContentViewCore#getContentSettings()
     */
    public ContentSettings getContentSettings() {
        return mContentViewCore.getContentSettings();
    }

    /**
     * @see View#setOverScrollMode(int)
     */
    public void setOverScrollMode(int mode) {
        if (mode != View.OVER_SCROLL_NEVER) {
            mOverScrollGlow = new OverScrollGlow(mContainerView);
        } else {
            mOverScrollGlow = null;
        }
    }

    /**
     * Called by the embedder when the scroll offset of the containing view has changed.
     * @see View#onScrollChanged(int,int)
     */
    public void onContainerViewScrollChanged(int l, int t, int oldl, int oldt) {
        mScrollOffsetManager.onContainerViewScrollChanged(l, t);
    }

    /**
     * Called by the embedder when the containing view is to be scrolled or overscrolled.
     * @see View#onOverScrolled(int,int,int,int)
     */
    public void onContainerViewOverScrolled(int scrollX, int scrollY, boolean clampedX,
            boolean clampedY) {
        int oldX = mContainerView.getScrollX();
        int oldY = mContainerView.getScrollY();

        mScrollOffsetManager.onContainerViewOverScrolled(scrollX, scrollY, clampedX, clampedY);

        if (mOverScrollGlow != null) {
            mOverScrollGlow.pullGlow(mContainerView.getScrollX(), mContainerView.getScrollY(),
                    oldX, oldY,
                    mScrollOffsetManager.computeMaximumHorizontalScrollOffset(),
                    mScrollOffsetManager.computeMaximumVerticalScrollOffset());
        }
    }

    /**
     * @see View.computeScroll()
     */
    public void computeScroll() {
        mScrollOffsetManager.computeScrollAndAbsorbGlow(mOverScrollGlow);
    }

    /**
     * @see View#computeHorizontalScrollRange()
     */
    public int computeHorizontalScrollRange() {
        return mScrollOffsetManager.computeHorizontalScrollRange();
    }

    /**
     * @see View#computeHorizontalScrollOffset()
     */
    public int computeHorizontalScrollOffset() {
        return mScrollOffsetManager.computeHorizontalScrollOffset();
    }

    /**
     * @see View#computeVerticalScrollRange()
     */
    public int computeVerticalScrollRange() {
        return mScrollOffsetManager.computeVerticalScrollRange();
    }

    /**
     * @see View#computeVerticalScrollOffset()
     */
    public int computeVerticalScrollOffset() {
        return mScrollOffsetManager.computeVerticalScrollOffset();
    }

    /**
     * @see View#computeVerticalScrollExtent()
     */
    public int computeVerticalScrollExtent() {
        return mScrollOffsetManager.computeVerticalScrollExtent();
    }

    /**
     * @see android.webkit.WebView#stopLoading()
     */
    public void stopLoading() {
        mContentViewCore.stopLoading();
    }

    /**
     * @see android.webkit.WebView#reload()
     */
    public void reload() {
        mContentViewCore.reload();
    }

    /**
     * @see android.webkit.WebView#canGoBack()
     */
    public boolean canGoBack() {
        return mContentViewCore.canGoBack();
    }

    /**
     * @see android.webkit.WebView#goBack()
     */
    public void goBack() {
        mContentViewCore.goBack();

        suppressInterceptionForThisNavigation();
    }

    /**
     * @see android.webkit.WebView#canGoForward()
     */
    public boolean canGoForward() {
        return mContentViewCore.canGoForward();
    }

    /**
     * @see android.webkit.WebView#goForward()
     */
    public void goForward() {
        mContentViewCore.goForward();

        suppressInterceptionForThisNavigation();
    }

    /**
     * @see android.webkit.WebView#canGoBackOrForward(int)
     */
    public boolean canGoBackOrForward(int steps) {
        return mContentViewCore.canGoToOffset(steps);
    }

    /**
     * @see android.webkit.WebView#goBackOrForward(int)
     */
    public void goBackOrForward(int steps) {
        mContentViewCore.goToOffset(steps);

        suppressInterceptionForThisNavigation();
    }

    /**
     * @see android.webkit.WebView#pauseTimers()
     */
    public void pauseTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(true);
    }

    /**
     * @see android.webkit.WebView#resumeTimers()
     */
    public void resumeTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(false);
    }

    private boolean mUnimplementedIsPaused;

    /**
     * @see android.webkit.WebView#onPause()
     */
    public void onPause() {
        mUnimplementedIsPaused = true;
    }

    /**
     * @see android.webkit.WebView#onResume()
     */
    public void onResume() {
        mUnimplementedIsPaused = false;
    }

    /**
     * @see android.webkit.WebView#isPaused()
     */
    public boolean isPaused() {
        return mUnimplementedIsPaused;
    }

    /**
     * @see android.webkit.WebView#onCreateInputConnection(EditorInfo)
     */
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mContentViewCore.onCreateInputConnection(outAttrs);
    }

    /**
     * @see android.webkit.WebView#onKeyUp(int, KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mContentViewCore.onKeyUp(keyCode, event);
    }

    /**
     * @see android.webkit.WebView#dispatchKeyEvent(KeyEvent)
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mContentViewCore.dispatchKeyEvent(event);
    }

    /**
     * Clears the resource cache. Note that the cache is per-application, so this will clear the
     * cache for all WebViews used.
     *
     * @param includeDiskFiles if false, only the RAM cache is cleared
     */
    public void clearCache(boolean includeDiskFiles) {
        if (mNativeAwContents == 0) return;
        nativeClearCache(mNativeAwContents, includeDiskFiles);
    }

    public void documentHasImages(Message message) {
        if (mNativeAwContents == 0) return;
        nativeDocumentHasImages(mNativeAwContents, message);
    }

    public void saveWebArchive(
            final String basename, boolean autoname, final ValueCallback<String> callback) {
        if (!autoname) {
            saveWebArchiveInternal(basename, callback);
            return;
        }
        // If auto-generating the file name, handle the name generation on a background thread
        // as it will require I/O access for checking whether previous files existed.
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                return generateArchiveAutoNamePath(getOriginalUrl(), basename);
            }

            @Override
            protected void onPostExecute(String result) {
                saveWebArchiveInternal(result, callback);
            }
        }.execute();
    }

    public String getOriginalUrl() {
        NavigationHistory history = mContentViewCore.getNavigationHistory();
        int currentIndex = history.getCurrentEntryIndex();
        if (currentIndex >= 0 && currentIndex < history.getEntryCount()) {
            return history.getEntryAtIndex(currentIndex).getOriginalUrl();
        }
        return null;
    }

    /**
     * @see ContentViewCore#getNavigationHistory()
     */
    public NavigationHistory getNavigationHistory() {
        return mContentViewCore.getNavigationHistory();
    }

    /**
     * @see android.webkit.WebView#getTitle()
     */
    public String getTitle() {
        return mContentViewCore.getTitle();
    }

    /**
     * @see android.webkit.WebView#clearHistory()
     */
    public void clearHistory() {
        mContentViewCore.clearHistory();
    }

    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return mBrowserContext.getHttpAuthDatabase(mContentViewCore.getContext())
                .getHttpAuthUsernamePassword(host, realm);
    }

    public void setHttpAuthUsernamePassword(String host, String realm, String username,
            String password) {
        mBrowserContext.getHttpAuthDatabase(mContentViewCore.getContext())
                .setHttpAuthUsernamePassword(host, realm, username, password);
    }

    /**
     * @see android.webkit.WebView#getCertificate()
     */
    public SslCertificate getCertificate() {
        if (mNativeAwContents == 0) return null;
        return SslUtil.getCertificateFromDerBytes(nativeGetCertificate(mNativeAwContents));
    }

    /**
     * @see android.webkit.WebView#clearSslPreferences()
     */
    public void clearSslPreferences() {
        mContentViewCore.clearSslPreferences();
    }

    /**
     * Method to return all hit test values relevant to public WebView API.
     * Note that this expose more data than needed for WebView.getHitTestResult.
     * Unsafely returning reference to mutable internal object to avoid excessive
     * garbage allocation on repeated calls.
     */
    public HitTestData getLastHitTestResult() {
        if (mNativeAwContents == 0) return null;
        nativeUpdateLastHitTestData(mNativeAwContents);
        return mPossiblyStaleHitTestData;
    }

    /**
     * @see android.webkit.WebView#requestFocusNodeHref()
     */
    public void requestFocusNodeHref(Message msg) {
        if (msg == null || mNativeAwContents == 0) return;

        nativeUpdateLastHitTestData(mNativeAwContents);
        Bundle data = msg.getData();
        data.putString("url", mPossiblyStaleHitTestData.href);
        data.putString("title", mPossiblyStaleHitTestData.anchorText);
        data.putString("src", mPossiblyStaleHitTestData.imgSrc);
        msg.setData(data);
        msg.sendToTarget();
    }

    /**
     * @see android.webkit.WebView#requestImageRef()
     */
    public void requestImageRef(Message msg) {
        if (msg == null || mNativeAwContents == 0) return;

        nativeUpdateLastHitTestData(mNativeAwContents);
        Bundle data = msg.getData();
        data.putString("url", mPossiblyStaleHitTestData.imgSrc);
        msg.setData(data);
        msg.sendToTarget();
    }

    /**
     * @see android.webkit.WebView#getScale()
     *
     * Please note that the scale returned is the page scale multiplied by
     * the screen density factor. See CTS WebViewTest.testSetInitialScale.
     */
    public float getScale() {
        return (float)(mContentViewCore.getScale() * mDIPScale);
    }

    /**
     * @see android.webkit.WebView#flingScroll(int, int)
     */
    public void flingScroll(int velocityX, int velocityY) {
        mContentViewCore.flingScroll(velocityX, velocityY);
    }

    /**
     * @see android.webkit.WebView#pageUp(boolean)
     */
    public boolean pageUp(boolean top) {
        return mContentViewCore.pageUp(top);
    }

    /**
     * @see android.webkit.WebView#pageDown(boolean)
     */
    public boolean pageDown(boolean bottom) {
        return mContentViewCore.pageDown(bottom);
    }

    /**
     * @see android.webkit.WebView#canZoomIn()
     */
    public boolean canZoomIn() {
        return mContentViewCore.canZoomIn();
    }

    /**
     * @see android.webkit.WebView#canZoomOut()
     */
    public boolean canZoomOut() {
        return mContentViewCore.canZoomOut();
    }

    /**
     * @see android.webkit.WebView#zoomIn()
     */
    public boolean zoomIn() {
        return mContentViewCore.zoomIn();
    }

    /**
     * @see android.webkit.WebView#zoomOut()
     */
    public boolean zoomOut() {
        return mContentViewCore.zoomOut();
    }

    /**
     * @see android.webkit.WebView#invokeZoomPicker()
     */
    public void invokeZoomPicker() {
        mContentViewCore.invokeZoomPicker();
    }

    /**
     * @see ContentViewCore.evaluateJavaScript(String, ContentViewCOre.JavaScriptCallback)
     */
    public void evaluateJavaScript(String script, final ValueCallback<String> callback) {
        ContentViewCore.JavaScriptCallback jsCallback = null;
        if (callback != null) {
            jsCallback = new ContentViewCore.JavaScriptCallback() {
                @Override
                public void handleJavaScriptResult(String jsonResult) {
                    callback.onReceiveValue(jsonResult);
                }
            };
        }

        mContentViewCore.evaluateJavaScript(script, jsCallback);
    }

    //--------------------------------------------------------------------------------------------
    //  View and ViewGroup method implementations
    //--------------------------------------------------------------------------------------------

    /**
     * @see android.webkit.View#onTouchEvent()
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (mNativeAwContents == 0) return false;

        mScrollOffsetManager.setProcessingTouchEvent(true);
        boolean rv = mContentViewCore.onTouchEvent(event);
        mScrollOffsetManager.setProcessingTouchEvent(false);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int actionIndex = event.getActionIndex();

            // Note this will trigger IPC back to browser even if nothing is hit.
            nativeRequestNewHitTestDataAt(mNativeAwContents,
                                          (int)Math.round(event.getX(actionIndex) / mDIPScale),
                                          (int)Math.round(event.getY(actionIndex) / mDIPScale));
        }

        if (mOverScrollGlow != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
            mOverScrollGlow.releaseAll();
        }

        return rv;
    }

    /**
     * @see android.view.View#onHoverEvent()
     */
    public boolean onHoverEvent(MotionEvent event) {
        return mContentViewCore.onHoverEvent(event);
    }

    /**
     * @see android.view.View#onGenericMotionEvent()
     */
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mContentViewCore.onGenericMotionEvent(event);
    }

    /**
     * @see android.view.View#onConfigurationChanged()
     */
    public void onConfigurationChanged(Configuration newConfig) {
        mContentViewCore.onConfigurationChanged(newConfig);
    }

    /**
     * @see android.view.View#onAttachedToWindow()
     *
     * Note that this is also called from receivePopupContents.
     */
    public void onAttachedToWindow() {
        mIsAttachedToWindow = true;

        mContentViewCore.onAttachedToWindow();
        nativeOnAttachedToWindow(mNativeAwContents, mContainerView.getWidth(),
                mContainerView.getHeight());
    }

    /**
     * @see android.view.View#onDetachedFromWindow()
     */
    public void onDetachedFromWindow() {
        hideAutofillPopup();
        mIsAttachedToWindow = false;
        if (mNativeAwContents != 0) {
            nativeOnDetachedFromWindow(mNativeAwContents);
        }

        mContentViewCore.onDetachedFromWindow();
    }

    /**
     * @see android.view.View#onWindowFocusChanged()
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mWindowFocused = hasWindowFocus;
        mContentViewCore.onFocusChanged(mContainerViewFocused && mWindowFocused);
    }

    /**
     * @see android.view.View#onFocusChanged()
     */
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        mContainerViewFocused = focused;
        mContentViewCore.onFocusChanged(mContainerViewFocused && mWindowFocused);
    }

    /**
     * @see android.view.View#onSizeChanged()
     */
    public void onSizeChanged(int w, int h, int ow, int oh) {
        if (mNativeAwContents == 0) return;
        mScrollOffsetManager.setContainerViewSize(w, h);
        mContentViewCore.onPhysicalBackingSizeChanged(w, h);
        mContentViewCore.onSizeChanged(w, h, ow, oh);
        nativeOnSizeChanged(mNativeAwContents, w, h, ow, oh);
    }

    /**
     * @see android.view.View#onVisibilityChanged()
     */
    public void onVisibilityChanged(View changedView, int visibility) {
        updateVisibilityState();
    }

    /**
     * @see android.view.View#onWindowVisibilityChanged()
     */
    public void onWindowVisibilityChanged(int visibility) {
        updateVisibilityState();
    }

    private void updateVisibilityState() {
        boolean visible = getContainerViewVisible();
        if (mIsVisible == visible) return;

        setVisibilityInternal(visible);
    }

    private boolean getContainerViewVisible() {
        boolean windowVisible = mContainerView.getWindowVisibility() == View.VISIBLE;
        boolean viewVisible = mContainerView.getVisibility() == View.VISIBLE;

        return windowVisible && viewVisible;
    }

    private void setVisibilityInternal(boolean visible) {
        // Note that this skips mIsVisible check and unconditionally sets
        // visibility. In general, callers should use updateVisibilityState
        // instead.
        mIsVisible = visible;
        nativeSetVisibility(mNativeAwContents, mIsVisible);
    }

    /**
     * Key for opaque state in bundle. Note this is only public for tests.
     */
    public static final String SAVE_RESTORE_STATE_KEY = "WEBVIEW_CHROMIUM_STATE";

    /**
     * Save the state of this AwContents into provided Bundle.
     * @return False if saving state failed.
     */
    public boolean saveState(Bundle outState) {
        if (outState == null) return false;

        byte[] state = nativeGetOpaqueState(mNativeAwContents);
        if (state == null) return false;

        outState.putByteArray(SAVE_RESTORE_STATE_KEY, state);
        return true;
    }

    /**
     * Restore the state of this AwContents into provided Bundle.
     * @param inState Must be a bundle returned by saveState.
     * @return False if restoring state failed.
     */
    public boolean restoreState(Bundle inState) {
        if (inState == null) return false;

        byte[] state = inState.getByteArray(SAVE_RESTORE_STATE_KEY);
        if (state == null) return false;

        boolean result = nativeRestoreFromOpaqueState(mNativeAwContents, state);

        // The onUpdateTitle callback normally happens when a page is loaded,
        // but is optimized out in the restoreState case because the title is
        // already restored. See WebContentsImpl::UpdateTitleForEntry. So we
        // call the callback explicitly here.
        if (result) mContentsClient.onReceivedTitle(mContentViewCore.getTitle());

        return result;
    }

    /**
     * @see ContentViewCore#addPossiblyUnsafeJavascriptInterface(Object, String, Class)
     */
    public void addPossiblyUnsafeJavascriptInterface(Object object, String name,
            Class<? extends Annotation> requiredAnnotation) {
        mContentViewCore.addPossiblyUnsafeJavascriptInterface(object, name, requiredAnnotation);
    }

    /**
     * @see android.webkit.WebView#removeJavascriptInterface(String)
     */
    public void removeJavascriptInterface(String interfaceName) {
        mContentViewCore.removeJavascriptInterface(interfaceName);
    }

    /**
     * If native accessibility (not script injection) is enabled, and if this is
     * running on JellyBean or later, returns an AccessibilityNodeProvider that
     * implements native accessibility for this view. Returns null otherwise.
     * @return The AccessibilityNodeProvider, if available, or null otherwise.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return mContentViewCore.getAccessibilityNodeProvider();
    }

    /**
     * @see android.webkit.WebView#onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo)
     */
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        mContentViewCore.onInitializeAccessibilityNodeInfo(info);
    }

    /**
     * @see android.webkit.WebView#onInitializeAccessibilityEvent(AccessibilityEvent)
     */
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        mContentViewCore.onInitializeAccessibilityEvent(event);
    }

    public boolean supportsAccessibilityAction(int action) {
        return mContentViewCore.supportsAccessibilityAction(action);
    }

    /**
     * @see android.webkit.WebView#performAccessibilityAction(int, Bundle)
     */
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return mContentViewCore.performAccessibilityAction(action, arguments);
    }

    /**
     * @see android.webkit.WebView#clearFormData()
     */
    public void hideAutofillPopup() {
        if (mAwAutofillManagerDelegate != null)
            mAwAutofillManagerDelegate.hideAutofillPopup();
    }

    //--------------------------------------------------------------------------------------------
    //  Methods called from native via JNI
    //--------------------------------------------------------------------------------------------

    @CalledByNative
    private static void onDocumentHasImagesResponse(boolean result, Message message) {
        message.arg1 = result ? 1 : 0;
        message.sendToTarget();
    }

    @CalledByNative
    private void onReceivedTouchIconUrl(String url, boolean precomposed) {
        mContentsClient.onReceivedTouchIconUrl(url, precomposed);
    }

    @CalledByNative
    private void onReceivedIcon(Bitmap bitmap) {
        mContentsClient.onReceivedIcon(bitmap);
        mFavicon = bitmap;
    }

    /** Callback for generateMHTML. */
    @CalledByNative
    private static void generateMHTMLCallback(
            String path, long size, ValueCallback<String> callback) {
        if (callback == null) return;
        callback.onReceiveValue(size < 0 ? null : path);
    }

    @CalledByNative
    private void onReceivedHttpAuthRequest(AwHttpAuthHandler handler, String host, String realm) {
        mContentsClient.onReceivedHttpAuthRequest(handler, host, realm);
    }

    private class AwGeolocationCallback implements GeolocationPermissions.Callback {

        @Override
        public void invoke(final String origin, final boolean allow, final boolean retain) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (retain) {
                        if (allow) {
                            mBrowserContext.getGeolocationPermissions().allow(origin);
                        } else {
                            mBrowserContext.getGeolocationPermissions().deny(origin);
                        }
                    }
                    nativeInvokeGeolocationCallback(mNativeAwContents, allow, origin);
                }
            });
        }
    }

    @CalledByNative
    private void onGeolocationPermissionsShowPrompt(String origin) {
        AwGeolocationPermissions permissions = mBrowserContext.getGeolocationPermissions();
        // Reject if geoloaction is disabled, or the origin has a retained deny
        if (!mSettings.getGeolocationEnabled()) {
            nativeInvokeGeolocationCallback(mNativeAwContents, false, origin);
            return;
        }
        // Allow if the origin has a retained allow
        if (permissions.hasOrigin(origin)) {
            nativeInvokeGeolocationCallback(mNativeAwContents, permissions.isOriginAllowed(origin),
                    origin);
            return;
        }
        mContentsClient.onGeolocationPermissionsShowPrompt(
                origin, new AwGeolocationCallback());
    }

    @CalledByNative
    private void onGeolocationPermissionsHidePrompt() {
        mContentsClient.onGeolocationPermissionsHidePrompt();
    }

    @CalledByNative
    public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting) {
        mContentsClient.onFindResultReceived(activeMatchOrdinal, numberOfMatches, isDoneCounting);
    }

    @CalledByNative
    public void onNewPicture() {
        // Don't call capturePicture() here but instead defer it until the posted task runs within
        // the callback helper, to avoid doubling back into the renderer compositor in the middle
        // of the notification it is sending up to here.
        mContentsClient.getCallbackHelper().postOnNewPicture(mPictureListenerContentProvider);
    }

    // Called as a result of nativeUpdateLastHitTestData.
    @CalledByNative
    private void updateHitTestData(
            int type, String extra, String href, String anchorText, String imgSrc) {
        mPossiblyStaleHitTestData.hitTestResultType = type;
        mPossiblyStaleHitTestData.hitTestResultExtraData = extra;
        mPossiblyStaleHitTestData.href = href;
        mPossiblyStaleHitTestData.anchorText = anchorText;
        mPossiblyStaleHitTestData.imgSrc = imgSrc;
    }

    @CalledByNative
    private boolean requestDrawGL(Canvas canvas) {
        return mInternalAccessAdapter.requestDrawGL(canvas);
    }

    private static final boolean SUPPORTS_ON_ANIMATION =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

    @CalledByNative
    private void postInvalidateOnAnimation() {
        if (SUPPORTS_ON_ANIMATION) {
            mContainerView.postInvalidateOnAnimation();
        } else {
            mContainerView.postInvalidate();
        }
    }

    @CalledByNative
    private boolean performLongClick() {
        return mContainerView.performLongClick();
    }

    @CalledByNative
    private int[] getLocationOnScreen() {
        int[] result = new int[2];
        mContainerView.getLocationOnScreen(result);
        return result;
    }

    @CalledByNative
    private void onWebLayoutPageScaleFactorChanged(float webLayoutPageScaleFactor) {
        // This change notification comes from the renderer thread, not from the cc/ impl thread.
        mLayoutSizer.onPageScaleChanged(webLayoutPageScaleFactor);
    }

    @CalledByNative
    private void scrollContainerViewTo(int x, int y) {
        mScrollOffsetManager.scrollContainerViewTo(x, y);
    }

    @CalledByNative
    private void setAwAutofillManagerDelegate(AwAutofillManagerDelegate delegate) {
        mAwAutofillManagerDelegate = delegate;
        delegate.init(mContentViewCore);
    }

    @CalledByNative
    private void didOverscroll(int deltaX, int deltaY) {
        if (mOverScrollGlow != null) {
            mOverScrollGlow.setOverScrollDeltas(deltaX, deltaY);
        }

        mScrollOffsetManager.overScrollBy(deltaX, deltaY);

        if (mOverScrollGlow != null && mOverScrollGlow.isAnimating()) {
            mContainerView.invalidate();
        }
    }

    // -------------------------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------------------------

    private void saveWebArchiveInternal(String path, final ValueCallback<String> callback) {
        if (path == null || mNativeAwContents == 0) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onReceiveValue(null);
                }
            });
        } else {
            nativeGenerateMHTML(mNativeAwContents, path, callback);
        }
    }

    /**
     * Try to generate a pathname for saving an MHTML archive. This roughly follows WebView's
     * autoname logic.
     */
    private static String generateArchiveAutoNamePath(String originalUrl, String baseName) {
        String name = null;
        if (originalUrl != null && !originalUrl.isEmpty()) {
            try {
                String path = new URL(originalUrl).getPath();
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    name = path.substring(lastSlash + 1);
                } else {
                    name = path;
                }
            } catch (MalformedURLException e) {
                // If it fails parsing the URL, we'll just rely on the default name below.
            }
        }

        if (TextUtils.isEmpty(name)) name = "index";

        String testName = baseName + name + WEB_ARCHIVE_EXTENSION;
        if (!new File(testName).exists()) return testName;

        for (int i = 1; i < 100; i++) {
            testName = baseName + name + "-" + i + WEB_ARCHIVE_EXTENSION;
            if (!new File(testName).exists()) return testName;
        }

        Log.e(TAG, "Unable to auto generate archive name for path: " + baseName);
        return null;
    }

    //--------------------------------------------------------------------------------------------
    //  Native methods
    //--------------------------------------------------------------------------------------------

    private static native int nativeInit(AwBrowserContext browserContext);
    private static native void nativeDestroy(int nativeAwContents);
    private static native void nativeSetAwDrawSWFunctionTable(int functionTablePointer);
    private static native void nativeSetAwDrawGLFunctionTable(int functionTablePointer);
    private static native int nativeGetAwDrawGLFunction();
    private static native int nativeGetNativeInstanceCount();
    private native void nativeSetJavaPeers(int nativeAwContents, AwContents awContents,
            AwWebContentsDelegate webViewWebContentsDelegate,
            AwContentsClientBridge contentsClientBridge,
            AwContentsIoThreadClient ioThreadClient,
            InterceptNavigationDelegate navigationInterceptionDelegate);
    private native int nativeGetWebContents(int nativeAwContents);

    private native void nativeDocumentHasImages(int nativeAwContents, Message message);
    private native void nativeGenerateMHTML(
            int nativeAwContents, String path, ValueCallback<String> callback);

    private native void nativeAddVisitedLinks(int nativeAwContents, String[] visitedLinks);
    private native boolean nativeOnDraw(int nativeAwContents, Canvas canvas,
            boolean isHardwareAccelerated, int scrollX, int ScrollY,
            int clipLeft, int clipTop, int clipRight, int clipBottom);
    private native void nativeSetGlobalVisibleRect(int nativeAwContents, int visibleLeft,
            int visibleTop, int visibleRight, int visibleBottom);
    private native void nativeFindAllAsync(int nativeAwContents, String searchString);
    private native void nativeFindNext(int nativeAwContents, boolean forward);
    private native void nativeClearMatches(int nativeAwContents);
    private native void nativeClearCache(int nativeAwContents, boolean includeDiskFiles);
    private native byte[] nativeGetCertificate(int nativeAwContents);

    // Coordinates in desity independent pixels.
    private native void nativeRequestNewHitTestDataAt(int nativeAwContents, int x, int y);
    private native void nativeUpdateLastHitTestData(int nativeAwContents);

    private native void nativeOnSizeChanged(int nativeAwContents, int w, int h, int ow, int oh);
    private native void nativeScrollTo(int nativeAwContents, int x, int y);
    private native void nativeSetVisibility(int nativeAwContents, boolean visible);
    private native void nativeOnAttachedToWindow(int nativeAwContents, int w, int h);
    private native void nativeOnDetachedFromWindow(int nativeAwContents);
    private native void nativeSetDipScale(int nativeAwContents, float dipScale);
    private native void nativeSetDisplayedPageScaleFactor(int nativeAwContents,
            float pageScaleFactor);

    // Returns null if save state fails.
    private native byte[] nativeGetOpaqueState(int nativeAwContents);

    // Returns false if restore state fails.
    private native boolean nativeRestoreFromOpaqueState(int nativeAwContents, byte[] state);

    private native int nativeReleasePopupAwContents(int nativeAwContents);
    private native void nativeFocusFirstNode(int nativeAwContents);
    private native void nativeSetBackgroundColor(int nativeAwContents, int color);

    private native int nativeGetAwDrawGLViewContext(int nativeAwContents);
    private native Picture nativeCapturePicture(int nativeAwContents, int width, int height);
    private native void nativeEnableOnNewPicture(int nativeAwContents, boolean enabled);

    private native void nativeInvokeGeolocationCallback(
            int nativeAwContents, boolean value, String requestingFrame);
}
