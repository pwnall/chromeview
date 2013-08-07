// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.InputMethodManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.RenderCoordinates;

import java.util.ArrayList;
import java.util.List;

/**
 * Native accessibility for a {@link ContentViewCore}.
 *
 * This class is safe to load on ICS and can be used to run tests, but
 * only the subclass, JellyBeanBrowserAccessibilityManager, actually
 * has a AccessibilityNodeProvider implementation needed for native
 * accessibility.
 */
@JNINamespace("content")
public class BrowserAccessibilityManager {
    private static final String TAG = "BrowserAccessibilityManager";

    private ContentViewCore mContentViewCore;
    private AccessibilityManager mAccessibilityManager;
    private RenderCoordinates mRenderCoordinates;
    private int mNativeObj;
    private int mAccessibilityFocusId;
    private int mCurrentHoverId;
    private final int[] mTempLocation = new int[2];
    private View mView;
    private boolean mUserHasTouchExplored;
    private boolean mFrameInfoInitialized;

    // If this is true, enables an experimental feature that focuses the web page after it
    // finishes loading. Disabled for now because it can be confusing if the user was
    // trying to do something when this happens.
    private boolean mFocusPageOnLoad;

    /**
     * Create a BrowserAccessibilityManager object, which is owned by the C++
     * BrowserAccessibilityManagerAndroid instance, and connects to the content view.
     * @param nativeBrowserAccessibilityManagerAndroid A pointer to the counterpart native
     *     C++ object that owns this object.
     * @param contentViewCore The content view that this object provides accessibility for.
     */
    @CalledByNative
    private static BrowserAccessibilityManager create(int nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return new JellyBeanBrowserAccessibilityManager(
                    nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        } else {
            return new BrowserAccessibilityManager(
                    nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        }
    }

    protected BrowserAccessibilityManager(int nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        mNativeObj = nativeBrowserAccessibilityManagerAndroid;
        mContentViewCore = contentViewCore;
        mContentViewCore.setBrowserAccessibilityManager(this);
        mAccessibilityFocusId = View.NO_ID;
        mCurrentHoverId = View.NO_ID;
        mView = mContentViewCore.getContainerView();
        mRenderCoordinates = mContentViewCore.getRenderCoordinates();
        mAccessibilityManager =
            (AccessibilityManager) mContentViewCore.getContext()
            .getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @CalledByNative
    private void onNativeObjectDestroyed() {
        if (mContentViewCore.getBrowserAccessibilityManager() == this) {
            mContentViewCore.setBrowserAccessibilityManager(null);
        }
        mNativeObj = 0;
        mContentViewCore = null;
    }

    /**
     * @return An AccessibilityNodeProvider on JellyBean, and null on previous versions.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return null;
    }

    /**
     * @see AccessibilityNodeProvider#createAccessibilityNodeInfo(int)
     */
    protected AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0 || !mFrameInfoInitialized) {
            return null;
        }

        int rootId = nativeGetRootId(mNativeObj);
        if (virtualViewId == View.NO_ID) {
            virtualViewId = rootId;
        }
        if (mAccessibilityFocusId == View.NO_ID) {
            mAccessibilityFocusId = rootId;
        }

        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(mView);
        info.setPackageName(mContentViewCore.getContext().getPackageName());
        info.setSource(mView, virtualViewId);

        if (nativePopulateAccessibilityNodeInfo(mNativeObj, info, virtualViewId)) {
            return info;
        } else {
            return null;
        }
    }

