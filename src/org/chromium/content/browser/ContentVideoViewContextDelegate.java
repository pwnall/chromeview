// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.view.View;

/**
 * Allows customization for clients of the ContentVideoView.
 * The implementer is responsible for displaying the Android view when
 * {@link #onShowCustomView(View)} is called.
 */
public interface ContentVideoViewContextDelegate {
    public void onShowCustomView(View view);
    public void onDestroyContentVideoView();
    public Context getContext();
    public View getVideoLoadingProgressView();
}
