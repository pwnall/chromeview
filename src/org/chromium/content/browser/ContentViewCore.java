// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.text.Editable;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.WeakContext;
import org.chromium.content.R;
import org.chromium.content.browser.ContentViewGestureHandler.MotionEventDelegate;
import org.chromium.content.browser.accessibility.AccessibilityInjector;
import org.chromium.content.browser.input.AdapterInputConnection;
import org.chromium.content.browser.input.HandleView;
import org.chromium.content.browser.input.ImeAdapter;
import org.chromium.content.browser.input.ImeAdapter.AdapterInputConnectionFactory;
import org.chromium.content.browser.input.InsertionHandleController;
import org.chromium.content.browser.input.SelectPopupDialog;
import org.chromium.content.browser.input.SelectionHandleController;
import org.chromium.content.common.TraceEvent;
import org.chromium.ui.ViewAndroid;
import org.chromium.ui.ViewAndroidDelegate;
import org.chromium.ui.WindowAndroid;
import org.chromium.ui.gfx.DeviceDisplayInfo;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Provides a Java-side 'wrapper' around a WebContent (native) instance.
 * Contains all the major functionality necessary to manage the lifecycle of a ContentView without
 * being tied to the view system.
 */
@JNINamespace("content")
public class ContentViewCore implements MotionEventDelegate, NavigationClient {
    /**
     * Indicates that input events are batched together and delivered just before vsync.
     */
    public static final int INPUT_EVENTS_DELIVERED_AT_VSYNC = 1;

    /**
     * Opposite of INPUT_EVENTS_DELIVERED_AT_VSYNC.
     */
    public static final int INPUT_EVENTS_DELIVERED_IMMEDIATELY = 0;

    private static final String TAG = ContentViewCore.class.getName();

    // Used to avoid enabling zooming in / out if resulting zooming will
    // produce little visible difference.
    private static final float ZOOM_CONTROLS_EPSILON = 0.007f;

    // Used to represent gestures for long press and long tap.
    private static final int IS_LONG_PRESS = 1;
    private static final int IS_LONG_TAP = 2;

    // Length of the delay (in ms) before fading in handles after the last page movement.
    private static final int TEXT_HANDLE_FADE_IN_DELAY = 300;

    // If the embedder adds a JavaScript interface object that contains an indirect reference to
    // the ContentViewCore, then storing a strong ref to the interface object on the native
    // side would prevent garbage collection of the ContentViewCore (as that strong ref would
    // create a new GC root).
    // For that reason, we store only a weak reference to the interface object on the
    // native side. However we still need a strong reference on the Java side to
    // prevent garbage collection if the embedder doesn't maintain their own ref to the
    // interface object - the Java side ref won't create a new GC root.
    // This map stores those refernces. We put into the map on addJavaScriptInterface()
    // and remove from it in removeJavaScriptInterface().
    private final Map<String, Object> mJavaScriptInterfaces = new HashMap<String, Object>();

    // Additionally, we keep track of all Java bound JS objects that are in use on the
    // current page to ensure that they are not garbage collected until the page is
    // navigated. This includes interface objects that have been removed
    // via the removeJavaScriptInterface API and transient objects returned from methods
    // on the interface object. Note we use HashSet rather than Set as the native side
    // expects HashSet (no bindings for interfaces).
    private final HashSet<Object> mRetainedJavaScriptObjects = new HashSet<Object>();

    /**
     * Interface that consumers of {@link ContentViewCore} must implement to allow the proper
     * dispatching of view methods through the containing view.
     *
     * <p>
     * All methods with the "super_" prefix should be routed to the parent of the
     * implementing container view.
     */
    @SuppressWarnings("javadoc")
    public interface InternalAccessDelegate {
        /**
         * @see View#drawChild(Canvas, View, long)
         */
        boolean drawChild(Canvas canvas, View child, long drawingTime);

        /**
         * @see View#onKeyUp(keyCode, KeyEvent)
         */
        boolean super_onKeyUp(int keyCode, KeyEvent event);

        /**
         * @see View#dispatchKeyEventPreIme(KeyEvent)
         */
        boolean super_dispatchKeyEventPreIme(KeyEvent event);

        /**
         * @see View#dispatchKeyEvent(KeyEvent)
         */
        boolean super_dispatchKeyEvent(KeyEvent event);

        /**
         * @see View#onGenericMotionEvent(MotionEvent)
         */
        boolean super_onGenericMotionEvent(MotionEvent event);

        /**
         * @see View#onConfigurationChanged(Configuration)
         */
        void super_onConfigurationChanged(Configuration newConfig);

        /**
         * @see View#onScrollChanged(int, int, int, int)
         */
        void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix);

        /**
         * @see View#awakenScrollBars()
         */
        boolean awakenScrollBars();

