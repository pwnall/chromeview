// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.Map;

/**
 * Holds parameters for ContentViewCore.LoadUrl. Parameters should match
 * counterparts in NavigationController::LoadURLParams, including default
 * values.
 */
@JNINamespace("content")
public class LoadUrlParams {
    // Should match NavigationController::LoadUrlType exactly. See comments
    // there for proper usage. Values are initialized in initializeConstants.
    public static int LOAD_TYPE_DEFAULT;
    public static int LOAD_TYPE_BROWSER_INITIATED_HTTP_POST;
    public static int LOAD_TYPE_DATA;

    // Should match NavigationController::UserAgentOverrideOption exactly.
    // See comments there for proper usage. Values are initialized in
    // initializeConstants.
    public static int UA_OVERRIDE_INHERIT;
    public static int UA_OVERRIDE_FALSE;
    public static int UA_OVERRIDE_TRUE;

    // Fields with counterparts in NavigationController::LoadURLParams.
    // Package private so that ContentViewCore.loadUrl can pass them down to
    // native code. Should not be accessed directly anywhere else outside of
    // this class.
    final String mUrl;
    int mLoadUrlType;
    int mTransitionType;
    int mUaOverrideOption;
    private Map<String, String> mExtraHeaders;
    byte[] mPostData;
    String mBaseUrlForDataUrl;
    String mVirtualUrlForDataUrl;
    boolean mCanLoadLocalResources;

    public LoadUrlParams(String url) {
        // Check initializeConstants was called.
        assert LOAD_TYPE_DEFAULT != LOAD_TYPE_BROWSER_INITIATED_HTTP_POST;

        mUrl = url;
        mLoadUrlType = LOAD_TYPE_DEFAULT;
        mTransitionType = PageTransitionTypes.PAGE_TRANSITION_LINK;
        mUaOverrideOption = UA_OVERRIDE_INHERIT;
        mPostData = null;
        mBaseUrlForDataUrl = null;
        mVirtualUrlForDataUrl = null;
    }

    /**
     * Helper method to create a LoadUrlParams object for data url.
     * @param data Data to be loaded.
     * @param mimeType Mime type of the data.
     * @param isBase64Encoded True if the data is encoded in Base 64 format.
     */
    public static LoadUrlParams createLoadDataParams(
        String data, String mimeType, boolean isBase64Encoded) {
        return createLoadDataParams(data, mimeType, isBase64Encoded, null);
    }

    /**
     * Helper method to create a LoadUrlParams object for data url.
     * @param data Data to be loaded.
     * @param mimeType Mime type of the data.
     * @param isBase64Encoded True if the data is encoded in Base 64 format.
     * @param charset The character set for the data. Pass null if the mime type
     *                does not require a special charset.
     */
    public static LoadUrlParams createLoadDataParams(
            String data, String mimeType, boolean isBase64Encoded, String charset) {
        StringBuilder dataUrl = new StringBuilder("data:");
        dataUrl.append(mimeType);
        if (charset != null && !charset.isEmpty()) {
            dataUrl.append(";charset=" + charset);
        }
        if (isBase64Encoded) {
            dataUrl.append(";base64");
        }
        dataUrl.append(",");
        dataUrl.append(data);

        LoadUrlParams params = new LoadUrlParams(dataUrl.toString());
        params.setLoadType(LoadUrlParams.LOAD_TYPE_DATA);
        params.setTransitionType(PageTransitionTypes.PAGE_TRANSITION_TYPED);
        return params;
    }

    /**
     * Helper method to create a LoadUrlParams object for data url with base
     * and virtual url.
     * @param data Data to be loaded.
     * @param mimeType Mime type of the data.
     * @param isBase64Encoded True if the data is encoded in Base 64 format.
     * @param baseUrl Base url of this data load. Note that for WebView compatibility,
     *                baseUrl and historyUrl are ignored if this is a data: url.
     *                Defaults to about:blank if null.
     * @param historyUrl History url for this data load. Note that for WebView compatibility,
     *                   this is ignored if baseUrl is a data: url. Defaults to about:blank
     *                   if null.
     */
    public static LoadUrlParams createLoadDataParamsWithBaseUrl(
            String data, String mimeType, boolean isBase64Encoded,
            String baseUrl, String historyUrl) {
        return createLoadDataParamsWithBaseUrl(data, mimeType, isBase64Encoded,
                baseUrl, historyUrl, null);
    }

    /**
     * Helper method to create a LoadUrlParams object for data url with base
     * and virtual url.
     * @param data Data to be loaded.
     * @param mimeType Mime type of the data.
     * @param isBase64Encoded True if the data is encoded in Base 64 format.
     * @param baseUrl Base url of this data load. Note that for WebView compatibility,
     *                baseUrl and historyUrl are ignored if this is a data: url.
     *                Defaults to about:blank if null.
     * @param historyUrl History url for this data load. Note that for WebView compatibility,
     *                   this is ignored if baseUrl is a data: url. Defaults to about:blank
     *                   if null.
     * @param charset The character set for the data. Pass null if the mime type
     *                does not require a special charset.
     */
    public static LoadUrlParams createLoadDataParamsWithBaseUrl(
            String data, String mimeType, boolean isBase64Encoded,
            String baseUrl, String historyUrl, String charset) {
        LoadUrlParams params = createLoadDataParams(data, mimeType, isBase64Encoded, charset);
        // For WebView compatibility, when the base URL has the 'data:'
        // scheme, we treat it as a regular data URL load and skip setting
        // baseUrl and historyUrl.
        // TODO(joth): we should just append baseURL and historyURL here, and move the
        // WebView specific transform up to a wrapper factory function in android_webview/.
        if (baseUrl == null || !baseUrl.toLowerCase().startsWith("data:")) {
            params.setBaseUrlForDataUrl(baseUrl != null ? baseUrl : "about:blank");
            params.setVirtualUrlForDataUrl(historyUrl != null ? historyUrl : "about:blank");
        }
        return params;
    }

