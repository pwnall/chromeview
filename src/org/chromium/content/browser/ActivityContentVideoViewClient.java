// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.content.browser.ContentVideoViewClient;

/**
 * Uses an existing Activity to handle displaying video in full screen.
 */
public class ActivityContentVideoViewClient implements ContentVideoViewClient {
    private Activity mActivity;
    private View mView;

    public ActivityContentVideoViewClient(Activity activity)  {
        this.mActivity = activity;
    }

    @Override
    public void onShowCustomView(View view) {
        mActivity.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mActivity.getWindow().addContentView(view,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER));
        mView = view;
    }

    @Override
    public void onDestroyContentVideoView() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mView);
        mView = null;
    }

    @Override
    public View getVideoLoadingProgressView() {
        return null;
    }

    @Override
    public ContentVideoViewControls createControls() {
        return null;
    }
}