        /**
         * @see View#awakenScrollBars(int, boolean)
         */
        boolean super_awakenScrollBars(int startDelay, boolean invalidate);
    }

    /**
     * An interface that allows the embedder to be notified when the pinch gesture starts and
     * stops.
     */
    public interface PinchGestureStateListener {
        /**
         * Called when the pinch gesture starts.
         */
        void onPinchGestureStart();
        /**
         * Called when the pinch gesture ends.
         */
        void onPinchGestureEnd();
    }

    /**
     * An interface for controlling visibility and state of embedder-provided zoom controls.
     */
    public interface ZoomControlsDelegate {
        /**
         * Called when it's reasonable to show zoom controls.
         */
        void invokeZoomPicker();
        /**
         * Called when zoom controls need to be hidden (e.g. when the view hides).
         */
        void dismissZoomPicker();
        /**
         * Called when page scale has been changed, so the controls can update their state.
         */
        void updateZoomControls();
    }

    private VSyncManager.Provider mVSyncProvider;
    private VSyncManager.Listener mVSyncListener;
    private int mVSyncSubscriberCount;
    private boolean mVSyncListenerRegistered;

    // To avoid IPC delay we use input events to directly trigger a vsync signal in the renderer.
    // When we do this, we also need to avoid sending the real vsync signal for the current
    // frame to avoid double-ticking. This flag is used to inhibit the next vsync notification.
    private boolean mDidSignalVSyncUsingInputEvent;

    public VSyncManager.Listener getVSyncListener(VSyncManager.Provider vsyncProvider) {
        if (mVSyncProvider != null && mVSyncListenerRegistered) {
            mVSyncProvider.unregisterVSyncListener(mVSyncListener);
            mVSyncListenerRegistered = false;
        }

        mVSyncProvider = vsyncProvider;
        mVSyncListener = new VSyncManager.Listener() {
            @Override
            public void updateVSync(long tickTimeMicros, long intervalMicros) {
                if (mNativeContentViewCore != 0) {
                    nativeUpdateVSyncParameters(mNativeContentViewCore, tickTimeMicros,
                            intervalMicros);
                }
            }

            @Override
            public void onVSync(long frameTimeMicros) {
                animateIfNecessary(frameTimeMicros);

                if (mDidSignalVSyncUsingInputEvent) {
                    TraceEvent.instant("ContentViewCore::onVSync ignored");
                    mDidSignalVSyncUsingInputEvent = false;
                    return;
                }
                if (mNativeContentViewCore != 0) {
                    nativeOnVSync(mNativeContentViewCore, frameTimeMicros);
                }
            }
        };

        if (mVSyncSubscriberCount > 0) {
            // setVSyncNotificationEnabled(true) is called before getVSyncListener.
            vsyncProvider.registerVSyncListener(mVSyncListener);
            mVSyncListenerRegistered = true;
        }

        return mVSyncListener;
    }

    @CalledByNative
    void setVSyncNotificationEnabled(boolean enabled) {
        if (!isVSyncNotificationEnabled() && enabled) {
            mDidSignalVSyncUsingInputEvent = false;
        }
        if (mVSyncProvider != null) {
            if (!mVSyncListenerRegistered && enabled) {
                mVSyncProvider.registerVSyncListener(mVSyncListener);
                mVSyncListenerRegistered = true;
            } else if (mVSyncSubscriberCount == 1 && !enabled) {
                assert mVSyncListenerRegistered;
                mVSyncProvider.unregisterVSyncListener(mVSyncListener);
                mVSyncListenerRegistered = false;
            }
        }
        mVSyncSubscriberCount += enabled ? 1 : -1;
        assert mVSyncSubscriberCount >= 0;
    }

    @CalledByNative
    private void resetVSyncNotification() {
        while (isVSyncNotificationEnabled()) setVSyncNotificationEnabled(false);
        mVSyncSubscriberCount = 0;
        mVSyncListenerRegistered = false;
        mNeedAnimate = false;
    }

    private boolean isVSyncNotificationEnabled() {
        return mVSyncProvider != null && mVSyncListenerRegistered;
    }

    @CalledByNative
    private void setNeedsAnimate() {
        if (!mNeedAnimate) {
            mNeedAnimate = true;
            setVSyncNotificationEnabled(true);
        }
    }

    private final Context mContext;
    private ViewGroup mContainerView;
    private InternalAccessDelegate mContainerViewInternals;
    private WebContentsObserverAndroid mWebContentsObserver;

    private ContentViewClient mContentViewClient;

    private ContentSettings mContentSettings;

    // Native pointer to C++ ContentViewCoreImpl object which will be set by nativeInit().
    private int mNativeContentViewCore = 0;

    private boolean mAttachedToWindow = false;

    private ContentViewGestureHandler mContentViewGestureHandler;
    private PinchGestureStateListener mPinchGestureStateListener;
    private ZoomManager mZoomManager;
    private ZoomControlsDelegate mZoomControlsDelegate;

    private PopupZoomer mPopupZoomer;

    private Runnable mFakeMouseMoveRunnable = null;

    // Only valid when focused on a text / password field.
    private ImeAdapter mImeAdapter;
    private ImeAdapter.AdapterInputConnectionFactory mAdapterInputConnectionFactory;
    private AdapterInputConnection mInputConnection;

    private SelectionHandleController mSelectionHandleController;
    private InsertionHandleController mInsertionHandleController;

    private Runnable mDeferredHandleFadeInRunnable;

    // Size of the viewport in physical pixels as set from onSizeChanged or setInitialViewportSize.
    private int mViewportWidthPix;
    private int mViewportHeightPix;
    private int mPhysicalBackingWidthPix;
    private int mPhysicalBackingHeightPix;
    private int mOverdrawBottomHeightPix;
    private int mViewportSizeOffsetWidthPix;
    private int mViewportSizeOffsetHeightPix;

    // Cached copy of all positions and scales as reported by the renderer.
    private final RenderCoordinates mRenderCoordinates;

    private final RenderCoordinates.NormalizedPoint mStartHandlePoint;
    private final RenderCoordinates.NormalizedPoint mEndHandlePoint;
    private final RenderCoordinates.NormalizedPoint mInsertionHandlePoint;

    // Tracks whether a selection is currently active.  When applied to selected text, indicates
    // whether the last selected text is still highlighted.
    private boolean mHasSelection;
    private String mLastSelectedText;
    private boolean mSelectionEditable;
    private ActionMode mActionMode;
    private boolean mUnselectAllOnActionModeDismiss;

    // Delegate that will handle GET downloads, and be notified of completion of POST downloads.
    private ContentViewDownloadDelegate mDownloadDelegate;

    // The AccessibilityInjector that handles loading Accessibility scripts into the web page.
    private AccessibilityInjector mAccessibilityInjector;

    // Temporary notification to tell onSizeChanged to focus a form element,
    // because the OSK was just brought up.
    private boolean mUnfocusOnNextSizeChanged = false;
    private final Rect mFocusPreOSKViewportRect = new Rect();

    private boolean mNeedUpdateOrientationChanged;

    // Used to keep track of whether we should try to undo the last zoom-to-textfield operation.
    private boolean mScrolledAndZoomedFocusedEditableNode = false;

    // Whether we use hardware-accelerated drawing.
    private boolean mHardwareAccelerated = false;

    // Whether we received a new frame since consumePendingRendererFrame() was last called.
    private boolean mPendingRendererFrame = false;

    // Whether we should animate at the next vsync tick.
    private boolean mNeedAnimate = false;

    private ViewAndroid mViewAndroid;

    /**
     * Constructs a new ContentViewCore. Embedders must call initialize() after constructing
     * a ContentViewCore and before using it.
     *
     * @param context The context used to create this.
     */
    public ContentViewCore(Context context) {
        mContext = context;

        WeakContext.initializeWeakContext(context);
        HeapStatsLogger.init(mContext.getApplicationContext());
        mAdapterInputConnectionFactory = new AdapterInputConnectionFactory();

        mRenderCoordinates = new RenderCoordinates();
        mRenderCoordinates.setDeviceScaleFactor(
                getContext().getResources().getDisplayMetrics().density);
        mStartHandlePoint = mRenderCoordinates.createNormalizedPoint();
        mEndHandlePoint = mRenderCoordinates.createNormalizedPoint();
        mInsertionHandlePoint = mRenderCoordinates.createNormalizedPoint();
    }

    /**
     * @return The context used for creating this ContentViewCore.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * @return The ViewGroup that all view actions of this ContentViewCore should interact with.
     */
    public ViewGroup getContainerView() {
        return mContainerView;
    }

    /**
     * Set initial viewport size parameters, so that the web page can have a reasonable
     * size to start before ContentView becomes visible.
     * This is useful for a background view that loads the web page before it is shown
     * and gets the first onSizeChanged().
     */
    public void setInitialViewportSize(int widthPix, int heightPix,
            int offsetXPix, int offsetYPix) {
        assert mViewportWidthPix == 0 && mViewportHeightPix == 0 &&
                mViewportSizeOffsetWidthPix == 0 && mViewportSizeOffsetHeightPix == 0;
        mViewportWidthPix = widthPix;
        mViewportHeightPix = heightPix;
        mViewportSizeOffsetWidthPix = offsetXPix;
        mViewportSizeOffsetHeightPix = offsetYPix;
        if (mNativeContentViewCore != 0) nativeWasResized(mNativeContentViewCore);
    }

    /**
     * Specifies how much smaller the WebKit layout size should be relative to the size of this
     * view.
     * @param offsetXPix The X amount in pixels to shrink the viewport by.
     * @param offsetYPix The Y amount in pixels to shrink the viewport by.
     */
    public void setViewportSizeOffset(int offsetXPix, int offsetYPix) {
        if (offsetXPix != mViewportSizeOffsetWidthPix ||
                offsetYPix != mViewportSizeOffsetHeightPix) {
            mViewportSizeOffsetWidthPix = offsetXPix;
            mViewportSizeOffsetHeightPix = offsetYPix;
            if (mNativeContentViewCore != 0) nativeWasResized(mNativeContentViewCore);
        }
    }

    /**
     * Returns a delegate that can be used to add and remove views from the ContainerView.
     *
     * NOTE: Use with care, as not all ContentViewCore users setup their ContainerView in the same
     * way. In particular, the Android WebView has limitations on what implementation details can
     * be provided via a child view, as they are visible in the API and could introduce
     * compatibility breaks with existing applications. If in doubt, contact the
     * android_webview/OWNERS
     *
     * @return A ViewAndroidDelegate that can be used to add and remove views.
     */
    @VisibleForTesting
    public ViewAndroidDelegate getViewAndroidDelegate() {
        return new ViewAndroidDelegate() {
            @Override
            public View acquireAnchorView() {
                View anchorView = new View(getContext());
                mContainerView.addView(anchorView);
                return anchorView;
            }

            @Override
            public void setAnchorViewPosition(
                    View view, float x, float y, float width, float height) {
                assert(view.getParent() == mContainerView);
                float scale = (float) DeviceDisplayInfo.create(getContext()).getDIPScale();

                // The anchor view should not go outside the bounds of the ContainerView.
                int scaledX = Math.round(x * scale);
                int scaledWidth = Math.round(width * scale);
                if (scaledWidth + scaledX > mContainerView.getWidth()) {
                    scaledWidth = mContainerView.getWidth() - scaledX;
                }

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        scaledWidth, Math.round(height * scale));
                lp.leftMargin = scaledX;
                lp.topMargin = (int) mRenderCoordinates.getContentOffsetYPix() +
                        Math.round(y * scale);
                view.setLayoutParams(lp);
            }

            @Override
            public void releaseAnchorView(View anchorView) {
                mContainerView.removeView(anchorView);
            }
        };
    }

    @VisibleForTesting
    public ImeAdapter getImeAdapterForTest() {
        return mImeAdapter;
    }

    @VisibleForTesting
    public void setAdapterInputConnectionFactory(AdapterInputConnectionFactory factory) {
        mAdapterInputConnectionFactory = factory;
    }

    @VisibleForTesting
    public AdapterInputConnection getInputConnectionForTest() {
        return mInputConnection;
    }

    private ImeAdapter createImeAdapter(Context context) {
        return new ImeAdapter(context, getSelectionHandleController(),
                getInsertionHandleController(),
                new ImeAdapter.ViewEmbedder() {
                    @Override
                    public void onImeEvent(boolean isFinish) {
                        getContentViewClient().onImeEvent();
                        if (!isFinish) {
                            undoScrollFocusedEditableNodeIntoViewIfNeeded(false);
                        }
                    }

                    @Override
                    public void onSetFieldValue() {
                        scrollFocusedEditableNodeIntoView();
                    }

                    @Override
                    public void onDismissInput() {
                        getContentViewClient().onImeStateChangeRequested(false);
                    }

                    @Override
                    public View getAttachedView() {
                        return mContainerView;
                    }

                    @Override
                    public ResultReceiver getNewShowKeyboardReceiver() {
                        return new ResultReceiver(new Handler()) {
                            @Override
                            public void onReceiveResult(int resultCode, Bundle resultData) {
                                getContentViewClient().onImeStateChangeRequested(
                                        resultCode == InputMethodManager.RESULT_SHOWN ||
                                        resultCode == InputMethodManager.RESULT_UNCHANGED_SHOWN);
                                if (resultCode == InputMethodManager.RESULT_SHOWN) {
                                    // If OSK is newly shown, delay the form focus until
                                    // the onSizeChanged (in order to adjust relative to the
                                    // new size).
                                    getContainerView().getWindowVisibleDisplayFrame(
                                            mFocusPreOSKViewportRect);
                                } else if (resultCode ==
                                        InputMethodManager.RESULT_UNCHANGED_SHOWN) {
                                    // If the OSK was already there, focus the form immediately.
                                    scrollFocusedEditableNodeIntoView();
                                } else {
                                    undoScrollFocusedEditableNodeIntoViewIfNeeded(false);
                                }
                            }
                        };
                    }
                }
        );
    }

    /**
     * Returns true if the given Activity has hardware acceleration enabled
     * in its manifest, or in its foreground window.
     *
     * TODO(husky): Remove when initialize() is refactored (see TODO there)
     * TODO(dtrainor) This is still used by other classes.  Make sure to pull some version of this
     * out before removing it.
     */
    public static boolean hasHardwareAcceleration(Activity activity) {
        // Has HW acceleration been enabled manually in the current window?
        Window window = activity.getWindow();
        if (window != null) {
            if ((window.getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0) {
                return true;
            }
        }

        // Has HW acceleration been enabled in the manifest?
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(
                    activity.getComponentName(), 0);
            if ((info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Chrome", "getActivityInfo(self) should not fail");
        }

        return false;
    }

    /**
     * Returns true if the given Context is a HW-accelerated Activity.
     *
     * TODO(husky): Remove when initialize() is refactored (see TODO there)
     */
    private static boolean hasHardwareAcceleration(Context context) {
        if (context instanceof Activity) {
            return hasHardwareAcceleration((Activity) context);
        }
        return false;
    }

    /**
     *
     * @param containerView The view that will act as a container for all views created by this.
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     * @param nativeWebContents A pointer to the native web contents.
     * @param windowAndroid An instance of the WindowAndroid.
     */
    // Perform important post-construction set up of the ContentViewCore.
    // We do not require the containing view in the constructor to allow embedders to create a
    // ContentViewCore without having fully created its containing view. The containing view
    // is a vital component of the ContentViewCore, so embedders must exercise caution in what
    // they do with the ContentViewCore before calling initialize().
    // We supply the nativeWebContents pointer here rather than in the constructor to allow us
    // to set the private browsing mode at a later point for the WebView implementation.
    // Note that the caller remains the owner of the nativeWebContents and is responsible for
    // deleting it after destroying the ContentViewCore.
    public void initialize(ViewGroup containerView, InternalAccessDelegate internalDispatcher,
            int nativeWebContents, WindowAndroid windowAndroid,
            int inputEventDeliveryMode) {
        // Check whether to use hardware acceleration. This is a bit hacky, and
        // only works if the Context is actually an Activity (as it is in the
        // Chrome application).
        //
        // What we're doing here is checking whether the app has *requested*
        // hardware acceleration by setting the appropriate flags. This does not
        // necessarily mean we're going to *get* hardware acceleration -- that's
        // up to the Android framework.
        //
        // TODO(husky): Once the native code has been updated so that the
        // HW acceleration flag can be set dynamically (Grace is doing this),
        // move this check into onAttachedToWindow(), where we can test for
        // HW support directly.
        mHardwareAccelerated = hasHardwareAcceleration(mContext);

        mContainerView = containerView;

        int windowNativePointer = windowAndroid != null ? windowAndroid.getNativePointer() : 0;

        int viewAndroidNativePointer = 0;
        if (windowNativePointer != 0) {
            mViewAndroid = new ViewAndroid(windowAndroid, getViewAndroidDelegate());
            viewAndroidNativePointer = mViewAndroid.getNativePointer();
        }

        mNativeContentViewCore = nativeInit(mHardwareAccelerated,
                nativeWebContents, viewAndroidNativePointer, windowNativePointer);
        mContentSettings = new ContentSettings(this, mNativeContentViewCore);
        initializeContainerView(internalDispatcher, inputEventDeliveryMode);

        mAccessibilityInjector = AccessibilityInjector.newInstance(this);
        mAccessibilityInjector.addOrRemoveAccessibilityApisIfNecessary();

        String contentDescription = "Web View";
        if (R.string.accessibility_content_view == 0) {
            Log.w(TAG, "Setting contentDescription to 'Web View' as no value was specified.");
        } else {
            contentDescription = mContext.getResources().getString(
                    R.string.accessibility_content_view);
        }
        mContainerView.setContentDescription(contentDescription);
        mWebContentsObserver = new WebContentsObserverAndroid(this) {
            @Override
            public void didStartLoading(String url) {
                hidePopupDialog();
                resetGestureDetectors();
            }
        };
    }

    @CalledByNative
    void onNativeContentViewCoreDestroyed(int nativeContentViewCore) {
        assert nativeContentViewCore == mNativeContentViewCore;
        mNativeContentViewCore = 0;
    }

    /**
     * Initializes the View that will contain all Views created by the ContentViewCore.
     *
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     */
    private void initializeContainerView(InternalAccessDelegate internalDispatcher,
            int inputEventDeliveryMode) {
        TraceEvent.begin();
        mContainerViewInternals = internalDispatcher;

        mContainerView.setWillNotDraw(false);
        mContainerView.setFocusable(true);
        mContainerView.setFocusableInTouchMode(true);
        mContainerView.setClickable(true);

        if (mContainerView.getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            mContainerView.setHorizontalScrollBarEnabled(false);
            mContainerView.setVerticalScrollBarEnabled(false);
        }

        mZoomManager = new ZoomManager(mContext, this);
        mContentViewGestureHandler = new ContentViewGestureHandler(mContext, this, mZoomManager,
                inputEventDeliveryMode);
        mZoomControlsDelegate = new ZoomControlsDelegate() {
            @Override
            public void invokeZoomPicker() {}
            @Override
            public void dismissZoomPicker() {}
            @Override
            public void updateZoomControls() {}
        };

        mRenderCoordinates.reset();

        initPopupZoomer(mContext);
        mImeAdapter = createImeAdapter(mContext);
        TraceEvent.end();
    }

    private void initPopupZoomer(Context context){
        mPopupZoomer = new PopupZoomer(context);
        mPopupZoomer.setOnVisibilityChangedListener(new PopupZoomer.OnVisibilityChangedListener() {
            @Override
            public void onPopupZoomerShown(final PopupZoomer zoomer) {
                mContainerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mContainerView.indexOfChild(zoomer) == -1) {
                            mContainerView.addView(zoomer);
                        } else {
                            assert false : "PopupZoomer should never be shown without being hidden";
                        }
                    }
                });
            }

            @Override
            public void onPopupZoomerHidden(final PopupZoomer zoomer) {
                mContainerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mContainerView.indexOfChild(zoomer) != -1) {
                            mContainerView.removeView(zoomer);
                            mContainerView.invalidate();
                        } else {
                            assert false : "PopupZoomer should never be hidden without being shown";
                        }
                    }
                });
            }
        });
        // TODO(yongsheng): LONG_TAP is not enabled in PopupZoomer. So need to dispatch a LONG_TAP
        // gesture if a user completes a tap on PopupZoomer UI after a LONG_PRESS gesture.
        PopupZoomer.OnTapListener listener = new PopupZoomer.OnTapListener() {
            @Override
            public boolean onSingleTap(View v, MotionEvent e) {
                mContainerView.requestFocus();
                if (mNativeContentViewCore != 0) {
                    nativeSingleTap(mNativeContentViewCore, e.getEventTime(),
                            e.getX(), e.getY(), true);
                }
                return true;
            }

            @Override
            public boolean onLongPress(View v, MotionEvent e) {
                if (mNativeContentViewCore != 0) {
                    nativeLongPress(mNativeContentViewCore, e.getEventTime(),
                            e.getX(), e.getY(), true);
                }
                return true;
            }
        };
        mPopupZoomer.setOnTapListener(listener);
    }

    /**
     * Destroy the internal state of the ContentView. This method may only be
     * called after the ContentView has been removed from the view system. No
     * other methods may be called on this ContentView after this method has
     * been called.
     */
    public void destroy() {
        if (mNativeContentViewCore != 0) {
            nativeOnJavaContentViewCoreDestroyed(mNativeContentViewCore);
        }
        resetVSyncNotification();
        mVSyncProvider = null;
        if (mViewAndroid != null) mViewAndroid.destroy();
        mNativeContentViewCore = 0;
        mContentSettings = null;
        mJavaScriptInterfaces.clear();
        mRetainedJavaScriptObjects.clear();
    }

    /**
     * Returns true initially, false after destroy() has been called.
     * It is illegal to call any other public method after destroy().
     */
    public boolean isAlive() {
        return mNativeContentViewCore != 0;
    }

    /**
     * This is only useful for passing over JNI to native code that requires ContentViewCore*.
     * @return native ContentViewCore pointer.
     */
    @CalledByNative
    public int getNativeContentViewCore() {
        return mNativeContentViewCore;
    }

    /**
     * For internal use. Throws IllegalStateException if mNativeContentView is 0.
     * Use this to ensure we get a useful Java stack trace, rather than a native
     * crash dump, from use-after-destroy bugs in Java code.
     */
    void checkIsAlive() throws IllegalStateException {
        if (!isAlive()) {
            throw new IllegalStateException("ContentView used after destroy() was called");
        }
    }

    public void setContentViewClient(ContentViewClient client) {
        if (client == null) {
            throw new IllegalArgumentException("The client can't be null.");
        }
        mContentViewClient = client;
    }

    ContentViewClient getContentViewClient() {
        if (mContentViewClient == null) {
            // We use the Null Object pattern to avoid having to perform a null check in this class.
            // We create it lazily because most of the time a client will be set almost immediately
            // after ContentView is created.
            mContentViewClient = new ContentViewClient();
            // We don't set the native ContentViewClient pointer here on purpose. The native
            // implementation doesn't mind a null delegate and using one is better than passing a
            // Null Object, since we cut down on the number of JNI calls.
        }
        return mContentViewClient;
    }

    public int getBackgroundColor() {
        if (mNativeContentViewCore != 0) {
            return nativeGetBackgroundColor(mNativeContentViewCore);
        }
        return Color.WHITE;
    }

    public void setBackgroundColor(int color) {
        if (mNativeContentViewCore != 0 && getBackgroundColor() != color) {
            nativeSetBackgroundColor(mNativeContentViewCore, color);
        }
    }

    @CalledByNative
    private void onBackgroundColorChanged(int color) {
        getContentViewClient().onBackgroundColorChanged(color);
    }

    /**
     * Load url without fixing up the url string. Consumers of ContentView are responsible for
     * ensuring the URL passed in is properly formatted (i.e. the scheme has been added if left
     * off during user input).
     *
     * @param params Parameters for this load.
     */
    public void loadUrl(LoadUrlParams params) {
        if (mNativeContentViewCore == 0) return;

        nativeLoadUrl(mNativeContentViewCore,
                params.mUrl,
                params.mLoadUrlType,
                params.mTransitionType,
                params.mUaOverrideOption,
                params.getExtraHeadersString(),
                params.mPostData,
                params.mBaseUrlForDataUrl,
                params.mVirtualUrlForDataUrl,
                params.mCanLoadLocalResources);
    }

    /**
     * Stops loading the current web contents.
     */
    public void stopLoading() {
        if (mNativeContentViewCore != 0) nativeStopLoading(mNativeContentViewCore);
    }

    /**
     * Get the URL of the current page.
     *
     * @return The URL of the current page.
     */
    public String getUrl() {
        if (mNativeContentViewCore != 0) return nativeGetURL(mNativeContentViewCore);
        return null;
    }

    /**
     * Get the title of the current page.
     *
     * @return The title of the current page.
     */
    public String getTitle() {
        if (mNativeContentViewCore != 0) return nativeGetTitle(mNativeContentViewCore);
        return null;
    }

    /**
     * Shows an interstitial page driven by the passed in delegate.
     *
     * @param url The URL being blocked by the interstitial.
     * @param delegate The delegate handling the interstitial.
     */
    @VisibleForTesting
    public void showInterstitialPage(
            String url, InterstitialPageDelegateAndroid delegate) {
        if (mNativeContentViewCore == 0) return;
        nativeShowInterstitialPage(mNativeContentViewCore, url, delegate.getNative());
    }

    /**
     * @return Whether the page is currently showing an interstitial, such as a bad HTTPS page.
     */
    public boolean isShowingInterstitialPage() {
        return mNativeContentViewCore == 0 ?
                false : nativeIsShowingInterstitialPage(mNativeContentViewCore);
    }

    /**
     * Mark any new frames that have arrived since this function was last called as non-pending.
     *
     * @return Whether there was a pending frame from the renderer.
     */
    public boolean consumePendingRendererFrame() {
        boolean hadPendingFrame = mPendingRendererFrame;
        mPendingRendererFrame = false;
        return hadPendingFrame;
    }

    /**
     * @return Viewport width in physical pixels as set from onSizeChanged or
     * setInitialViewportSize.
     */
    @CalledByNative
    public int getViewportWidthPix() { return mViewportWidthPix; }

    /**
     * @return Viewport height in physical pixels as set from onSizeChanged or
     * setInitialViewportSize.
     */
    @CalledByNative
    public int getViewportHeightPix() { return mViewportHeightPix; }

    /**
     * @return Width of underlying physical surface.
     */
    @CalledByNative
    public int getPhysicalBackingWidthPix() { return mPhysicalBackingWidthPix; }

    /**
     * @return Height of underlying physical surface.
     */
    @CalledByNative
    public int getPhysicalBackingHeightPix() { return mPhysicalBackingHeightPix; }

    /**
     * @return Amount the output surface extends past the bottom of the window viewport.
     */
    @CalledByNative
    public int getOverdrawBottomHeightPix() { return mOverdrawBottomHeightPix; }

    /**
     * @return The amount to shrink the viewport relative to {@link #getViewportWidthPix()}.
     */
    @CalledByNative
    public int getViewportSizeOffsetWidthPix() { return mViewportSizeOffsetWidthPix; }

    /**
     * @return The amount to shrink the viewport relative to {@link #getViewportHeightPix()}.
     */
    @CalledByNative
    public int getViewportSizeOffsetHeightPix() { return mViewportSizeOffsetHeightPix; }

    /**
     * @see android.webkit.WebView#getContentHeight()
     */
    public float getContentHeightCss() {
        return mRenderCoordinates.getContentHeightCss();
    }

    /**
     * @see android.webkit.WebView#getContentWidth()
     */
    public float getContentWidthCss() {
        return mRenderCoordinates.getContentWidthCss();
    }

    public Bitmap getBitmap() {
        return getBitmap(getViewportWidthPix(), getViewportHeightPix());
    }

    public Bitmap getBitmap(int width, int height) {
        if (width == 0 || height == 0
                || getViewportWidthPix() == 0 || getViewportHeightPix() == 0) {
            return null;
        }

        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        if (mNativeContentViewCore != 0 &&
                nativePopulateBitmapFromCompositor(mNativeContentViewCore, b)) {
            // If we successfully grabbed a bitmap, check if we have to draw the Android overlay
            // components as well.
            if (mContainerView.getChildCount() > 0) {
                Canvas c = new Canvas(b);
                c.scale(width / (float) getViewportWidthPix(),
                        height / (float) getViewportHeightPix());
                mContainerView.draw(c);
            }
            return b;
        }

        return null;
    }

    /**
     * Generates a bitmap of the content that is performance optimized based on capture time.
     *
     * <p>
     * To have a consistent capture time across devices, we will scale down the captured bitmap
     * where necessary to reduce the time to generate the bitmap.
     *
     * @param width The width of the content to be captured.
     * @param height The height of the content to be captured.
     * @return A pair of the generated bitmap, and the scale that needs to be applied to return the
     *         bitmap to it's original size (i.e. if the bitmap is scaled down 50%, this
     *         will be 2).
     */
    public Pair<Bitmap, Float> getScaledPerformanceOptimizedBitmap(int width, int height) {
        float scale = 1f;
        // On tablets, always scale down to MDPI for performance reasons.
        if (DeviceUtils.isTablet(getContext())) {
            scale = getContext().getResources().getDisplayMetrics().density;
        }
        return Pair.create(
                getBitmap((int) (width / scale), (int) (height / scale)),
                scale);
    }

    /**
     * @return Whether the current WebContents has a previous navigation entry.
     */
    public boolean canGoBack() {
        return mNativeContentViewCore != 0 && nativeCanGoBack(mNativeContentViewCore);
    }

    /**
     * @return Whether the current WebContents has a navigation entry after the current one.
     */
    public boolean canGoForward() {
        return mNativeContentViewCore != 0 && nativeCanGoForward(mNativeContentViewCore);
    }

    /**
     * @param offset The offset into the navigation history.
     * @return Whether we can move in history by given offset
     */
    public boolean canGoToOffset(int offset) {
        return mNativeContentViewCore != 0 && nativeCanGoToOffset(mNativeContentViewCore, offset);
    }

    /**
     * Navigates to the specified offset from the "current entry". Does nothing if the offset is out
     * of bounds.
     * @param offset The offset into the navigation history.
     */
    public void goToOffset(int offset) {
        if (mNativeContentViewCore != 0) nativeGoToOffset(mNativeContentViewCore, offset);
    }

    @Override
    public void goToNavigationIndex(int index) {
        if (mNativeContentViewCore != 0) nativeGoToNavigationIndex(mNativeContentViewCore, index);
    }

    /**
     * Goes to the navigation entry before the current one.
     */
    public void goBack() {
        if (mNativeContentViewCore != 0) nativeGoBack(mNativeContentViewCore);
    }

    /**
     * Goes to the navigation entry following the current one.
     */
    public void goForward() {
        if (mNativeContentViewCore != 0) nativeGoForward(mNativeContentViewCore);
    }

    /**
     * Reload the current page.
     */
    public void reload() {
        mAccessibilityInjector.addOrRemoveAccessibilityApisIfNecessary();
        if (mNativeContentViewCore != 0) nativeReload(mNativeContentViewCore);
    }

    /**
     * Cancel the pending reload.
     */
    public void cancelPendingReload() {
        if (mNativeContentViewCore != 0) nativeCancelPendingReload(mNativeContentViewCore);
    }

    /**
     * Continue the pending reload.
     */
    public void continuePendingReload() {
        if (mNativeContentViewCore != 0) nativeContinuePendingReload(mNativeContentViewCore);
    }

    /**
     * Clears the ContentViewCore's page history in both the backwards and
     * forwards directions.
     */
    public void clearHistory() {
        if (mNativeContentViewCore != 0) nativeClearHistory(mNativeContentViewCore);
    }

    String getSelectedText() {
        return mHasSelection ? mLastSelectedText : "";
    }

    // End FrameLayout overrides.

    /**
     * @see {@link android.webkit.WebView#flingScroll(int, int)}
     */
    public void flingScroll(int vx, int vy) {
        // Notes:
        //   (1) Use large negative values for the x/y parameters so we don't accidentally scroll a
        //       nested frame.
        //   (2) vx and vy are inverted to match WebView behavior.
        mContentViewGestureHandler.fling(
                System.currentTimeMillis(), -Integer.MAX_VALUE, -Integer.MIN_VALUE, -vx, -vy);
    }

    /**
     * @see View#onTouchEvent(MotionEvent)
     */
    public boolean onTouchEvent(MotionEvent event) {
        undoScrollFocusedEditableNodeIntoViewIfNeeded(false);
        return mContentViewGestureHandler.onTouchEvent(event);
    }

    /**
     * @return ContentViewGestureHandler for all MotionEvent and gesture related calls.
     */
    ContentViewGestureHandler getContentViewGestureHandler() {
        return mContentViewGestureHandler;
    }

    @Override
    public boolean sendTouchEvent(long timeMs, int action, TouchPoint[] pts) {
        if (mNativeContentViewCore != 0) {
            return nativeSendTouchEvent(mNativeContentViewCore, timeMs, action, pts);
        }
        return false;
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void hasTouchEventHandlers(boolean hasTouchHandlers) {
        mContentViewGestureHandler.hasTouchEventHandlers(hasTouchHandlers);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void confirmTouchEvent(int ackResult) {
        mContentViewGestureHandler.confirmTouchEvent(ackResult);
    }

    @Override
    public boolean sendGesture(int type, long timeMs, int x, int y, boolean lastInputEventForVSync,
                               Bundle b) {
        if (mNativeContentViewCore == 0) return false;
        updateTextHandlesForGesture(type);
        updatePinchGestureStateListener(type);
        if (lastInputEventForVSync && isVSyncNotificationEnabled()) {
            assert type == ContentViewGestureHandler.GESTURE_SCROLL_BY ||
                    type == ContentViewGestureHandler.GESTURE_PINCH_BY;
            mDidSignalVSyncUsingInputEvent = true;
        }
        switch (type) {
            case ContentViewGestureHandler.GESTURE_SHOW_PRESSED_STATE:
                nativeShowPressState(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_SHOW_PRESS_CANCEL:
                nativeShowPressCancel(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_DOUBLE_TAP:
                nativeDoubleTap(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_SINGLE_TAP_UP:
                nativeSingleTap(mNativeContentViewCore, timeMs, x, y, false);
                return true;
            case ContentViewGestureHandler.GESTURE_SINGLE_TAP_CONFIRMED:
                handleTapOrPress(timeMs, x, y, 0,
                        b.getBoolean(ContentViewGestureHandler.SHOW_PRESS, false));
                return true;
            case ContentViewGestureHandler.GESTURE_SINGLE_TAP_UNCONFIRMED:
                nativeSingleTapUnconfirmed(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_LONG_PRESS:
                handleTapOrPress(timeMs, x, y, IS_LONG_PRESS, false);
                return true;
            case ContentViewGestureHandler.GESTURE_LONG_TAP:
                handleTapOrPress(timeMs, x, y, IS_LONG_TAP, false);
                return true;
            case ContentViewGestureHandler.GESTURE_SCROLL_START:
                nativeScrollBegin(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_SCROLL_BY: {
                int dx = b.getInt(ContentViewGestureHandler.DISTANCE_X);
                int dy = b.getInt(ContentViewGestureHandler.DISTANCE_Y);
                nativeScrollBy(mNativeContentViewCore, timeMs, x, y, dx, dy,
                        lastInputEventForVSync);
                return true;
            }
            case ContentViewGestureHandler.GESTURE_SCROLL_END:
                nativeScrollEnd(mNativeContentViewCore, timeMs);
                return true;
            case ContentViewGestureHandler.GESTURE_FLING_START:
                nativeFlingStart(mNativeContentViewCore, timeMs, x, y,
                        b.getInt(ContentViewGestureHandler.VELOCITY_X, 0),
                        b.getInt(ContentViewGestureHandler.VELOCITY_Y, 0));
                return true;
            case ContentViewGestureHandler.GESTURE_FLING_CANCEL:
                nativeFlingCancel(mNativeContentViewCore, timeMs);
                return true;
            case ContentViewGestureHandler.GESTURE_PINCH_BEGIN:
                nativePinchBegin(mNativeContentViewCore, timeMs, x, y);
                return true;
            case ContentViewGestureHandler.GESTURE_PINCH_BY:
                nativePinchBy(mNativeContentViewCore, timeMs, x, y,
                        b.getFloat(ContentViewGestureHandler.DELTA, 0),
                        lastInputEventForVSync);
                return true;
            case ContentViewGestureHandler.GESTURE_PINCH_END:
                nativePinchEnd(mNativeContentViewCore, timeMs);
                return true;
            default:
                return false;
        }
    }

    public void setPinchGestureStateListener(PinchGestureStateListener pinchGestureStateListener) {
        mPinchGestureStateListener = pinchGestureStateListener;
    }

    void updatePinchGestureStateListener(int gestureType) {
        if (mPinchGestureStateListener == null) return;

        switch (gestureType) {
            case ContentViewGestureHandler.GESTURE_PINCH_BEGIN:
                mPinchGestureStateListener.onPinchGestureStart();
                break;
            case ContentViewGestureHandler.GESTURE_PINCH_END:
                mPinchGestureStateListener.onPinchGestureEnd();
                break;
            default:
                break;
        }
    }

    public interface JavaScriptCallback {
        void handleJavaScriptResult(String jsonResult);
    }

    /**
     * Injects the passed Javascript code in the current page and evaluates it.
     * If a result is required, pass in a callback.
     * Used in automation tests.
     *
     * @param script The Javascript to execute.
     * @param callback The callback to be fired off when a result is ready. The script's
     *                 result will be json encoded and passed as the parameter, and the call
     *                 will be made on the main thread.
     *                 If no result is required, pass null.
     * @throws IllegalStateException If the ContentView has been destroyed.
     */
    public void evaluateJavaScript(
            String script, JavaScriptCallback callback) throws IllegalStateException {
        checkIsAlive();
        nativeEvaluateJavaScript(mNativeContentViewCore, script, callback);
    }

    /**
     * This method should be called when the containing activity is paused.
     */
    public void onActivityPause() {
        TraceEvent.begin();
        hidePopupDialog();
        nativeOnHide(mNativeContentViewCore);
        setAccessibilityState(false);
        TraceEvent.end();
    }

    /**
     * This method should be called when the containing activity is resumed.
     */
    public void onActivityResume() {
        nativeOnShow(mNativeContentViewCore);
        setAccessibilityState(true);
    }

    /**
     * To be called when the ContentView is shown.
     */
    public void onShow() {
        nativeOnShow(mNativeContentViewCore);
        setAccessibilityState(true);
    }

    /**
     * To be called when the ContentView is hidden.
     */
    public void onHide() {
        hidePopupDialog();
        setAccessibilityState(false);
        nativeOnHide(mNativeContentViewCore);
    }

    /**
     * Return the ContentSettings object used to retrieve the settings for this
     * ContentViewCore. For modifications, ChromeNativePreferences is to be used.
     * @return A ContentSettings object that can be used to retrieve this
     *         ContentViewCore's settings.
     */
    public ContentSettings getContentSettings() {
        return mContentSettings;
    }

    @Override
    public boolean didUIStealScroll(float x, float y) {
        return getContentViewClient().shouldOverrideScroll(
                x, y, computeHorizontalScrollOffset(), computeVerticalScrollOffset());
    }

    @Override
    public boolean hasFixedPageScale() {
        return mRenderCoordinates.hasFixedPageScale();
    }

    private void hidePopupDialog() {
        SelectPopupDialog.hide(this);
        hideHandles();
        hideSelectActionBar();
    }

    void hideSelectActionBar() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    private void resetGestureDetectors() {
        mContentViewGestureHandler.resetGestureHandlers();
    }

    /**
     * @see View#onAttachedToWindow()
     */
    @SuppressWarnings("javadoc")
    public void onAttachedToWindow() {
        mAttachedToWindow = true;
        if (mNativeContentViewCore != 0) {
            int pid = nativeGetCurrentRenderProcessId(mNativeContentViewCore);
            if (pid > 0) {
                ChildProcessLauncher.bindAsHighPriority(pid);
            }
        }
        setAccessibilityState(true);
    }

    /**
     * @see View#onDetachedFromWindow()
     */
    @SuppressWarnings("javadoc")
    public void onDetachedFromWindow() {
        mAttachedToWindow = false;
        if (mNativeContentViewCore != 0) {
            int pid = nativeGetCurrentRenderProcessId(mNativeContentViewCore);
            if (pid > 0) {
                ChildProcessLauncher.unbindAsHighPriority(pid);
            }
        }
        setAccessibilityState(false);
        hidePopupDialog();
        mZoomControlsDelegate.dismissZoomPicker();
    }

    /**
     * @see View#onVisibilityChanged(android.view.View, int)
     */
    public void onVisibilityChanged(View changedView, int visibility) {
      if (visibility != View.VISIBLE) {
          mZoomControlsDelegate.dismissZoomPicker();
      }
    }

    /**
     * @see View#onCreateInputConnection(EditorInfo)
     */
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!mImeAdapter.hasTextInputType()) {
            // Although onCheckIsTextEditor will return false in this case, the EditorInfo
            // is still used by the InputMethodService. Need to make sure the IME doesn't
            // enter fullscreen mode.
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        mInputConnection =
                mAdapterInputConnectionFactory.get(mContainerView, mImeAdapter, outAttrs);
        return mInputConnection;
    }

    public Editable getEditableForTest() {
        return mInputConnection.getEditable();
    }

    /**
     * @see View#onCheckIsTextEditor()
     */
    public boolean onCheckIsTextEditor() {
        return mImeAdapter.hasTextInputType();
    }

    /**
     * @see View#onConfigurationChanged(Configuration)
     */
    @SuppressWarnings("javadoc")
    public void onConfigurationChanged(Configuration newConfig) {
        TraceEvent.begin();

        if (newConfig.keyboard != Configuration.KEYBOARD_NOKEYS) {
            mImeAdapter.attach(nativeGetNativeImeAdapter(mNativeContentViewCore),
                    ImeAdapter.getTextInputTypeNone(),
                    AdapterInputConnection.INVALID_SELECTION,
                    AdapterInputConnection.INVALID_SELECTION);
            InputMethodManager manager = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.restartInput(mContainerView);
        }
        mContainerViewInternals.super_onConfigurationChanged(newConfig);
        mNeedUpdateOrientationChanged = true;
        TraceEvent.end();
    }

    /**
     * @see View#onSizeChanged(int, int, int, int)
     */
    @SuppressWarnings("javadoc")
    public void onSizeChanged(int wPix, int hPix, int owPix, int ohPix) {
        if (getViewportWidthPix() == wPix && getViewportHeightPix() == hPix) return;

        mViewportWidthPix = wPix;
        mViewportHeightPix = hPix;
        if (mNativeContentViewCore != 0) {
            nativeWasResized(mNativeContentViewCore);
        }

        updateAfterSizeChanged();
    }

    /**
     * Called when the underlying surface the compositor draws to changes size.
     * This may be larger than the viewport size.
     */
    public void onPhysicalBackingSizeChanged(int wPix, int hPix) {
        if (mPhysicalBackingWidthPix == wPix && mPhysicalBackingHeightPix == hPix) return;

        mPhysicalBackingWidthPix = wPix;
        mPhysicalBackingHeightPix = hPix;

        if (mNativeContentViewCore != 0) {
            nativeWasResized(mNativeContentViewCore);
        }
    }

    /**
     * Called when the amount the surface is overdrawing off the bottom has changed.
     * @param overdrawHeightPix The overdraw height.
     */
    public void onOverdrawBottomHeightChanged(int overdrawHeightPix) {
        if (mOverdrawBottomHeightPix == overdrawHeightPix) return;

        mOverdrawBottomHeightPix = overdrawHeightPix;

        if (mNativeContentViewCore != 0) {
            nativeWasResized(mNativeContentViewCore);
        }
    }

    private void updateAfterSizeChanged() {
        mPopupZoomer.hide(false);

        // Execute a delayed form focus operation because the OSK was brought
        // up earlier.
        if (!mFocusPreOSKViewportRect.isEmpty()) {
            Rect rect = new Rect();
            getContainerView().getWindowVisibleDisplayFrame(rect);
            if (!rect.equals(mFocusPreOSKViewportRect)) {
                scrollFocusedEditableNodeIntoView();
                mFocusPreOSKViewportRect.setEmpty();
            }
        } else if (mUnfocusOnNextSizeChanged) {
            undoScrollFocusedEditableNodeIntoViewIfNeeded(true);
            mUnfocusOnNextSizeChanged = false;
        }

        if (mNeedUpdateOrientationChanged) {
            sendOrientationChangeEvent();
            mNeedUpdateOrientationChanged = false;
        }
    }

    private void scrollFocusedEditableNodeIntoView() {
        if (mNativeContentViewCore != 0) {
            Runnable scrollTask = new Runnable() {
                @Override
                public void run() {
                    if (mNativeContentViewCore != 0) {
                        nativeScrollFocusedEditableNodeIntoView(mNativeContentViewCore);
                    }
                }
            };

            scrollTask.run();

            // The native side keeps track of whether the zoom and scroll actually occurred. It is
            // more efficient to do it this way and sometimes fire an unnecessary message rather
            // than synchronize with the renderer and always have an additional message.
            mScrolledAndZoomedFocusedEditableNode = true;
        }
    }

    private void undoScrollFocusedEditableNodeIntoViewIfNeeded(boolean backButtonPressed) {
        // The only call to this function that matters is the first call after the
        // scrollFocusedEditableNodeIntoView function call.
        // If the first call to this function is a result of a back button press we want to undo the
        // preceding scroll. If the call is a result of some other action we don't want to perform
        // an undo.
        // All subsequent calls are ignored since only the scroll function sets
        // mScrolledAndZoomedFocusedEditableNode to true.
        if (mScrolledAndZoomedFocusedEditableNode && backButtonPressed &&
                mNativeContentViewCore != 0) {
            Runnable scrollTask = new Runnable() {
                @Override
                public void run() {
                    if (mNativeContentViewCore != 0) {
                        nativeUndoScrollFocusedEditableNodeIntoView(mNativeContentViewCore);
                    }
                }
            };

            scrollTask.run();
        }
        mScrolledAndZoomedFocusedEditableNode = false;
    }

    /**
     * @see View#onFocusedChanged(boolean, int, Rect)
     * TODO(benm): Remove once downstream usages have been updated to use single
     * parameter version
     */
    @Deprecated
    @SuppressWarnings("javadoc")
    public void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        onFocusChanged(gainFocus);
    }


    public void onFocusChanged(boolean gainFocus) {
        if (!gainFocus) getContentViewClient().onImeStateChangeRequested(false);
        if (mNativeContentViewCore != 0) nativeSetFocus(mNativeContentViewCore, gainFocus);
    }

    /**
     * @see View#onKeyUp(int, KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mPopupZoomer.isShowing() && keyCode == KeyEvent.KEYCODE_BACK) {
            mPopupZoomer.hide(true);
            return true;
        }
        return mContainerViewInternals.super_onKeyUp(keyCode, event);
    }

    /**
     * @see View#dispatchKeyEventPreIme(KeyEvent)
     */
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        try {
            TraceEvent.begin();
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mImeAdapter.isActive()) {
                mUnfocusOnNextSizeChanged = true;
            } else {
                undoScrollFocusedEditableNodeIntoViewIfNeeded(false);
            }
            return mContainerViewInternals.super_dispatchKeyEventPreIme(event);
        } finally {
            TraceEvent.end();
        }
    }

    /**
     * @see View#dispatchKeyEvent(KeyEvent)
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (getContentViewClient().shouldOverrideKeyEvent(event)) {
            return mContainerViewInternals.super_dispatchKeyEvent(event);
        }

        if (mImeAdapter.dispatchKeyEvent(event)) return true;

        return mContainerViewInternals.super_dispatchKeyEvent(event);
    }

    /**
     * @see View#onHoverEvent(MotionEvent)
     * Mouse move events are sent on hover enter, hover move and hover exit.
     * They are sent on hover exit because sometimes it acts as both a hover
     * move and hover exit.
     */
    public boolean onHoverEvent(MotionEvent event) {
        TraceEvent.begin("onHoverEvent");
        mContainerView.removeCallbacks(mFakeMouseMoveRunnable);
        if (mNativeContentViewCore != 0) {
            nativeSendMouseMoveEvent(mNativeContentViewCore, event.getEventTime(),
                    event.getX(), event.getY());
        }
        TraceEvent.end("onHoverEvent");
        return true;
    }

    /**
     * @see View#onGenericMotionEvent(MotionEvent)
     */
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL:
                    nativeSendMouseWheelEvent(mNativeContentViewCore, event.getEventTime(),
                            event.getX(), event.getY(),
                            event.getAxisValue(MotionEvent.AXIS_VSCROLL));

                    mContainerView.removeCallbacks(mFakeMouseMoveRunnable);
                    // Send a delayed onMouseMove event so that we end
                    // up hovering over the right position after the scroll.
                    final MotionEvent eventFakeMouseMove = MotionEvent.obtain(event);
                    mFakeMouseMoveRunnable = new Runnable() {
                          @Override
                          public void run() {
                              onHoverEvent(eventFakeMouseMove);
                          }
                    };
                    mContainerView.postDelayed(mFakeMouseMoveRunnable, 250);
                    return true;
            }
        }
        return mContainerViewInternals.super_onGenericMotionEvent(event);
    }

    /**
     * @see View#scrollBy(int, int)
     * Currently the ContentView scrolling happens in the native side. In
     * the Java view system, it is always pinned at (0, 0). scrollBy() and scrollTo()
     * are overridden, so that View's mScrollX and mScrollY will be unchanged at
     * (0, 0). This is critical for drawing ContentView correctly.
     */
    public void scrollBy(int xPix, int yPix) {
        if (mNativeContentViewCore != 0) {
            nativeScrollBy(mNativeContentViewCore,
                    System.currentTimeMillis(), 0, 0, xPix, yPix, false);
        }
    }

    /**
     * @see View#scrollTo(int, int)
     */
    public void scrollTo(int xPix, int yPix) {
        if (mNativeContentViewCore == 0) return;
        final float xCurrentPix = mRenderCoordinates.getScrollXPix();
        final float yCurrentPix = mRenderCoordinates.getScrollYPix();
        final float dxPix = xPix - xCurrentPix;
        final float dyPix = yPix - yCurrentPix;
        if (dxPix != 0 || dyPix != 0) {
            long time = System.currentTimeMillis();
            nativeScrollBegin(mNativeContentViewCore, time, xCurrentPix, yCurrentPix);
            nativeScrollBy(mNativeContentViewCore,
                    time, xCurrentPix, yCurrentPix, dxPix, dyPix, false);
            nativeScrollEnd(mNativeContentViewCore, time);
        }
    }

    // NOTE: this can go away once ContentView.getScrollX() reports correct values.
    //       see: b/6029133
    public int getNativeScrollXForTest() {
        return mRenderCoordinates.getScrollXPixInt();
    }

    // NOTE: this can go away once ContentView.getScrollY() reports correct values.
    //       see: b/6029133
    public int getNativeScrollYForTest() {
        return mRenderCoordinates.getScrollYPixInt();
    }

    /**
     * @see View#computeHorizontalScrollExtent()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportWidthPixInt();
    }

    /**
     * @see View#computeHorizontalScrollOffset()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollOffset() {
        return mRenderCoordinates.getScrollXPixInt();
    }

    /**
     * @see View#computeHorizontalScrollRange()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollRange() {
        return mRenderCoordinates.getContentWidthPixInt();
    }

    /**
     * @see View#computeVerticalScrollExtent()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportHeightPixInt();
    }

    /**
     * @see View#computeVerticalScrollOffset()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollOffset() {
        return mRenderCoordinates.getScrollYPixInt();
    }

    /**
     * @see View#computeVerticalScrollRange()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollRange() {
        return mRenderCoordinates.getContentHeightPixInt();
    }

    // End FrameLayout overrides.

    /**
     * @see View#awakenScrollBars(int, boolean)
     */
    @SuppressWarnings("javadoc")
    public boolean awakenScrollBars(int startDelay, boolean invalidate) {
        // For the default implementation of ContentView which draws the scrollBars on the native
        // side, calling this function may get us into a bad state where we keep drawing the
        // scrollBars, so disable it by always returning false.
        if (mContainerView.getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            return false;
        } else {
            return mContainerViewInternals.super_awakenScrollBars(startDelay, invalidate);
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onTabCrash() {
        getContentViewClient().onTabCrash();
    }

    private void handleTapOrPress(
            long timeMs, float xPix, float yPix, int isLongPressOrTap, boolean showPress) {
        if (!mContainerView.isFocused()) mContainerView.requestFocus();

        if (!mPopupZoomer.isShowing()) mPopupZoomer.setLastTouch(xPix, yPix);

        if (isLongPressOrTap == IS_LONG_PRESS) {
            getInsertionHandleController().allowAutomaticShowing();
            getSelectionHandleController().allowAutomaticShowing();
            if (mNativeContentViewCore != 0) {
                nativeLongPress(mNativeContentViewCore, timeMs, xPix, yPix, false);
            }
        } else if (isLongPressOrTap == IS_LONG_TAP) {
            getInsertionHandleController().allowAutomaticShowing();
            getSelectionHandleController().allowAutomaticShowing();
            if (mNativeContentViewCore != 0) {
                nativeLongTap(mNativeContentViewCore, timeMs, xPix, yPix, false);
            }
        } else {
            if (!showPress && mNativeContentViewCore != 0) {
                nativeShowPressState(mNativeContentViewCore, timeMs, xPix, yPix);
            }
            if (mSelectionEditable) getInsertionHandleController().allowAutomaticShowing();
            if (mNativeContentViewCore != 0) {
                nativeSingleTap(mNativeContentViewCore, timeMs, xPix, yPix, false);
            }
        }
    }

    public void setZoomControlsDelegate(ZoomControlsDelegate zoomControlsDelegate) {
        mZoomControlsDelegate = zoomControlsDelegate;
    }

    public void updateMultiTouchZoomSupport(boolean supportsMultiTouchZoom) {
        mZoomManager.updateMultiTouchSupport(supportsMultiTouchZoom);
    }

    public void selectPopupMenuItems(int[] indices) {
        if (mNativeContentViewCore != 0) {
            nativeSelectPopupMenuItems(mNativeContentViewCore, indices);
        }
    }

    /**
     * Get the screen orientation from the OS and push it to WebKit.
     *
     * TODO(husky): Add a hook for mock orientations.
     *
     * TODO(husky): Currently each new tab starts with an orientation of 0 until you actually
     * rotate the device. This is wrong if you actually started in landscape mode. To fix this, we
     * need to push the correct orientation, but only after WebKit's Frame object has been fully
     * initialized. Need to find a good time to do that. onPageFinished() would probably work but
     * it isn't implemented yet.
     */
    private void sendOrientationChangeEvent() {
        if (mNativeContentViewCore == 0) return;

        WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                nativeSendOrientationChangeEvent(mNativeContentViewCore, 90);
                break;
            case Surface.ROTATION_180:
                nativeSendOrientationChangeEvent(mNativeContentViewCore, 180);
                break;
            case Surface.ROTATION_270:
                nativeSendOrientationChangeEvent(mNativeContentViewCore, -90);
                break;
            case Surface.ROTATION_0:
                nativeSendOrientationChangeEvent(mNativeContentViewCore, 0);
                break;
            default:
                Log.w(TAG, "Unknown rotation!");
                break;
        }
    }

    /**
     * Register the delegate to be used when content can not be handled by
     * the rendering engine, and should be downloaded instead. This will replace
     * the current delegate, if any.
     * @param delegate An implementation of ContentViewDownloadDelegate.
     */
    public void setDownloadDelegate(ContentViewDownloadDelegate delegate) {
        mDownloadDelegate = delegate;
    }

    // Called by DownloadController.
    ContentViewDownloadDelegate getDownloadDelegate() {
        return mDownloadDelegate;
    }

    private SelectionHandleController getSelectionHandleController() {
        if (mSelectionHandleController == null) {
            mSelectionHandleController = new SelectionHandleController(getContainerView()) {
                @Override
                public void selectBetweenCoordinates(int x1, int y1, int x2, int y2) {
                    if (mNativeContentViewCore != 0 && !(x1 == x2 && y1 == y2)) {
                        nativeSelectBetweenCoordinates(mNativeContentViewCore,
                                x1, y1 - mRenderCoordinates.getContentOffsetYPix(),
                                x2, y2 - mRenderCoordinates.getContentOffsetYPix());
                    }
                }

                @Override
                public void showHandles(int startDir, int endDir) {
                    super.showHandles(startDir, endDir);
                    showSelectActionBar();
                }

            };

            mSelectionHandleController.hideAndDisallowAutomaticShowing();
        }

        return mSelectionHandleController;
    }

    private InsertionHandleController getInsertionHandleController() {
        if (mInsertionHandleController == null) {
            mInsertionHandleController = new InsertionHandleController(getContainerView()) {
                private static final int AVERAGE_LINE_HEIGHT = 14;

                @Override
                public void setCursorPosition(int x, int y) {
                    if (mNativeContentViewCore != 0) {
                        nativeMoveCaret(mNativeContentViewCore,
                                x, y - mRenderCoordinates.getContentOffsetYPix());
                    }
                }

                @Override
                public void paste() {
                    mImeAdapter.paste();
                    hideHandles();
                }

                @Override
                public int getLineHeight() {
                    return (int) Math.ceil(
                            mRenderCoordinates.fromLocalCssToPix(AVERAGE_LINE_HEIGHT));
                }

                @Override
                public void showHandle() {
                    super.showHandle();
                }
            };

            mInsertionHandleController.hideAndDisallowAutomaticShowing();
        }

        return mInsertionHandleController;
    }

    public InsertionHandleController getInsertionHandleControllerForTest() {
        return mInsertionHandleController;
    }

    private void updateHandleScreenPositions() {
        if (isSelectionHandleShowing()) {
            mSelectionHandleController.setStartHandlePosition(
                    mStartHandlePoint.getXPix(), mStartHandlePoint.getYPix());
            mSelectionHandleController.setEndHandlePosition(
                    mEndHandlePoint.getXPix(), mEndHandlePoint.getYPix());
        }

        if (isInsertionHandleShowing()) {
            mInsertionHandleController.setHandlePosition(
                    mInsertionHandlePoint.getXPix(), mInsertionHandlePoint.getYPix());
        }
    }

    private void hideHandles() {
        if (mSelectionHandleController != null) {
            mSelectionHandleController.hideAndDisallowAutomaticShowing();
        }
        if (mInsertionHandleController != null) {
            mInsertionHandleController.hideAndDisallowAutomaticShowing();
        }
    }

    private void showSelectActionBar() {
        if (mActionMode != null) {
            mActionMode.invalidate();
            return;
        }

        // Start a new action mode with a SelectActionModeCallback.
        SelectActionModeCallback.ActionHandler actionHandler =
                new SelectActionModeCallback.ActionHandler() {
            @Override
            public boolean selectAll() {
                return mImeAdapter.selectAll();
            }

            @Override
            public boolean cut() {
                return mImeAdapter.cut();
            }

            @Override
            public boolean copy() {
                return mImeAdapter.copy();
            }

            @Override
            public boolean paste() {
                return mImeAdapter.paste();
            }

            @Override
            public boolean isSelectionEditable() {
                return mSelectionEditable;
            }

            @Override
            public String getSelectedText() {
                return ContentViewCore.this.getSelectedText();
            }

            @Override
            public void onDestroyActionMode() {
                mActionMode = null;
                if (mUnselectAllOnActionModeDismiss) mImeAdapter.unselect();
                getContentViewClient().onContextualActionBarHidden();
            }
        };
        mActionMode = null;
        // On ICS, startActionMode throws an NPE when getParent() is null.
        if (mContainerView.getParent() != null) {
            mActionMode = mContainerView.startActionMode(
                    getContentViewClient().getSelectActionModeCallback(getContext(), actionHandler,
                            nativeIsIncognito(mNativeContentViewCore)));
        }
        mUnselectAllOnActionModeDismiss = true;
        if (mActionMode == null) {
            // There is no ActionMode, so remove the selection.
            mImeAdapter.unselect();
        } else {
            getContentViewClient().onContextualActionBarShown();
        }
    }

    public boolean getUseDesktopUserAgent() {
        if (mNativeContentViewCore != 0) {
            return nativeGetUseDesktopUserAgent(mNativeContentViewCore);
        }
        return false;
    }

    /**
     * Set whether or not we're using a desktop user agent for the currently loaded page.
     * @param override If true, use a desktop user agent.  Use a mobile one otherwise.
     * @param reloadOnChange Reload the page if the UA has changed.
     */
    public void setUseDesktopUserAgent(boolean override, boolean reloadOnChange) {
        if (mNativeContentViewCore != 0) {
            nativeSetUseDesktopUserAgent(mNativeContentViewCore, override, reloadOnChange);
        }
    }

    public void clearSslPreferences() {
        nativeClearSslPreferences(mNativeContentViewCore);
    }

    /**
     * @return Whether the native ContentView has crashed.
     */
    public boolean isCrashed() {
        if (mNativeContentViewCore == 0) return false;
        return nativeCrashed(mNativeContentViewCore);
    }

    private boolean isSelectionHandleShowing() {
        return mSelectionHandleController != null && mSelectionHandleController.isShowing();
    }

    private boolean isInsertionHandleShowing() {
        return mInsertionHandleController != null && mInsertionHandleController.isShowing();
    }

    private void updateTextHandlesForGesture(int type) {
        switch(type) {
            case ContentViewGestureHandler.GESTURE_DOUBLE_TAP:
            case ContentViewGestureHandler.GESTURE_SCROLL_START:
            case ContentViewGestureHandler.GESTURE_FLING_START:
            case ContentViewGestureHandler.GESTURE_PINCH_BEGIN:
                temporarilyHideTextHandles();
                break;

            default:
                break;
        }
    }

    // Makes the insertion/selection handles invisible. They will fade back in shortly after the
    // last call to scheduleTextHandleFadeIn (or temporarilyHideTextHandles).
    private void temporarilyHideTextHandles() {
        if (isSelectionHandleShowing()) {
            mSelectionHandleController.setHandleVisibility(HandleView.INVISIBLE);
        }
        if (isInsertionHandleShowing()) {
            mInsertionHandleController.setHandleVisibility(HandleView.INVISIBLE);
        }
        scheduleTextHandleFadeIn();
    }

    // Cancels any pending fade in and schedules a new one.
    private void scheduleTextHandleFadeIn() {
        if (!isInsertionHandleShowing() && !isSelectionHandleShowing()) return;

        if (mDeferredHandleFadeInRunnable == null) {
            mDeferredHandleFadeInRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mContentViewGestureHandler.isNativeScrolling() ||
                            mContentViewGestureHandler.isNativePinching()) {
                        // Delay fade in until no longer scrolling or pinching.
                        scheduleTextHandleFadeIn();
                    } else {
                        if (isSelectionHandleShowing()) {
                            mSelectionHandleController.beginHandleFadeIn();
                        }
                        if (isInsertionHandleShowing()) {
                            mInsertionHandleController.beginHandleFadeIn();
                        }
                    }
                }
            };
        }

        mContainerView.removeCallbacks(mDeferredHandleFadeInRunnable);
        mContainerView.postDelayed(mDeferredHandleFadeInRunnable, TEXT_HANDLE_FADE_IN_DELAY);
    }

    /**
     * Shows the IME if the focused widget could accept text input.
     */
    public void showImeIfNeeded() {
        if (mNativeContentViewCore != 0) nativeShowImeIfNeeded(mNativeContentViewCore);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void updateFrameInfo(
            float scrollOffsetX, float scrollOffsetY,
            float pageScaleFactor, float minPageScaleFactor, float maxPageScaleFactor,
            float contentWidth, float contentHeight,
            float viewportWidth, float viewportHeight,
            float controlsOffsetYCss, float contentOffsetYCss,
            float overdrawBottomHeightCss) {
        TraceEvent.instant("ContentViewCore:updateFrameInfo");
        // Adjust contentWidth/Height to be always at least as big as the actual viewport
        // (as set by onSizeChanged or setInitialViewportSize).
        contentWidth = Math.max(contentWidth,
                mRenderCoordinates.fromPixToLocalCss(mViewportWidthPix));
        contentHeight = Math.max(contentHeight,
                mRenderCoordinates.fromPixToLocalCss(mViewportHeightPix));

        final float contentOffsetYPix = mRenderCoordinates.fromDipToPix(contentOffsetYCss);

        final boolean contentSizeChanged =
                contentWidth != mRenderCoordinates.getContentWidthCss()
                || contentHeight != mRenderCoordinates.getContentHeightCss();
        final boolean scaleLimitsChanged =
                minPageScaleFactor != mRenderCoordinates.getMinPageScaleFactor()
                || maxPageScaleFactor != mRenderCoordinates.getMaxPageScaleFactor();
        final boolean pageScaleChanged =
                pageScaleFactor != mRenderCoordinates.getPageScaleFactor();
        final boolean scrollChanged =
                pageScaleChanged
                || scrollOffsetX != mRenderCoordinates.getScrollX()
                || scrollOffsetY != mRenderCoordinates.getScrollY();
        final boolean contentOffsetChanged =
                contentOffsetYPix != mRenderCoordinates.getContentOffsetYPix();

        final boolean needHidePopupZoomer = contentSizeChanged || scrollChanged;
        final boolean needUpdateZoomControls = scaleLimitsChanged || scrollChanged;
        final boolean needTemporarilyHideHandles = scrollChanged;

        if (needHidePopupZoomer) mPopupZoomer.hide(true);

        if (scrollChanged) {
            mContainerViewInternals.onScrollChanged(
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetX),
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetY),
                    (int) mRenderCoordinates.getScrollXPix(),
                    (int) mRenderCoordinates.getScrollYPix());
        }

        if (pageScaleChanged) {
            // This function should be called back from native as soon
            // as the scroll is applied to the backbuffer.  We should only
            // update mNativeScrollX/Y here for consistency.
            getContentViewClient().onScaleChanged(
                    mRenderCoordinates.getPageScaleFactor(), pageScaleFactor);
        }

        mRenderCoordinates.updateFrameInfo(
                scrollOffsetX, scrollOffsetY,
                contentWidth, contentHeight,
                viewportWidth, viewportHeight,
                pageScaleFactor, minPageScaleFactor, maxPageScaleFactor,
                contentOffsetYPix);

        if (needTemporarilyHideHandles) temporarilyHideTextHandles();
        if (needUpdateZoomControls) mZoomControlsDelegate.updateZoomControls();
        if (contentOffsetChanged) updateHandleScreenPositions();

        // Update offsets for fullscreen.
        final float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
        final float controlsOffsetPix = controlsOffsetYCss * deviceScale;
        final float overdrawBottomHeightPix = overdrawBottomHeightCss * deviceScale;
        getContentViewClient().onOffsetsForFullscreenChanged(
                controlsOffsetPix, contentOffsetYPix, overdrawBottomHeightPix);

        mPendingRendererFrame = true;
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void updateImeAdapter(int nativeImeAdapterAndroid, int textInputType,
            String text, int selectionStart, int selectionEnd,
            int compositionStart, int compositionEnd, boolean showImeIfNeeded) {
        TraceEvent.begin();
        mSelectionEditable = (textInputType != ImeAdapter.getTextInputTypeNone());

        if (mActionMode != null) mActionMode.invalidate();

        mImeAdapter.attachAndShowIfNeeded(nativeImeAdapterAndroid, textInputType,
                selectionStart, selectionEnd, showImeIfNeeded);

        if (mInputConnection != null) {
            mInputConnection.setEditableText(text, selectionStart, selectionEnd,
                    compositionStart, compositionEnd);
        }
        TraceEvent.end();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void processImeBatchStateAck(boolean isBegin) {
        if (mInputConnection == null) return;
        mInputConnection.setIgnoreTextInputStateUpdates(isBegin);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void setTitle(String title) {
        getContentViewClient().onUpdateTitle(title);
    }

    /**
     * Called (from native) when the <select> popup needs to be shown.
     * @param items           Items to show.
     * @param enabled         POPUP_ITEM_TYPEs for items.
     * @param multiple        Whether the popup menu should support multi-select.
     * @param selectedIndices Indices of selected items.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void showSelectPopup(String[] items, int[] enabled, boolean multiple,
            int[] selectedIndices) {
        SelectPopupDialog.show(this, items, enabled, multiple, selectedIndices);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void showDisambiguationPopup(Rect targetRect, Bitmap zoomedBitmap) {
        mPopupZoomer.setBitmap(zoomedBitmap);
        mPopupZoomer.show(targetRect);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private SmoothScroller createSmoothScroller(boolean scrollDown, int mouseEventX,
            int mouseEventY) {
        return new SmoothScroller(this, scrollDown, mouseEventX, mouseEventY);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onSelectionChanged(String text) {
        mLastSelectedText = text;
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onSelectionBoundsChanged(Rect anchorRectDip, int anchorDir, Rect focusRectDip,
            int focusDir, boolean isAnchorFirst) {
        // All coordinates are in DIP.
        int x1 = anchorRectDip.left;
        int y1 = anchorRectDip.bottom;
        int x2 = focusRectDip.left;
        int y2 = focusRectDip.bottom;

        if (x1 != x2 || y1 != y2 ||
                (mSelectionHandleController != null && mSelectionHandleController.isDragging())) {
            if (mInsertionHandleController != null) {
                mInsertionHandleController.hide();
            }
            if (isAnchorFirst) {
                mStartHandlePoint.setLocalDip(x1, y1);
                mEndHandlePoint.setLocalDip(x2, y2);
            } else {
                mStartHandlePoint.setLocalDip(x2, y2);
                mEndHandlePoint.setLocalDip(x1, y1);
            }

            getSelectionHandleController().onSelectionChanged(anchorDir, focusDir);
            updateHandleScreenPositions();
            mHasSelection = true;
        } else {
            mUnselectAllOnActionModeDismiss = false;
            hideSelectActionBar();
            if (x1 != 0 && y1 != 0 && mSelectionEditable) {
                // Selection is a caret, and a text field is focused.
                if (mSelectionHandleController != null) {
                    mSelectionHandleController.hide();
                }
                mInsertionHandlePoint.setLocalDip(x1, y1);

                getInsertionHandleController().onCursorPositionChanged();
                updateHandleScreenPositions();
                InputMethodManager manager = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (manager.isWatchingCursor(mContainerView)) {
                    final int xPix = (int) mInsertionHandlePoint.getXPix();
                    final int yPix = (int) mInsertionHandlePoint.getYPix();
                    manager.updateCursor(mContainerView, xPix, yPix, xPix, yPix);
                }
            } else {
                // Deselection
                if (mSelectionHandleController != null) {
                    mSelectionHandleController.hideAndDisallowAutomaticShowing();
                }
                if (mInsertionHandleController != null) {
                    mInsertionHandleController.hideAndDisallowAutomaticShowing();
                }
            }
            mHasSelection = false;
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private static void onEvaluateJavaScriptResult(
            String jsonResult, JavaScriptCallback callback) {
        callback.handleJavaScriptResult(jsonResult);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void showPastePopup(int xDip, int yDip) {
        mInsertionHandlePoint.setLocalDip(xDip, yDip);
        getInsertionHandleController().showHandle();
        updateHandleScreenPositions();
        getInsertionHandleController().showHandleWithPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onRenderProcessSwap(int oldPid, int newPid) {
        if (mAttachedToWindow && oldPid != newPid) {
            if (oldPid > 0) {
                ChildProcessLauncher.unbindAsHighPriority(oldPid);
            }
            if (newPid > 0) {
                ChildProcessLauncher.bindAsHighPriority(newPid);
            }
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onWebContentsConnected() {
        if (mImeAdapter != null &&
                !mImeAdapter.isNativeImeAdapterAttached() && mNativeContentViewCore != 0) {
            mImeAdapter.attach(nativeGetNativeImeAdapter(mNativeContentViewCore));
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onWebContentsSwapped() {
        if (mImeAdapter != null && mNativeContentViewCore != 0) {
            mImeAdapter.attach(nativeGetNativeImeAdapter(mNativeContentViewCore));
        }
    }

    /**
     * @return Whether a reload happens when this ContentView is activated.
     */
    public boolean needsReload() {
        return mNativeContentViewCore != 0 && nativeNeedsReload(mNativeContentViewCore);
    }

    /**
     * @see View#hasFocus()
     */
    @CalledByNative
    public boolean hasFocus() {
        return mContainerView.hasFocus();
    }

    /**
     * Checks whether the ContentViewCore can be zoomed in.
     *
     * @return True if the ContentViewCore can be zoomed in.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomIn() {
        final float zoomInExtent = mRenderCoordinates.getMaxPageScaleFactor()
                - mRenderCoordinates.getPageScaleFactor();
        return zoomInExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * Checks whether the ContentViewCore can be zoomed out.
     *
     * @return True if the ContentViewCore can be zoomed out.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomOut() {
        final float zoomOutExtent = mRenderCoordinates.getPageScaleFactor()
                - mRenderCoordinates.getMinPageScaleFactor();
        return zoomOutExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * Zooms in the ContentViewCore by 25% (or less if that would result in
     * zooming in more than possible).
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomIn() {
        if (!canZoomIn()) {
            return false;
        }
        return zoomByDelta(1.25f);
    }

    /**
     * Zooms out the ContentViewCore by 20% (or less if that would result in
     * zooming out more than possible).
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomOut() {
        if (!canZoomOut()) {
            return false;
        }
        return zoomByDelta(0.8f);
    }

    /**
     * Resets the zoom factor of the ContentViewCore.
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomReset() {
        // The page scale factor is initialized to mNativeMinimumScale when
        // the page finishes loading. Thus sets it back to mNativeMinimumScale.
        if (!canZoomOut()) return false;
        return zoomByDelta(
                mRenderCoordinates.getMinPageScaleFactor()
                        / mRenderCoordinates.getPageScaleFactor());
    }

    private boolean zoomByDelta(float delta) {
        if (mNativeContentViewCore == 0) {
            return false;
        }

        long timeMs = System.currentTimeMillis();
        int xPix = getViewportWidthPix() / 2;
        int yPix = getViewportHeightPix() / 2;

        getContentViewGestureHandler().pinchBegin(timeMs, xPix, yPix);
        getContentViewGestureHandler().pinchBy(timeMs, xPix, yPix, delta);
        getContentViewGestureHandler().pinchEnd(timeMs);

        return true;
    }

    /**
     * Invokes the graphical zoom picker widget for this ContentView.
     */
    @Override
    public void invokeZoomPicker() {
        mZoomControlsDelegate.invokeZoomPicker();
    }

    /**
     * This will mimic {@link #addPossiblyUnsafeJavascriptInterface(Object, String, Class)}
     * and automatically pass in {@link JavascriptInterface} as the required annotation.
     *
     * @param object The Java object to inject into the ContentViewCore's JavaScript context.  Null
     *               values are ignored.
     * @param name   The name used to expose the instance in JavaScript.
     */
    public void addJavascriptInterface(Object object, String name) {
        addPossiblyUnsafeJavascriptInterface(object, name, JavascriptInterface.class);
    }

    /**
     * This method injects the supplied Java object into the ContentViewCore.
     * The object is injected into the JavaScript context of the main frame,
     * using the supplied name. This allows the Java object to be accessed from
     * JavaScript. Note that that injected objects will not appear in
     * JavaScript until the page is next (re)loaded. For example:
     * <pre> view.addJavascriptInterface(new Object(), "injectedObject");
     * view.loadData("<!DOCTYPE html><title></title>", "text/html", null);
     * view.loadUrl("javascript:alert(injectedObject.toString())");</pre>
     * <p><strong>IMPORTANT:</strong>
     * <ul>
     * <li> addJavascriptInterface() can be used to allow JavaScript to control
     * the host application. This is a powerful feature, but also presents a
     * security risk. Use of this method in a ContentViewCore containing
     * untrusted content could allow an attacker to manipulate the host
     * application in unintended ways, executing Java code with the permissions
     * of the host application. Use extreme care when using this method in a
     * ContentViewCore which could contain untrusted content. Particular care
     * should be taken to avoid unintentional access to inherited methods, such
     * as {@link Object#getClass()}. To prevent access to inherited methods,
     * pass an annotation for {@code requiredAnnotation}.  This will ensure
     * that only methods with {@code requiredAnnotation} are exposed to the
     * Javascript layer.  {@code requiredAnnotation} will be passed to all
     * subsequently injected Java objects if any methods return an object.  This
     * means the same restrictions (or lack thereof) will apply.  Alternatively,
     * {@link #addJavascriptInterface(Object, String)} can be called, which
     * automatically uses the {@link JavascriptInterface} annotation.
     * <li> JavaScript interacts with Java objects on a private, background
     * thread of the ContentViewCore. Care is therefore required to maintain
     * thread safety.</li>
     * </ul></p>
     *
     * @param object             The Java object to inject into the
     *                           ContentViewCore's JavaScript context. Null
     *                           values are ignored.
     * @param name               The name used to expose the instance in
     *                           JavaScript.
     * @param requiredAnnotation Restrict exposed methods to ones with this
     *                           annotation.  If {@code null} all methods are
     *                           exposed.
     *
     */
    public void addPossiblyUnsafeJavascriptInterface(Object object, String name,
            Class<? extends Annotation> requiredAnnotation) {
        if (mNativeContentViewCore != 0 && object != null) {
            mJavaScriptInterfaces.put(name, object);
            nativeAddJavascriptInterface(mNativeContentViewCore, object, name, requiredAnnotation,
                    mRetainedJavaScriptObjects);
        }
    }

    /**
     * Removes a previously added JavaScript interface with the given name.
     *
     * @param name The name of the interface to remove.
     */
    public void removeJavascriptInterface(String name) {
        mJavaScriptInterfaces.remove(name);
        if (mNativeContentViewCore != 0) {
            nativeRemoveJavascriptInterface(mNativeContentViewCore, name);
        }
    }

    /**
     * Return the current scale of the ContentView.
     * @return The current page scale factor.
     */
    public float getScale() {
        return mRenderCoordinates.getPageScaleFactor();
    }

    /**
     * If the view is ready to draw contents to the screen. In hardware mode,
     * the initialization of the surface texture may not occur until after the
     * view has been added to the layout. This method will return {@code true}
     * once the texture is actually ready.
     */
    public boolean isReady() {
        return nativeIsRenderWidgetHostViewReady(mNativeContentViewCore);
    }

    @CalledByNative
    private void startContentIntent(String contentUrl) {
        getContentViewClient().onStartContentIntent(getContext(), contentUrl);
    }

    /**
     * Determines whether or not this ContentViewCore can handle this accessibility action.
     * @param action The action to perform.
     * @return Whether or not this action is supported.
     */
    public boolean supportsAccessibilityAction(int action) {
        return mAccessibilityInjector.supportsAccessibilityAction(action);
    }

    /**
     * Attempts to perform an accessibility action on the web content.  If the accessibility action
     * cannot be processed, it returns {@code null}, allowing the caller to know to call the
     * super {@link View#performAccessibilityAction(int, Bundle)} method and use that return value.
     * Otherwise the return value from this method should be used.
     * @param action The action to perform.
     * @param arguments Optional action arguments.
     * @return Whether the action was performed or {@code null} if the call should be delegated to
     *         the super {@link View} class.
     */
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (mAccessibilityInjector.supportsAccessibilityAction(action)) {
            return mAccessibilityInjector.performAccessibilityAction(action, arguments);
        }

        return false;
    }

    /**
     * @see View#onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo)
     */
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        mAccessibilityInjector.onInitializeAccessibilityNodeInfo(info);
    }

    /**
     * @see View#onInitializeAccessibilityEvent(AccessibilityEvent)
     */
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        event.setClassName(this.getClass().getName());

        // Identify where the top-left of the screen currently points to.
        event.setScrollX(mRenderCoordinates.getScrollXPixInt());
        event.setScrollY(mRenderCoordinates.getScrollYPixInt());

        // The maximum scroll values are determined by taking the content dimensions and
        // subtracting off the actual dimensions of the ChromeView.
        int maxScrollXPix = Math.max(0, mRenderCoordinates.getMaxHorizontalScrollPixInt());
        int maxScrollYPix = Math.max(0, mRenderCoordinates.getMaxVerticalScrollPixInt());
        event.setScrollable(maxScrollXPix > 0 || maxScrollYPix > 0);

        // Setting the maximum scroll values requires API level 15 or higher.
        final int SDK_VERSION_REQUIRED_TO_SET_SCROLL = 15;
        if (Build.VERSION.SDK_INT >= SDK_VERSION_REQUIRED_TO_SET_SCROLL) {
            event.setMaxScrollX(maxScrollXPix);
            event.setMaxScrollY(maxScrollYPix);
        }
    }

    /**
     * Returns whether or not accessibility injection is being used.
     */
    public boolean isInjectingAccessibilityScript() {
        return mAccessibilityInjector.accessibilityIsAvailable();
    }

    /**
     * Enable or disable accessibility features.
     */
    public void setAccessibilityState(boolean state) {
        mAccessibilityInjector.setScriptEnabled(state);
    }

    /**
     * Stop any TTS notifications that are currently going on.
     */
    public void stopCurrentAccessibilityNotifications() {
        mAccessibilityInjector.onPageLostFocus();
    }

    /**
     * Inform WebKit that Fullscreen mode has been exited by the user.
     */
    public void exitFullscreen() {
        nativeExitFullscreen(mNativeContentViewCore);
    }

    /**
     * Changes whether hiding the top controls is enabled.
     *
     * @param enableHiding Whether hiding the top controls should be enabled or not.
     * @param enableShowing Whether showing the top controls should be enabled or not.
     * @param animate Whether the transition should be animated or not.
     */
    public void updateTopControlsState(boolean enableHiding, boolean enableShowing,
            boolean animate) {
        nativeUpdateTopControlsState(mNativeContentViewCore, enableHiding, enableShowing, animate);
    }

    /**
     * @See android.webkit.WebView#pageDown(boolean)
     */
    public boolean pageDown(boolean bottom) {
        final int maxVerticalScrollPix = mRenderCoordinates.getMaxVerticalScrollPixInt();
        if (computeVerticalScrollOffset() >= maxVerticalScrollPix) {
            // We seem to already be at the bottom of the page, so no scrolling will occur.
            return false;
        }

        if (bottom) {
            scrollTo(computeHorizontalScrollOffset(), maxVerticalScrollPix);
        } else {
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN));
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN));
        }
        return true;
    }

    /**
     * @See android.webkit.WebView#pageUp(boolean)
     */
    public boolean pageUp(boolean top) {
        if (computeVerticalScrollOffset() == 0) {
            // We seem to already be at the top of the page, so no scrolling will occur.
            return false;
        }

        if (top) {
            scrollTo(computeHorizontalScrollOffset(), 0);
        } else {
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP));
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_UP));
        }
        return true;
    }

    /**
     * Callback factory method for nativeGetNavigationHistory().
     */
    @CalledByNative
    private void addToNavigationHistory(Object history, int index, String url, String virtualUrl,
            String originalUrl, String title, Bitmap favicon) {
        NavigationEntry entry = new NavigationEntry(
                index, url, virtualUrl, originalUrl, title, favicon);
        ((NavigationHistory) history).addEntry(entry);
    }

    /**
     * Get a copy of the navigation history of the view.
     */
    public NavigationHistory getNavigationHistory() {
        NavigationHistory history = new NavigationHistory();
        int currentIndex = nativeGetNavigationHistory(mNativeContentViewCore, history);
        history.setCurrentEntryIndex(currentIndex);
        return history;
    }

    @Override
    public NavigationHistory getDirectedNavigationHistory(boolean isForward, int itemLimit) {
        NavigationHistory history = new NavigationHistory();
        nativeGetDirectedNavigationHistory(mNativeContentViewCore, history, isForward, itemLimit);
        return history;
    }

    /**
     * @return The original request URL for the current navigation entry, or null if there is no
     *         current entry.
     */
    public String getOriginalUrlForActiveNavigationEntry() {
        return nativeGetOriginalUrlForActiveNavigationEntry(mNativeContentViewCore);
    }

    /**
     * @return The cached copy of render positions and scales.
     */
    public RenderCoordinates getRenderCoordinates() {
        return mRenderCoordinates;
    }

    @CalledByNative
    private static Rect createRect(int x, int y, int right, int bottom) {
        return new Rect(x, y, right, bottom);
    }

    public void attachExternalVideoSurface(int playerId, Surface surface) {
        if (mNativeContentViewCore != 0) {
            nativeAttachExternalVideoSurface(mNativeContentViewCore, playerId, surface);
        }
    }

    public void detachExternalVideoSurface(int playerId) {
        if (mNativeContentViewCore != 0) {
            nativeDetachExternalVideoSurface(mNativeContentViewCore, playerId);
        }
    }

    private boolean onAnimate(long frameTimeMicros) {
        if (mNativeContentViewCore == 0) return false;
        return nativeOnAnimate(mNativeContentViewCore, frameTimeMicros);
    }

    private void animateIfNecessary(long frameTimeMicros) {
        if (mNeedAnimate) {
            mNeedAnimate = onAnimate(frameTimeMicros);
            if (!mNeedAnimate) setVSyncNotificationEnabled(false);
        }
    }

    @CalledByNative
    private void notifyExternalSurface(
            int playerId, boolean isRequest, float x, float y, float width, float height) {
        RenderCoordinates.NormalizedPoint topLeft = mRenderCoordinates.createNormalizedPoint();
        RenderCoordinates.NormalizedPoint bottomRight = mRenderCoordinates.createNormalizedPoint();
        topLeft.setLocalDip(x * getScale(), y * getScale());
        bottomRight.setLocalDip((x + width) * getScale(), (y + height) * getScale());

        if (isRequest) getContentViewClient().onExternalVideoSurfaceRequested(playerId);
        getContentViewClient().onGeometryChanged(
                playerId,
                topLeft.getXPix(),
                topLeft.getYPix(),
                bottomRight.getXPix() - topLeft.getXPix(),
                bottomRight.getYPix() - topLeft.getYPix());
    }

    private native int nativeInit(boolean hardwareAccelerated, int webContentsPtr,
            int viewAndroidPtr, int windowAndroidPtr);

    private native void nativeOnJavaContentViewCoreDestroyed(int nativeContentViewCoreImpl);

    private native void nativeLoadUrl(
            int nativeContentViewCoreImpl,
            String url,
            int loadUrlType,
            int transitionType,
            int uaOverrideOption,
            String extraHeaders,
            byte[] postData,
            String baseUrlForDataUrl,
            String virtualUrlForDataUrl,
            boolean canLoadLocalResources);

    private native String nativeGetURL(int nativeContentViewCoreImpl);

    private native String nativeGetTitle(int nativeContentViewCoreImpl);

    private native void nativeShowInterstitialPage(
            int nativeContentViewCoreImpl, String url, int nativeInterstitialPageDelegateAndroid);
    private native boolean nativeIsShowingInterstitialPage(int nativeContentViewCoreImpl);

    private native boolean nativeIsIncognito(int nativeContentViewCoreImpl);

    // Returns true if the native side crashed so that java side can draw a sad tab.
    private native boolean nativeCrashed(int nativeContentViewCoreImpl);

    private native void nativeSetFocus(int nativeContentViewCoreImpl, boolean focused);

    private native void nativeSendOrientationChangeEvent(
            int nativeContentViewCoreImpl, int orientation);

    // All touch events (including flings, scrolls etc) accept coordinates in physical pixels.
    private native boolean nativeSendTouchEvent(
            int nativeContentViewCoreImpl, long timeMs, int action, TouchPoint[] pts);

    private native int nativeSendMouseMoveEvent(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native int nativeSendMouseWheelEvent(
            int nativeContentViewCoreImpl, long timeMs, float x, float y, float verticalAxis);

    private native void nativeScrollBegin(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeScrollEnd(int nativeContentViewCoreImpl, long timeMs);

    private native void nativeScrollBy(
            int nativeContentViewCoreImpl, long timeMs, float x, float y,
            float deltaX, float deltaY, boolean lastInputEventForVSync);

    private native void nativeFlingStart(
            int nativeContentViewCoreImpl, long timeMs, float x, float y, float vx, float vy);

    private native void nativeFlingCancel(int nativeContentViewCoreImpl, long timeMs);

    private native void nativeSingleTap(
            int nativeContentViewCoreImpl, long timeMs, float x, float y, boolean linkPreviewTap);

    private native void nativeSingleTapUnconfirmed(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeShowPressState(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeShowPressCancel(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeDoubleTap(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeLongPress(
            int nativeContentViewCoreImpl, long timeMs, float x, float y, boolean linkPreviewTap);

    private native void nativeLongTap(
            int nativeContentViewCoreImpl, long timeMs, float x, float y, boolean linkPreviewTap);

    private native void nativePinchBegin(
            int nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativePinchEnd(int nativeContentViewCoreImpl, long timeMs);

    private native void nativePinchBy(int nativeContentViewCoreImpl, long timeMs,
            float anchorX, float anchorY, float deltaScale, boolean lastInputEventForVSync);

    private native void nativeSelectBetweenCoordinates(
            int nativeContentViewCoreImpl, float x1, float y1, float x2, float y2);

    private native void nativeMoveCaret(int nativeContentViewCoreImpl, float x, float y);

    private native boolean nativeCanGoBack(int nativeContentViewCoreImpl);
    private native boolean nativeCanGoForward(int nativeContentViewCoreImpl);
    private native boolean nativeCanGoToOffset(int nativeContentViewCoreImpl, int offset);
    private native void nativeGoBack(int nativeContentViewCoreImpl);
    private native void nativeGoForward(int nativeContentViewCoreImpl);
    private native void nativeGoToOffset(int nativeContentViewCoreImpl, int offset);
    private native void nativeGoToNavigationIndex(int nativeContentViewCoreImpl, int index);

    private native void nativeStopLoading(int nativeContentViewCoreImpl);

    private native void nativeReload(int nativeContentViewCoreImpl);

    private native void nativeCancelPendingReload(int nativeContentViewCoreImpl);

    private native void nativeContinuePendingReload(int nativeContentViewCoreImpl);

    private native void nativeSelectPopupMenuItems(int nativeContentViewCoreImpl, int[] indices);

    private native void nativeScrollFocusedEditableNodeIntoView(int nativeContentViewCoreImpl);
    private native void nativeUndoScrollFocusedEditableNodeIntoView(int nativeContentViewCoreImpl);
    private native boolean nativeNeedsReload(int nativeContentViewCoreImpl);

    private native void nativeClearHistory(int nativeContentViewCoreImpl);

    private native void nativeEvaluateJavaScript(int nativeContentViewCoreImpl,
            String script, JavaScriptCallback callback);

    private native int nativeGetNativeImeAdapter(int nativeContentViewCoreImpl);

    private native int nativeGetCurrentRenderProcessId(int nativeContentViewCoreImpl);

    private native int nativeGetBackgroundColor(int nativeContentViewCoreImpl);

    private native void nativeSetBackgroundColor(int nativeContentViewCoreImpl, int color);

    private native void nativeOnShow(int nativeContentViewCoreImpl);
    private native void nativeOnHide(int nativeContentViewCoreImpl);

    private native void nativeSetUseDesktopUserAgent(int nativeContentViewCoreImpl,
            boolean enabled, boolean reloadOnChange);
    private native boolean nativeGetUseDesktopUserAgent(int nativeContentViewCoreImpl);

    private native void nativeClearSslPreferences(int nativeContentViewCoreImpl);

    private native void nativeAddJavascriptInterface(int nativeContentViewCoreImpl, Object object,
            String name, Class requiredAnnotation, HashSet<Object> retainedObjectSet);

    private native void nativeRemoveJavascriptInterface(int nativeContentViewCoreImpl, String name);

    private native int nativeGetNavigationHistory(int nativeContentViewCoreImpl, Object context);
    private native void nativeGetDirectedNavigationHistory(int nativeContentViewCoreImpl,
            Object context, boolean isForward, int maxEntries);
    private native String nativeGetOriginalUrlForActiveNavigationEntry(
            int nativeContentViewCoreImpl);

    private native void nativeUpdateVSyncParameters(int nativeContentViewCoreImpl,
            long timebaseMicros, long intervalMicros);

    private native void nativeOnVSync(int nativeContentViewCoreImpl, long frameTimeMicros);

    private native boolean nativeOnAnimate(int nativeContentViewCoreImpl, long frameTimeMicros);

    private native boolean nativePopulateBitmapFromCompositor(int nativeContentViewCoreImpl,
            Bitmap bitmap);

    private native void nativeWasResized(int nativeContentViewCoreImpl);

    private native boolean nativeIsRenderWidgetHostViewReady(int nativeContentViewCoreImpl);

    private native void nativeExitFullscreen(int nativeContentViewCoreImpl);
    private native void nativeUpdateTopControlsState(int nativeContentViewCoreImpl,
            boolean enableHiding, boolean enableShowing, boolean animate);

    private native void nativeShowImeIfNeeded(int nativeContentViewCoreImpl);

    private native void nativeAttachExternalVideoSurface(
            int nativeContentViewCoreImpl, int playerId, Surface surface);

    private native void nativeDetachExternalVideoSurface(
            int nativeContentViewCoreImpl, int playerId);
}
