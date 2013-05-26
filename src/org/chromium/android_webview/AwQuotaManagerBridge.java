// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

import android.webkit.ValueCallback;

import java.util.Map;
import java.util.HashMap;

/**
 * Bridge between android.webview.WebStorage and native QuotaManager. This object is owned by Java
 * AwBrowserContext and the native side is owned by the native AwBrowserContext.
 *
 * TODO(boliu): Actually make this true after Java AwBrowserContext is added.
 */
@JNINamespace("android_webview")
public class AwQuotaManagerBridge {
    // TODO(boliu): This should be obtained from Java AwBrowserContext that owns this.
    private static native int nativeGetDefaultNativeAwQuotaManagerBridge();

    // TODO(boliu): This should be owned by Java AwBrowserContext, not a singleton.
    private static AwQuotaManagerBridge sInstance;
    public static AwQuotaManagerBridge getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = new AwQuotaManagerBridge(nativeGetDefaultNativeAwQuotaManagerBridge());
        }
        return sInstance;
    }

    /**
     * This class represent the callback value of android.webview.WebStorage.getOrigins. The values
     * are optimized for JNI convenience and need to be converted.
     */
    public static class Origins {
        // Origin, usage, and quota data in parallel arrays of same length.
        public final String[] mOrigins;
        public final long[] mUsages;
        public final long[] mQuotas;

        Origins(String[] origins, long[] usages, long[] quotas) {
            mOrigins = origins;
            mUsages = usages;
            mQuotas = quotas;
        }
    }

    // This is not owning. The native object is owned by the native AwBrowserContext.
    private int mNativeAwQuotaManagerBridgeImpl;

    // The Java callbacks are saved here. An incrementing callback id is generated for each saved
    // callback and is passed to the native side to identify callback.
    private int mNextId;
    private Map<Integer, ValueCallback<Origins>> mPendingGetOriginCallbacks;
    private Map<Integer, ValueCallback<Long>> mPendingGetQuotaForOriginCallbacks;
    private Map<Integer, ValueCallback<Long>> mPendingGetUsageForOriginCallbacks;

    private AwQuotaManagerBridge(int nativeAwQuotaManagerBridgeImpl) {
        mNativeAwQuotaManagerBridgeImpl = nativeAwQuotaManagerBridgeImpl;
        mPendingGetOriginCallbacks =
                new HashMap<Integer, ValueCallback<Origins>>();
        mPendingGetQuotaForOriginCallbacks = new HashMap<Integer, ValueCallback<Long>>();
        mPendingGetUsageForOriginCallbacks = new HashMap<Integer, ValueCallback<Long>>();
        nativeInit(mNativeAwQuotaManagerBridgeImpl);
    }

    private int getNextId() {
        ThreadUtils.assertOnUiThread();
        return ++mNextId;
    }

    /*
     * There are five HTML5 offline storage APIs.
     * 1) Web Storage (ie the localStorage and sessionStorage variables)
     * 2) Web SQL database
     * 3) Application cache
     * 4) Indexed Database
     * 5) Filesystem API
     */

    /**
     * Implements WebStorage.deleteAllData(). Clear the storage of all five offline APIs.
     *
     * TODO(boliu): Actually clear Web Storage.
     */
    public void deleteAllData() {
        nativeDeleteAllData(mNativeAwQuotaManagerBridgeImpl);
    }

    /**
     * Implements WebStorage.deleteOrigin(). Clear the storage of APIs 2-5 for the given origin.
     */
    public void deleteOrigin(String origin) {
        nativeDeleteOrigin(mNativeAwQuotaManagerBridgeImpl, origin);
    }

    /**
     * Implements WebStorage.getOrigins. Get the per origin usage and quota of APIs 2-5 in
     * aggregate.
     */
    public void getOrigins(ValueCallback<Origins> callback) {
        int callbackId = getNextId();
        assert !mPendingGetOriginCallbacks.containsKey(callbackId);
        mPendingGetOriginCallbacks.put(callbackId, callback);
        nativeGetOrigins(mNativeAwQuotaManagerBridgeImpl, callbackId);
    }

    /**
     * Implements WebStorage.getQuotaForOrigin. Get the quota of APIs 2-5 in aggregate for given
     * origin.
     */
    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        int callbackId = getNextId();
        assert !mPendingGetQuotaForOriginCallbacks.containsKey(callbackId);
        mPendingGetQuotaForOriginCallbacks.put(callbackId, callback);
        nativeGetUsageAndQuotaForOrigin(mNativeAwQuotaManagerBridgeImpl, origin, callbackId, true);
    }

    /**
     * Implements WebStorage.getUsageForOrigin. Get the usage of APIs 2-5 in aggregate for given
     * origin.
     */
    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        int callbackId = getNextId();
        assert !mPendingGetUsageForOriginCallbacks.containsKey(callbackId);
        mPendingGetUsageForOriginCallbacks.put(callbackId, callback);
        nativeGetUsageAndQuotaForOrigin(mNativeAwQuotaManagerBridgeImpl, origin, callbackId, false);
    }

    @CalledByNative
    private void onGetOriginsCallback(int callbackId, String[] origin, long[] usages,
            long[] quotas) {
        assert mPendingGetOriginCallbacks.containsKey(callbackId);
        mPendingGetOriginCallbacks.remove(callbackId).onReceiveValue(
            new Origins(origin, usages, quotas));
    }

    @CalledByNative
    private void onGetUsageAndQuotaForOriginCallback(
            int callbackId, boolean isQuota, long usage, long quota) {
        if (isQuota) {
          assert mPendingGetQuotaForOriginCallbacks.containsKey(callbackId);
          mPendingGetQuotaForOriginCallbacks.remove(callbackId).onReceiveValue(quota);
        } else {
          assert mPendingGetUsageForOriginCallbacks.containsKey(callbackId);
          mPendingGetUsageForOriginCallbacks.remove(callbackId).onReceiveValue(usage);
        }
    }

    private native void nativeInit(int nativeAwQuotaManagerBridgeImpl);
    private native void nativeDeleteAllData(int nativeAwQuotaManagerBridgeImpl);
    private native void nativeDeleteOrigin(int nativeAwQuotaManagerBridgeImpl, String origin);
    private native void nativeGetOrigins(int nativeAwQuotaManagerBridgeImpl, int callbackId);
    private native void nativeGetUsageAndQuotaForOrigin(int nativeAwQuotaManagerBridgeImpl,
            String origin, int callbackId, boolean isQuota);
}
