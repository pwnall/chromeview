// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.content.browser.ContentVideoViewContextDelegate;

/**
 * Uses an exisiting Activity to handle displaying video in full screen.
 */
public class ActivityContentVideoViewDelegate implements ContentVideoViewContextDelegate {
    private Activity mActivity;

    public ActivityContentVideoViewDelegate(Activity activity)  {
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
    }

    @Override
    public void onDestroyContentVideoView() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public Context getContext() {
        return mActivity;
    }

    @Override
    public View getVideoLoadingProgressView() {
        return null;
    }
}
