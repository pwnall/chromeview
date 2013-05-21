// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.web_contents_delegate_android;

import android.graphics.Rect;
import android.view.KeyEvent;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

/**
 * Java peer of the native class of the same name.
 */
@JNINamespace("components")
public class WebContentsDelegateAndroid {

    // Equivalent of WebCore::WebConsoleMessage::LevelTip.
    public static final int LOG_LEVEL_TIP = 0;
    // Equivalent of WebCore::WebConsoleMessage::LevelLog.
    public static final int LOG_LEVEL_LOG = 1;
    // Equivalent of WebCore::WebConsoleMessage::LevelWarning.
    public static final int LOG_LEVEL_WARNING = 2;
    // Equivalent of WebCore::WebConsoleMessage::LevelError.
    public static final int LOG_LEVEL_ERROR = 3;
    // The most recent load progress callback received from WebContents, as a percentage.
    // Initialize to 100 to indicate that we're not in a loading state.
    private int mMostRecentProgress = 100;

    public int getMostRecentProgress() {
        return mMostRecentProgress;
    }

    @CalledByNative
    public void openNewTab(String url, boolean incognito) {
    }

    @CalledByNative
    public boolean addNewContents(int nativeSourceWebContents, int nativeWebContents,
            int disposition, Rect initialPosition, boolean userGesture) {
        return false;
    }

    @CalledByNative
    public void closeContents() {
    }

    @CalledByNative
    public void onLoadStarted() {
    }

    @CalledByNative
    public void onLoadStopped() {
    }

    @CalledByNative
    public void onTitleUpdated() {
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private final void notifyLoadProgressChanged(double progress) {
        mMostRecentProgress = (int) (100.0 * progress);
        onLoadProgressChanged(mMostRecentProgress);
    }

    /**
     * @param progress The load progress [0, 100] for the current web contents.
     */
    public void onLoadProgressChanged(int progress) {
    }

    /**
     * Signaled when the renderer has been deemed to be unresponsive.
     */
    @CalledByNative
    public void rendererUnresponsive() {
    }

    /**
     * Signaled when the render has been deemed to be responsive.
     */
    @CalledByNative
    public void rendererResponsive() {
    }

    @CalledByNative
    public void onUpdateUrl(String url) {
    }

    @CalledByNative
    public boolean takeFocus(boolean reverse) {
        return false;
    }

    @CalledByNative
    public void handleKeyboardEvent(KeyEvent event) {
        // TODO(bulach): we probably want to re-inject the KeyEvent back into
        // the system. Investigate if this is at all possible.
    }

    /**
     * Report a JavaScript console message.
     *
     * @param level message level. One of WebContentsDelegateAndroid.LOG_LEVEL*.
     * @param message the error message.
     * @param lineNumber the line number int the source file at which the error is reported.
     * @param sourceId the name of the source file that caused the error.
     * @return true if the client will handle logging the message.
     */
    @CalledByNative
    public boolean addMessageToConsole(int level, String message, int lineNumber,
            String sourceId) {
        return false;
    }

    /**
     * Report a form resubmission. The overwriter of this function should eventually call
     * either of ContentViewCore.ContinuePendingReload or ContentViewCore.CancelPendingReload.
     */
    @CalledByNative
    public void showRepostFormWarningDialog(ContentViewCore contentViewCore) {
    }

    @CalledByNative
    public void toggleFullscreenModeForTab(boolean enterFullscreen) {
    }

    @CalledByNative
    public boolean isFullscreenForTabOrPending() {
        return false;
    }
}