    /**
     * Helper method to create a LoadUrlParams object for an HTTP POST load.
     * @param url URL of the load.
     * @param postData Post data of the load. Can be null.
     */
    public static LoadUrlParams createLoadHttpPostParams(
            String url, byte[] postData) {
        LoadUrlParams params = new LoadUrlParams(url);
        params.setLoadType(LOAD_TYPE_BROWSER_INITIATED_HTTP_POST);
        params.setTransitionType(PageTransitionTypes.PAGE_TRANSITION_TYPED);
        params.setPostData(postData);
        return params;
    }

    /**
     * Return the url.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Set load type of this load. Defaults to LOAD_TYPE_DEFAULT.
     * @param loadType One of LOAD_TYPE static constants above.
     */
    public void setLoadType(int loadType) {
        mLoadUrlType = loadType;
    }

    /**
     * Set transition type of this load. Defaults to PAGE_TRANSITION_LINK.
     * @param transitionType One of PAGE_TRANSITION static constants in ContentView.
     */
    public void setTransitionType(int transitionType) {
        mTransitionType = transitionType;
    }

    /**
     * Return the transition type.
     */
    public int getTransitionType() {
        return mTransitionType;
    }

    /**
     * Set user agent override option of this load. Defaults to UA_OVERRIDE_INHERIT.
     * @param uaOption One of UA_OVERRIDE static constants above.
     */
    public void setOverrideUserAgent(int uaOption) {
        mUaOverrideOption = uaOption;
    }

    /**
     * Set extra headers for this load.
     * @param extraHeaders Extra HTTP headers for this load. Note that these
     *                     headers will never overwrite existing ones set by Chromium.
     */
    public void setExtraHeaders(Map<String, String> extraHeaders) {
        mExtraHeaders = extraHeaders;
    }

    /**
     * Return the extra headers as a single String separated by "\n", or null if no extra header
     * is set. This form is suitable for passing to native
     * NavigationController::LoadUrlParams::extra_headers.
     */
    String getExtraHeadersString() {
        if (mExtraHeaders == null) return null;

        StringBuilder headerBuilder = new StringBuilder();
        for (Map.Entry<String, String> header : mExtraHeaders.entrySet()) {
            if (headerBuilder.length() > 0) headerBuilder.append("\n");

            // Header name should be lower case.
            headerBuilder.append(header.getKey().toLowerCase());
            headerBuilder.append(":");
            headerBuilder.append(header.getValue());
        }

        return headerBuilder.toString();
    }

    /**
     * Set the post data of this load. This field is ignored unless load type is
     * LOAD_TYPE_BROWSER_INITIATED_HTTP_POST.
     * @param postData Post data for this http post load.
     */
    public void setPostData(byte[] postData) {
        mPostData = postData;
    }

    /**
     * Set the base url for data load. It is used both to resolve relative URLs
     * and when applying JavaScript's same origin policy. It is ignored unless
     * load type is LOAD_TYPE_DATA.
     * @param baseUrl The base url for this data load.
     */
    public void setBaseUrlForDataUrl(String baseUrl) {
        mBaseUrlForDataUrl = baseUrl;
    }

    /**
     * Set the virtual url for data load. It is the url displayed to the user.
     * It is ignored unless load type is LOAD_TYPE_DATA.
     * @param virtualUrl The virtual url for this data load.
     */
    public void setVirtualUrlForDataUrl(String virtualUrl) {
        mVirtualUrlForDataUrl = virtualUrl;
    }

    /**
     * Set whether the load should be able to access local resources. This
     * defaults to false.
     */
    public void setCanLoadLocalResources(boolean canLoad) {
        mCanLoadLocalResources = canLoad;
    }

    public int getLoadUrlType() {
        return mLoadUrlType;
    }

    public boolean isBaseUrlDataScheme() {
        // If there's no base url set, but this is a data load then
        // treat the scheme as data:.
        if (mBaseUrlForDataUrl == null && mLoadUrlType == LOAD_TYPE_DATA) {
            return true;
        }
        return nativeIsDataScheme(mBaseUrlForDataUrl);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private static void initializeConstants(
            int load_type_default,
            int load_type_browser_initiated_http_post,
            int load_type_data,
            int ua_override_inherit,
            int ua_override_false,
            int ua_override_true) {
        LOAD_TYPE_DEFAULT = load_type_default;
        LOAD_TYPE_BROWSER_INITIATED_HTTP_POST = load_type_browser_initiated_http_post;
        LOAD_TYPE_DATA = load_type_data;
        UA_OVERRIDE_INHERIT = ua_override_inherit;
        UA_OVERRIDE_FALSE = ua_override_false;
        UA_OVERRIDE_TRUE = ua_override_true;
    }

    /**
     * Parses |url| as a GURL on the native side, and
     * returns true if it's scheme is data:.
     */
    private static native boolean nativeIsDataScheme(String url);
}
