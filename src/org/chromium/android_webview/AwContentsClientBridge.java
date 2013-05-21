// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.webkit.ValueCallback;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

/**
 * This class handles the JNI communication logic for the the AwContentsClient class.
 * Both the Java and the native peers of AwContentsClientBridge are owned by the
 * corresponding AwContents instances. This class and its native peer are connected
 * via weak references. The native AwContentsClientBridge sets up and clear these weak
 * references.
 */
@JNINamespace("android_webview")
public class AwContentsClientBridge {

    private AwContentsClient mClient;
    // The native peer of this object.
    private int mNativeContentsClientBridge;

    public AwContentsClientBridge(AwContentsClient client) {
        assert client != null;
        mClient = client;
    }

    // Used by the native peer to set/reset a weak ref to the native peer.
    @CalledByNative
    private void setNativeContentsClientBridge(int nativeContentsClientBridge) {
        mNativeContentsClientBridge = nativeContentsClientBridge;
    }

    // If returns false, the request is immediately canceled, and any call to proceedSslError
    // has no effect. If returns true, the request should be canceled or proceeded using
    // proceedSslError().
    // Unlike the webview classic, we do not keep keep a database of certificates that
    // are allowed by the user, because this functionality is already handled via
    // ssl_policy in native layers.
    @CalledByNative
    private boolean allowCertificateError(int certError, byte[] derBytes, final String url,
            final int id) {
        final SslCertificate cert = SslUtil.getCertificateFromDerBytes(derBytes);
        if (cert == null) {
            // if the certificate or the client is null, cancel the request
            return false;
        }
        final SslError sslError = SslUtil.sslErrorFromNetErrorCode(certError, cert, url);
        ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
                proceedSslError(value.booleanValue(), id);
            }
        };
        mClient.onReceivedSslError(callback, sslError);
        return true;
    }

    private void proceedSslError(boolean proceed, int id) {
        if (mNativeContentsClientBridge == 0) return;
        nativeProceedSslError(mNativeContentsClientBridge, proceed, id);
    }

    @CalledByNative
    private void handleJsAlert(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsAlert(url, message, handler);
    }

    @CalledByNative
    private void handleJsConfirm(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsConfirm(url, message, handler);
    }

    @CalledByNative
    private void handleJsPrompt(String url, String message, String defaultValue, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsPrompt(url, message, defaultValue, handler);
    }

    @CalledByNative
    private void handleJsBeforeUnload(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsBeforeUnload(url, message, handler);
    }

    void confirmJsResult(int id, String prompt) {
        if (mNativeContentsClientBridge == 0) return;
        nativeConfirmJsResult(mNativeContentsClientBridge, id, prompt);
    }

    void cancelJsResult(int id) {
        if (mNativeContentsClientBridge == 0) return;
        nativeCancelJsResult(mNativeContentsClientBridge, id);
    }

    //--------------------------------------------------------------------------------------------
    //  Native methods
    //--------------------------------------------------------------------------------------------
    private native void nativeProceedSslError(int nativeAwContentsClientBridge, boolean proceed,
            int id);

    private native void nativeConfirmJsResult(int nativeAwContentsClientBridge, int id,
            String prompt);
    private native void nativeCancelJsResult(int nativeAwContentsClientBridge, int id);
}