    /**
     * @see AccessibilityNodeProvider#findAccessibilityNodeInfosByText(String, int)
     */
    protected List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,
            int virtualViewId) {
        return new ArrayList<AccessibilityNodeInfo>();
    }

    /**
     * @see AccessibilityNodeProvider#performAction(int, int, Bundle)
     */
    protected boolean performAction(int virtualViewId, int action, Bundle arguments) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0) {
            return false;
        }

        switch (action) {
            case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
                if (mAccessibilityFocusId == virtualViewId) {
                    return true;
                }

                mAccessibilityFocusId = virtualViewId;
                sendAccessibilityEvent(mAccessibilityFocusId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                return true;
            case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                if (mAccessibilityFocusId == virtualViewId) {
                    mAccessibilityFocusId = View.NO_ID;
                }
                return true;
            case AccessibilityNodeInfo.ACTION_CLICK:
                nativeClick(mNativeObj, virtualViewId);
                break;
            case AccessibilityNodeInfo.ACTION_FOCUS:
                nativeFocus(mNativeObj, virtualViewId);
                break;
            case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:
                nativeBlur(mNativeObj);
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * @see View#onHoverEvent(MotionEvent)
     */
    public boolean onHoverEvent(MotionEvent event) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) return true;

        mUserHasTouchExplored = true;
        float x = event.getX();
        float y = event.getY();

        // Convert to CSS coordinates.
        int cssX = (int) (mRenderCoordinates.fromPixToLocalCss(x) +
                          mRenderCoordinates.getScrollX());
        int cssY = (int) (mRenderCoordinates.fromPixToLocalCss(y) +
                          mRenderCoordinates.getScrollY());
        int id = nativeHitTest(mNativeObj, cssX, cssY);
        if (mCurrentHoverId != id) {
            sendAccessibilityEvent(mCurrentHoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            mCurrentHoverId = id;
        }

        return true;
    }

    /**
     * Called by ContentViewCore to notify us when the frame info is initialized,
     * the first time, since until that point, we can't use mRenderCoordinates to transform
     * web coordinates to screen coordinates.
     */
    public void notifyFrameInfoInitialized() {
        if (mFrameInfoInitialized) return;

        mFrameInfoInitialized = true;
        // (Re-) focus focused element, since we weren't able to create an
        // AccessibilityNodeInfo for this element before.
        if (mAccessibilityFocusId != View.NO_ID) {
            sendAccessibilityEvent(mAccessibilityFocusId,
                                   AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        }
    }

    private void sendAccessibilityEvent(int virtualViewId, int eventType) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0) return;

        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mContentViewCore.getContext().getPackageName());
        int rootId = nativeGetRootId(mNativeObj);
        if (virtualViewId == rootId) {
            virtualViewId = View.NO_ID;
        }
        event.setSource(mView, virtualViewId);
        if (!nativePopulateAccessibilityEvent(mNativeObj, event, virtualViewId, eventType)) return;

        // This is currently needed if we want Android to draw the yellow box around
        // the item that has accessibility focus. In practice, this doesn't seem to slow
        // things down, because it's only called when the accessibility focus moves.
        // TODO(dmazzoni): remove this if/when Android framework fixes bug.
        mContentViewCore.getContainerView().postInvalidate();

        mContentViewCore.getContainerView().requestSendAccessibilityEvent(mView, event);
    }

    @CalledByNative
    private void handlePageLoaded(int id) {
        if (mUserHasTouchExplored) return;

        if (mFocusPageOnLoad) {
            // Focus the natively focused node (usually document),
            // if this feature is enabled.
            mAccessibilityFocusId = id;
            sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    @CalledByNative
    private void handleFocusChanged(int id) {
        if (mAccessibilityFocusId == id) return;

        mAccessibilityFocusId = id;
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    @CalledByNative
    private void handleCheckStateChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @CalledByNative
    private void handleTextSelectionChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    @CalledByNative
    private void handleEditableTextChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
    }

    @CalledByNative
    private void handleContentChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @CalledByNative
    private void handleNavigate() {
        mAccessibilityFocusId = View.NO_ID;
        mUserHasTouchExplored = false;
        mFrameInfoInitialized = false;
    }

    @CalledByNative
    private void handleScrolledToAnchor(int id) {
        if (mAccessibilityFocusId == id) {
            return;
        }

        mAccessibilityFocusId = id;
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    }

    @CalledByNative
    private void announceLiveRegionText(String text) {
        mView.announceForAccessibility(text);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoParent(AccessibilityNodeInfo node, int parentId) {
        node.setParent(mView, parentId);
    }

    @CalledByNative
    private void addAccessibilityNodeInfoChild(AccessibilityNodeInfo node, int child_id) {
        node.addChild(mView, child_id);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoBooleanAttributes(AccessibilityNodeInfo node,
            int virtualViewId, boolean checkable, boolean checked, boolean clickable,
            boolean enabled, boolean focusable, boolean focused, boolean password,
            boolean scrollable, boolean selected, boolean visibleToUser) {
        node.setCheckable(checkable);
        node.setChecked(checked);
        node.setClickable(clickable);
        node.setEnabled(enabled);
        node.setFocusable(focusable);
        node.setFocused(focused);
        node.setPassword(password);
        node.setScrollable(scrollable);
        node.setSelected(selected);
        node.setVisibleToUser(visibleToUser);

        if (focusable) {
            if (focused) {
                node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            } else {
                node.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
        }

        if (mAccessibilityFocusId == virtualViewId) {
            node.setAccessibilityFocused(true);
            node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            node.setAccessibilityFocused(false);
            node.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }

        if (clickable) {
            node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    @CalledByNative
    private void setAccessibilityNodeInfoStringAttributes(AccessibilityNodeInfo node,
            String className, String contentDescription) {
        node.setClassName(className);
        node.setContentDescription(contentDescription);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoLocation(AccessibilityNodeInfo node,
            int absoluteLeft, int absoluteTop, int parentRelativeLeft, int parentRelativeTop,
            int width, int height, boolean isRootNode) {
        // First set the bounds in parent.
        Rect boundsInParent = new Rect(parentRelativeLeft, parentRelativeTop,
                parentRelativeLeft + width, parentRelativeTop + height);
        if (isRootNode) {
            // Offset of the web content relative to the View.
            boundsInParent.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
        }
        node.setBoundsInParent(boundsInParent);

        // Now set the absolute rect, which requires several transformations.
        Rect rect = new Rect(absoluteLeft, absoluteTop, absoluteLeft + width, absoluteTop + height);

        // Offset by the scroll position.
        rect.offset(-(int) mRenderCoordinates.getScrollX(),
                    -(int) mRenderCoordinates.getScrollY());

        // Convert CSS (web) pixels to Android View pixels
        rect.left = (int) mRenderCoordinates.fromLocalCssToPix(rect.left);
        rect.top = (int) mRenderCoordinates.fromLocalCssToPix(rect.top);
        rect.bottom = (int) mRenderCoordinates.fromLocalCssToPix(rect.bottom);
        rect.right = (int) mRenderCoordinates.fromLocalCssToPix(rect.right);

        // Offset by the location of the web content within the view.
        rect.offset(0,
                    (int) mRenderCoordinates.getContentOffsetYPix());

        // Finally offset by the location of the view within the screen.
        final int[] viewLocation = new int[2];
        mView.getLocationOnScreen(viewLocation);
        rect.offset(viewLocation[0], viewLocation[1]);

        node.setBoundsInScreen(rect);
    }

    @CalledByNative
    private void setAccessibilityEventBooleanAttributes(AccessibilityEvent event,
            boolean checked, boolean enabled, boolean password, boolean scrollable) {
        event.setChecked(checked);
        event.setEnabled(enabled);
        event.setPassword(password);
        event.setScrollable(scrollable);
    }

    @CalledByNative
    private void setAccessibilityEventClassName(AccessibilityEvent event, String className) {
        event.setClassName(className);
    }

    @CalledByNative
    private void setAccessibilityEventListAttributes(AccessibilityEvent event,
            int currentItemIndex, int itemCount) {
        event.setCurrentItemIndex(currentItemIndex);
        event.setItemCount(itemCount);
    }

    @CalledByNative
    private void setAccessibilityEventScrollAttributes(AccessibilityEvent event,
            int scrollX, int scrollY, int maxScrollX, int maxScrollY) {
        event.setScrollX(scrollX);
        event.setScrollY(scrollY);
        event.setMaxScrollX(maxScrollX);
        event.setMaxScrollY(maxScrollY);
    }

    @CalledByNative
    private void setAccessibilityEventTextChangedAttrs(AccessibilityEvent event,
            int fromIndex, int addedCount, int removedCount, String beforeText, String text) {
        event.setFromIndex(fromIndex);
        event.setAddedCount(addedCount);
        event.setRemovedCount(removedCount);
        event.setBeforeText(beforeText);
        event.getText().add(text);
    }

    @CalledByNative
    private void setAccessibilityEventSelectionAttrs(AccessibilityEvent event,
            int fromIndex, int addedCount, int itemCount, String text) {
        event.setFromIndex(fromIndex);
        event.setAddedCount(addedCount);
        event.setItemCount(itemCount);
        event.getText().add(text);
    }

    private native int nativeGetRootId(int nativeBrowserAccessibilityManagerAndroid);
    private native int nativeHitTest(int nativeBrowserAccessibilityManagerAndroid, int x, int y);
    private native boolean nativePopulateAccessibilityNodeInfo(
        int nativeBrowserAccessibilityManagerAndroid, AccessibilityNodeInfo info, int id);
    private native boolean nativePopulateAccessibilityEvent(
        int nativeBrowserAccessibilityManagerAndroid, AccessibilityEvent event, int id,
        int eventType);
    private native void nativeClick(int nativeBrowserAccessibilityManagerAndroid, int id);
    private native void nativeFocus(int nativeBrowserAccessibilityManagerAndroid, int id);
    private native void nativeBlur(int nativeBrowserAccessibilityManagerAndroid);
}
