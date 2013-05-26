// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.view.ViewTreeObserver;

/**
 * A CursorController instance can be used to control a cursor in the text.
 */
interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {

    /**
     * Hide the cursor controller from screen.
     */
    void hide();

    /**
     * @return true if the CursorController is currently visible
     */
    boolean isShowing();

    /**
     * Called when the handle is about to start updating its position.
     * @param handle
     */
    void beforeStartUpdatingPosition(HandleView handle);

    /**
     * Update the controller's position.
     */
    void updatePosition(HandleView handle, int x, int y);

    /**
     * Called when the view is detached from window. Perform house keeping task, such as
     * stopping Runnable thread that would otherwise keep a reference on the context, thus
     * preventing the activity to be recycled.
     */
    void onDetached();
}
