// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.view.View;
import android.widget.MediaController.MediaPlayerControl;

/**
 * Represents the video controls of the content video view (the fullscreen video player).
 * Abstracts ContentVideoView from android.widget.MediaController to allow embedders to
 * override the behavior and/or the look of the default controls.
 */
public interface ContentVideoViewControls {

    /**
     * The interface that the host of the controls and the video needs to implement.
     */
    public interface Delegate extends MediaPlayerControl {
    }

    /**
     * Show the controls on screen. It will go away
     * automatically after 3 seconds of inactivity.
     */
    public void show();

    /**
     * Show the controls on screen. It will go away
     * automatically after 'timeout' milliseconds of inactivity.
     * @param timeout_ms The timeout in milliseconds. Use 0 to show
     * the controls until hide() is called.
     */
    public void show(int timeout_ms);

    /**
     * Remove the controls from the screen.
     */
    public void hide();

    /**
     * Check if the controls are shown or hidden at the moment.
     */
    public boolean isShowing();

    /**
     * Enable or disable the controls.
     */
    public void setEnabled(boolean enabled);

    /**
     * Set the media player interface to use to update/implement the controls.
     * @param delegate The delegate implementation by the controls host.
     */
    public void setDelegate(Delegate delegate);

    /**
     * Set the view that acts as the anchor for the control view.
     * This can for example be a VideoView, or your Activity's main view.
     * When VideoView calls this method, it will use the VideoView's parent
     * as the anchor.
     * @param view The view to which to anchor the controls when visible.
     */
    public void setAnchorView(View view);
}