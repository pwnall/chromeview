// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.webkit.WebChromeClient.CustomViewCallback;

import org.chromium.content.browser.ContentVideoViewContextDelegate;

/**
 * This further delegates the responsibility displaying full-screen video to the
 * Webview client.
 */
public class AwContentVideoViewDelegate implements ContentVideoViewContextDelegate {
    private AwContentsClient mAwContentsClient;
    private Context mContext;

    public AwContentVideoViewDelegate(AwContentsClient client, Context context) {
        mAwContentsClient = client;
        mContext = context;
    }

    @Override
    public void onShowCustomView(View view) {
        CustomViewCallback cb = new CustomViewCallback() {
            @Override
            public void onCustomViewHidden() {
                // TODO: we need to invoke ContentVideoView.onDestroyContentVideoView() here.
            }
        };
        mAwContentsClient.onShowCustomView(view, cb);
    }

    @Override
    public void onDestroyContentVideoView() {
        mAwContentsClient.onHideCustomView();
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public View getVideoLoadingProgressView() {
        return mAwContentsClient.getVideoLoadingProgressView();
    }
}
