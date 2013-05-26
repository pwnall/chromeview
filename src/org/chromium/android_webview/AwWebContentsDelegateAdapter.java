// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;

import org.chromium.content.browser.ContentViewCore;

/**
 * Adapts the AwWebContentsDelegate interface to the AwContentsClient interface.
 * This class also serves a secondary function of routing certain callbacks from the content layer
 * to specific listener interfaces.
 */
class AwWebContentsDelegateAdapter extends AwWebContentsDelegate {
    private static final String TAG = "AwWebContentsDelegateAdapter";

    /**
     * Listener definition for a callback to be invoked when the preferred size of the page
     * contents changes.
     */
    public interface PreferredSizeChangedListener {
        /**
         * Called when the preferred size of the page contents changes.
         * @see AwWebContentsDelegate#updatePreferredSize
         */
        void updatePreferredSize(int width, int height);
    }

    final AwContentsClient mContentsClient;
    final PreferredSizeChangedListener mPreferredSizeChangedListener;

    public AwWebContentsDelegateAdapter(AwContentsClient contentsClient,
            PreferredSizeChangedListener preferredSizeChangedListener) {
        mContentsClient = contentsClient;
        mPreferredSizeChangedListener = preferredSizeChangedListener;
    }

    @Override
    public void onLoadProgressChanged(int progress) {
        mContentsClient.onProgressChanged(progress);
    }

    @Override
    public void handleKeyboardEvent(KeyEvent event) {
        mContentsClient.onUnhandledKeyEvent(event);
    }

    @Override
    public boolean addMessageToConsole(int level, String message, int lineNumber,
            String sourceId) {
        ConsoleMessage.MessageLevel messageLevel = ConsoleMessage.MessageLevel.DEBUG;
        switch(level) {
            case LOG_LEVEL_TIP:
                messageLevel = ConsoleMessage.MessageLevel.TIP;
                break;
            case LOG_LEVEL_LOG:
                messageLevel = ConsoleMessage.MessageLevel.LOG;
                break;
            case LOG_LEVEL_WARNING:
                messageLevel = ConsoleMessage.MessageLevel.WARNING;
                break;
            case LOG_LEVEL_ERROR:
                messageLevel = ConsoleMessage.MessageLevel.ERROR;
                break;
            default:
                Log.w(TAG, "Unknown message level, defaulting to DEBUG");
                break;
        }

        return mContentsClient.onConsoleMessage(
                new ConsoleMessage(message, sourceId, lineNumber, messageLevel));
    }

    @Override
    public void onUpdateUrl(String url) {
        // TODO: implement
    }

    @Override
    public void openNewTab(String url, boolean incognito) {
        // TODO: implement
    }

    @Override
    public boolean addNewContents(int nativeSourceWebContents, int nativeWebContents,
            int disposition, Rect initialPosition, boolean userGesture) {
        // TODO: implement
        return false;
    }

    @Override
    public void closeContents() {
        mContentsClient.onCloseWindow();
    }

    @Override
    public void showRepostFormWarningDialog(final ContentViewCore contentViewCore) {
        // TODO(mkosiba) We should be using something akin to the JsResultReceiver as the
        // callback parameter (instead of ContentViewCore) and implement a way of converting
        // that to a pair of messages.
        final int MSG_CONTINUE_PENDING_RELOAD = 1;
        final int MSG_CANCEL_PENDING_RELOAD = 2;

        // TODO(sgurun) Remember the URL to cancel the reload behavior
        // if it is different than the most recent NavigationController entry.
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_CONTINUE_PENDING_RELOAD: {
                        contentViewCore.continuePendingReload();
                        break;
                    }
                    case MSG_CANCEL_PENDING_RELOAD: {
                        contentViewCore.cancelPendingReload();
                        break;
                    }
                    default:
                        throw new IllegalStateException(
                                "WebContentsDelegateAdapter: unhandled message " + msg.what);
                }
            }
        };

        Message resend = handler.obtainMessage(MSG_CONTINUE_PENDING_RELOAD);
        Message dontResend = handler.obtainMessage(MSG_CANCEL_PENDING_RELOAD);
        mContentsClient.onFormResubmission(dontResend, resend);
    }

    @Override
    public boolean addNewContents(boolean isDialog, boolean isUserGesture) {
        return mContentsClient.onCreateWindow(isDialog, isUserGesture);
    }

    @Override
    public void activateContents() {
        mContentsClient.onRequestFocus();
    }

    @Override
    public void updatePreferredSize(int width, int height) {
        mPreferredSizeChangedListener.updatePreferredSize(width, height);
    }
}
