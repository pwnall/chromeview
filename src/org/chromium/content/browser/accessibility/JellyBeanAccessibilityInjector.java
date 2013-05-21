// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.JavascriptInterface;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles injecting accessibility Javascript and related Javascript -> Java APIs for JB and newer
 * devices.
 */
class JellyBeanAccessibilityInjector extends AccessibilityInjector {
    private CallbackHandler mCallback;
    private JSONObject mAccessibilityJSONObject;

    private static final String ALIAS_TRAVERSAL_JS_INTERFACE = "accessibilityTraversal";

    // Template for JavaScript that performs AndroidVox actions.
    private static final String ACCESSIBILITY_ANDROIDVOX_TEMPLATE =
            "cvox.AndroidVox.performAction('%1s')";

    /**
     * Constructs an instance of the JellyBeanAccessibilityInjector.
     * @param view The ContentViewCore that this AccessibilityInjector manages.
     */
    protected JellyBeanAccessibilityInjector(ContentViewCore view) {
        super(view);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH |
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
        info.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        info.addAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
        info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.setClickable(true);
    }

    @Override
    public boolean supportsAccessibilityAction(int action) {
        if (action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ||
                action == AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY ||
                action == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT ||
                action == AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT ||
                action == AccessibilityNodeInfo.ACTION_CLICK) {
            return true;
        }

        return false;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (!accessibilityIsAvailable() || !mContentViewCore.isAlive() ||
                !mInjectedScriptEnabled || !mScriptInjected) {
            return false;
        }

        boolean actionSuccessful = sendActionToAndroidVox(action, arguments);

        if (actionSuccessful) mContentViewCore.showImeIfNeeded();

        return actionSuccessful;
    }

    @Override
    protected void addAccessibilityApis() {
        super.addAccessibilityApis();

        Context context = mContentViewCore.getContext();
        if (context != null && mCallback == null) {
            mCallback = new CallbackHandler(ALIAS_TRAVERSAL_JS_INTERFACE);
            mContentViewCore.addJavascriptInterface(mCallback, ALIAS_TRAVERSAL_JS_INTERFACE);
        }
    }

    @Override
    protected void removeAccessibilityApis() {
        super.removeAccessibilityApis();

        if (mCallback != null) {
            mContentViewCore.removeJavascriptInterface(ALIAS_TRAVERSAL_JS_INTERFACE);
            mCallback = null;
        }
    }

    /**
     * Packs an accessibility action into a JSON object and sends it to AndroidVox.
     *
     * @param action The action identifier.
     * @param arguments The action arguments, if applicable.
     * @return The result of the action.
     */
    private boolean sendActionToAndroidVox(int action, Bundle arguments) {
        if (mCallback == null) return false;
        if (mAccessibilityJSONObject == null) {
            mAccessibilityJSONObject = new JSONObject();
        } else {
            // Remove all keys from the object.
            final Iterator<?> keys = mAccessibilityJSONObject.keys();
            while (keys.hasNext()) {
                keys.next();
                keys.remove();
            }
        }

        try {
            mAccessibilityJSONObject.accumulate("action", action);
            if (arguments != null) {
                if (action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ||
                        action == AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
                    final int granularity = arguments.getInt(AccessibilityNodeInfo.
                            ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                    mAccessibilityJSONObject.accumulate("granularity", granularity);
                } else if (action == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT ||
                        action == AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT) {
                    final String element = arguments.getString(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING);
                    mAccessibilityJSONObject.accumulate("element", element);
                }
            }
        } catch (JSONException ex) {
            return false;
        }

        final String jsonString = mAccessibilityJSONObject.toString();
        final String jsCode = String.format(ACCESSIBILITY_ANDROIDVOX_TEMPLATE, jsonString);
        return mCallback.performAction(mContentViewCore, jsCode);
    }

    private static class CallbackHandler {
        private static final String JAVASCRIPT_ACTION_TEMPLATE =
                "(function() {" +
                "  retVal = false;" +
                "  try {" +
                "    retVal = %s;" +
                "  } catch (e) {" +
                "    retVal = false;" +
                "  }" +
                "  %s.onResult(%d, retVal);" +
                "})()";

        // Time in milliseconds to wait for a result before failing.
        private static final long RESULT_TIMEOUT = 5000;

        private final AtomicInteger mResultIdCounter = new AtomicInteger();
        private final Object mResultLock = new Object();
        private final String mInterfaceName;

        private boolean mResult = false;
        private long mResultId = -1;

        private CallbackHandler(String interfaceName) {
            mInterfaceName = interfaceName;
        }

        /**
         * Performs an action and attempts to wait for a result.
         *
         * @param contentView The ContentViewCore to perform the action on.
         * @param code Javascript code that evaluates to a result.
         * @return The result of the action.
         */
        private boolean performAction(ContentViewCore contentView, String code) {
            final int resultId = mResultIdCounter.getAndIncrement();
            final String js = String.format(JAVASCRIPT_ACTION_TEMPLATE, code, mInterfaceName,
                    resultId);
            contentView.evaluateJavaScript(js, null);

            return getResultAndClear(resultId);
        }

        /**
         * Gets the result of a request to perform an accessibility action.
         *
         * @param resultId The result id to match the result with the request.
         * @return The result of the request.
         */
        private boolean getResultAndClear(int resultId) {
            synchronized (mResultLock) {
                final boolean success = waitForResultTimedLocked(resultId);
                final boolean result = success ? mResult : false;
                clearResultLocked();
                return result;
            }
        }

        /**
         * Clears the result state.
         */
        private void clearResultLocked() {
            mResultId = -1;
            mResult = false;
        }

        /**
         * Waits up to a given bound for a result of a request and returns it.
         *
         * @param resultId The result id to match the result with the request.
         * @return Whether the result was received.
         */
        private boolean waitForResultTimedLocked(int resultId) {
            long waitTimeMillis = RESULT_TIMEOUT;
            final long startTimeMillis = SystemClock.uptimeMillis();
            while (true) {
                try {
                    if (mResultId == resultId) return true;
                    if (mResultId > resultId) return false;
                    final long elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
                    waitTimeMillis = RESULT_TIMEOUT - elapsedTimeMillis;
                    if (waitTimeMillis <= 0) return false;
                    mResultLock.wait(waitTimeMillis);
                } catch (InterruptedException ie) {
                    /* ignore */
                }
            }
        }

        /**
         * Callback exposed to JavaScript.  Handles returning the result of a
         * request to a waiting (or potentially timed out) thread.
         *
         * @param id The result id of the request as a {@link String}.
         * @param result The result of a request as a {@link String}.
         */
        @JavascriptInterface
        @SuppressWarnings("unused")
        public void onResult(String id, String result) {
            final long resultId;
             try {
                resultId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                return;
            }

            synchronized (mResultLock) {
                if (resultId > mResultId) {
                    mResult = Boolean.parseBoolean(result);
                    mResultId = resultId;
                }
                mResultLock.notifyAll();
            }
        }
    }
}
