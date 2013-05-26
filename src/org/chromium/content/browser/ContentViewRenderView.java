// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import org.chromium.base.JNINamespace;

/***
 * This view is used by a ContentView to render its content.
 * Call {@link #setCurrentContentView(ContentView)} with the contentView that should be displayed.
 * Note that only one ContentView can be shown at a time.
 */
@JNINamespace("content")
public class ContentViewRenderView extends FrameLayout {

    // The native side of this object.
    private int mNativeContentViewRenderView = 0;

    private SurfaceView mSurfaceView;
    private VSyncAdapter mVSyncAdapter;

    private ContentView mCurrentContentView;

    /**
     * Constructs a new ContentViewRenderView that should be can to a view hierarchy.
     * Native code should add/remove the layers to be rendered through the ContentViewLayerRenderer.
     * @param context The context used to create this.
     */
    public ContentViewRenderView(Context context) {
        super(context);

        mNativeContentViewRenderView = nativeInit();
        assert mNativeContentViewRenderView != 0;

        mSurfaceView = createSurfaceView(getContext());
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                nativeSurfaceSetSize(mNativeContentViewRenderView, width, height);
                if (mCurrentContentView != null) {
                    mCurrentContentView.getContentViewCore().onPhysicalBackingSizeChanged(
                            width, height);
                }
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                nativeSurfaceCreated(mNativeContentViewRenderView, holder.getSurface());
                onReadyToRender();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                nativeSurfaceDestroyed(mNativeContentViewRenderView);
            }
        });

        mVSyncAdapter = new VSyncAdapter(getContext());
        addView(mSurfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private static class VSyncAdapter implements VSyncManager.Provider, VSyncMonitor.Listener {
        private final VSyncMonitor mVSyncMonitor;
        private boolean mVSyncNotificationEnabled;
        private VSyncManager.Listener mVSyncListener;

        // The VSyncMonitor gives the timebase for the actual vsync, but we don't want render until
        // we have had a chance for input events to propagate to the compositor thread. This takes
        // 3 ms typically, so we adjust the vsync timestamps forward by a bit to give input events a
        // chance to arrive.
        private static final long INPUT_EVENT_LAG_FROM_VSYNC_MICROSECONDS = 3200;

        VSyncAdapter(Context context) {
            mVSyncMonitor = new VSyncMonitor(context, this);
        }

        @Override
        public void onVSync(VSyncMonitor monitor, long vsyncTimeMicros) {
            if (mVSyncListener == null) return;
            if (mVSyncNotificationEnabled) {
                mVSyncListener.onVSync(vsyncTimeMicros);
                mVSyncMonitor.requestUpdate();
            } else {
                // Compensate for input event lag. Input events are delivered immediately on
                // pre-JB releases, so this adjustment is only done for later versions.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    vsyncTimeMicros += INPUT_EVENT_LAG_FROM_VSYNC_MICROSECONDS;
                }
                mVSyncListener.updateVSync(vsyncTimeMicros,
                        mVSyncMonitor.getVSyncPeriodInMicroseconds());
            }
        }

        @Override
        public void registerVSyncListener(VSyncManager.Listener listener) {
            if (!mVSyncNotificationEnabled) mVSyncMonitor.requestUpdate();
            mVSyncNotificationEnabled = true;
        }

        @Override
        public void unregisterVSyncListener(VSyncManager.Listener listener) {
            mVSyncNotificationEnabled = false;
        }

        void setVSyncListener(VSyncManager.Listener listener) {
            mVSyncListener = listener;
            if (mVSyncListener != null) mVSyncMonitor.requestUpdate();
        }
    }

    /**
     * Should be called when the ContentViewRenderView is not needed anymore so its associated
     * native resource can be freed.
     */
    public void destroy() {
        nativeDestroy(mNativeContentViewRenderView);
    }

    /**
     * Makes the passed ContentView the one displayed by this ContentViewRenderView.
     */
    public void setCurrentContentView(ContentView contentView) {
        ContentViewCore contentViewCore = contentView.getContentViewCore();
        nativeSetCurrentContentView(mNativeContentViewRenderView,
                contentViewCore.getNativeContentViewCore());

        mCurrentContentView = contentView;
        contentViewCore.onPhysicalBackingSizeChanged(getWidth(), getHeight());
        mVSyncAdapter.setVSyncListener(contentViewCore.getVSyncListener(mVSyncAdapter));
    }

    /**
     * This method should be subclassed to provide actions to be performed once the view is ready to
     * render.
     */
    protected void onReadyToRender() {
    }

    /**
     * This method could be subclassed optionally to provide a custom SurfaceView object to
     * this ContentViewRenderView.
     * @param context The context used to create the SurfaceView object.
     * @return The created SurfaceView object.
     */
    protected SurfaceView createSurfaceView(Context context) {
        return new SurfaceView(context);
    }

    /**
     * @return whether the surface view is initialized and ready to render.
     */
    public boolean isInitialized() {
        return mSurfaceView.getHolder().getSurface() != null;
    }

    private static native int nativeInit();
    private native void nativeDestroy(int nativeContentViewRenderView);
    private native void nativeSetCurrentContentView(int nativeContentViewRenderView,
            int nativeContentView);
    private native void nativeSurfaceCreated(int nativeContentViewRenderView, Surface surface);
    private native void nativeSurfaceDestroyed(int nativeContentViewRenderView);
    private native void nativeSurfaceSetSize(int nativeContentViewRenderView,
            int width, int height);
}
