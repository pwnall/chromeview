// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ZoomButtonsController;
import org.chromium.content.browser.ContentViewCore.ZoomControlsDelegate;

class AwZoomControls implements ZoomControlsDelegate {

    private AwContents mAwContents;
    // It is advised to use getZoomController() where possible.
    private ZoomButtonsController mZoomButtonsController;

    AwZoomControls(AwContents awContents) {
        mAwContents = awContents;
    }

    @Override
    public void invokeZoomPicker() {
        ZoomButtonsController zoomController = getZoomController();
        if (zoomController != null) {
            zoomController.setVisible(true);
        }
    }

    @Override
    public void dismissZoomPicker() {
        ZoomButtonsController zoomController = getZoomController();
        if (zoomController != null) {
            zoomController.setVisible(false);
        }
    }

    @Override
    public void updateZoomControls() {
        ZoomButtonsController zoomController = getZoomController();
        if (zoomController == null) {
            return;
        }
        boolean canZoomIn = mAwContents.canZoomIn();
        boolean canZoomOut = mAwContents.canZoomOut();
        if (!canZoomIn && !canZoomOut) {
            // Hide the zoom in and out buttons if the page cannot zoom
            zoomController.getZoomControls().setVisibility(View.GONE);
        } else {
            // Set each one individually, as a page may be able to zoom in or out
            zoomController.setZoomInEnabled(canZoomIn);
            zoomController.setZoomOutEnabled(canZoomOut);
        }
    }

    // This method is used in tests. It doesn't modify the state of zoom controls.
    View getZoomControlsViewForTest() {
        return mZoomButtonsController != null ? mZoomButtonsController.getZoomControls() : null;
    }

    private ZoomButtonsController getZoomController() {
        if (mZoomButtonsController == null &&
            mAwContents.getSettings().shouldDisplayZoomControls()) {
            mZoomButtonsController = new ZoomButtonsController(
                    mAwContents.getContentViewCore().getContainerView());
            mZoomButtonsController.setOnZoomListener(new ZoomListener());
            // ZoomButtonsController positions the buttons at the bottom, but in
            // the middle. Change their layout parameters so they appear on the
            // right.
            View controls = mZoomButtonsController.getZoomControls();
            ViewGroup.LayoutParams params = controls.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = Gravity.RIGHT;
            }
        }
        return mZoomButtonsController;
    }

    private class ZoomListener implements ZoomButtonsController.OnZoomListener {
        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                // Bring back the hidden zoom controls.
                mZoomButtonsController.getZoomControls().setVisibility(View.VISIBLE);
                updateZoomControls();
            }
        }

        @Override
        public void onZoom(boolean zoomIn) {
            if (zoomIn) {
                mAwContents.zoomIn();
            } else {
                mAwContents.zoomOut();
            }
            // ContentView will call updateZoomControls after its current page scale
            // is got updated from the native code.
        }
    }
}
