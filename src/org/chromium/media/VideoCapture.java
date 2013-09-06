// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Iterator;
import java.util.List;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("media")
public class VideoCapture implements PreviewCallback, OnFrameAvailableListener {
    static class CaptureCapability {
        public int mWidth = 0;
        public int mHeight = 0;
        public int mDesiredFps = 0;
    }

    // Some devices with OS older than JELLY_BEAN don't support YV12 format correctly.
    // Some devices don't support YV12 format correctly even with JELLY_BEAN or newer OS.
    // To work around the issues on those devices, we'd have to request NV21.
    // This is a temporary hack till device manufacturers fix the problem or
    // we don't need to support those devices any more.
    private static class DeviceImageFormatHack {
        private static final String[] sBUGGY_DEVICE_LIST = {
            "SAMSUNG-SGH-I747",
        };

        static int getImageFormat() {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                return ImageFormat.NV21;
            }

            for (String buggyDevice : sBUGGY_DEVICE_LIST) {
                if (buggyDevice.contentEquals(android.os.Build.MODEL)) {
                    return ImageFormat.NV21;
                }
            }

            return ImageFormat.YV12;
        }
    }

    private Camera mCamera;
    public ReentrantLock mPreviewBufferLock = new ReentrantLock();
    private int mImageFormat = ImageFormat.YV12;
    private byte[] mColorPlane = null;
    private Context mContext = null;
    // True when native code has started capture.
    private boolean mIsRunning = false;

    private static final int NUM_CAPTURE_BUFFERS = 3;
    private int mExpectedFrameSize = 0;
    private int mId = 0;
    // Native callback context variable.
    private int mNativeVideoCaptureDeviceAndroid = 0;
    private int[] mGlTextures = null;
    private SurfaceTexture mSurfaceTexture = null;
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private int mCameraOrientation = 0;
    private int mCameraFacing = 0;
    private int mDeviceOrientation = 0;

    CaptureCapability mCurrentCapability = null;
    private static final String TAG = "VideoCapture";

    @CalledByNative
    public static VideoCapture createVideoCapture(
            Context context, int id, int nativeVideoCaptureDeviceAndroid) {
        return new VideoCapture(context, id, nativeVideoCaptureDeviceAndroid);
    }

    public VideoCapture(
            Context context, int id, int nativeVideoCaptureDeviceAndroid) {
        mContext = context;
        mId = id;
        mNativeVideoCaptureDeviceAndroid = nativeVideoCaptureDeviceAndroid;
    }

    // Returns true on success, false otherwise.
    @CalledByNative
    public boolean allocate(int width, int height, int frameRate) {
        Log.d(TAG, "allocate: requested width=" + width +
              ", height=" + height + ", frameRate=" + frameRate);
        try {
            mCamera = Camera.open(mId);
            Camera.CameraInfo camera_info = new Camera.CameraInfo();
            Camera.getCameraInfo(mId, camera_info);
            mCameraOrientation = camera_info.orientation;
            mCameraFacing = camera_info.facing;
            mDeviceOrientation = getDeviceOrientation();
            Log.d(TAG, "allocate: device orientation=" + mDeviceOrientation +
                  ", camera orientation=" + mCameraOrientation +
                  ", facing=" + mCameraFacing);

            Camera.Parameters parameters = mCamera.getParameters();

            // Calculate fps.
            List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
            if (listFpsRange.size() == 0) {
                Log.e(TAG, "allocate: no fps range found");
                return false;
            }
            int frameRateInMs = frameRate * 1000;
            Iterator itFpsRange = listFpsRange.iterator();
            int[] fpsRange = (int[])itFpsRange.next();
            // Use the first range as default.
            int fpsMin = fpsRange[0];
            int fpsMax = fpsRange[1];
            int newFrameRate = (fpsMin + 999) / 1000;
            while (itFpsRange.hasNext()) {
                fpsRange = (int[])itFpsRange.next();
                if (fpsRange[0] <= frameRateInMs &&
                    frameRateInMs <= fpsRange[1]) {
                    fpsMin = fpsRange[0];
                    fpsMax = fpsRange[1];
                    newFrameRate = frameRate;
                    break;
                }
            }
            frameRate = newFrameRate;
            Log.d(TAG, "allocate: fps set to " + frameRate);

            mCurrentCapability = new CaptureCapability();
            mCurrentCapability.mDesiredFps = frameRate;

            // Calculate size.
            List<Camera.Size> listCameraSize =
                    parameters.getSupportedPreviewSizes();
            int minDiff = Integer.MAX_VALUE;
            int matchedWidth = width;
            int matchedHeight = height;
            Iterator itCameraSize = listCameraSize.iterator();
            while (itCameraSize.hasNext()) {
                Camera.Size size = (Camera.Size)itCameraSize.next();
                int diff = Math.abs(size.width - width) +
                           Math.abs(size.height - height);
                Log.d(TAG, "allocate: support resolution (" +
                      size.width + ", " + size.height + "), diff=" + diff);
                // TODO(wjia): Remove this hack (forcing width to be multiple
                // of 32) by supporting stride in video frame buffer.
                // Right now, VideoCaptureController requires compact YV12
                // (i.e., with no padding).
                if (diff < minDiff && (size.width % 32 == 0)) {
                    minDiff = diff;
                    matchedWidth = size.width;
                    matchedHeight = size.height;
                }
            }
            if (minDiff == Integer.MAX_VALUE) {
                Log.e(TAG, "allocate: can not find a resolution whose width " +
                           "is multiple of 32");
                return false;
            }
            mCurrentCapability.mWidth = matchedWidth;
            mCurrentCapability.mHeight = matchedHeight;
            Log.d(TAG, "allocate: matched width=" + matchedWidth +
                  ", height=" + matchedHeight);

            calculateImageFormat(matchedWidth, matchedHeight);

            parameters.setPreviewSize(matchedWidth, matchedHeight);
            parameters.setPreviewFormat(mImageFormat);
            parameters.setPreviewFpsRange(fpsMin, fpsMax);
            mCamera.setParameters(parameters);

            // Set SurfaceTexture.
            mGlTextures = new int[1];
            // Generate one texture pointer and bind it as an external texture.
            GLES20.glGenTextures(1, mGlTextures, 0);
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mGlTextures[0]);
            // No mip-mapping with camera source.
            GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            // Clamp to edge is only option.
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            mSurfaceTexture = new SurfaceTexture(mGlTextures[0]);
            mSurfaceTexture.setOnFrameAvailableListener(null);

            mCamera.setPreviewTexture(mSurfaceTexture);

            int bufSize = matchedWidth * matchedHeight *
                          ImageFormat.getBitsPerPixel(mImageFormat) / 8;
            for (int i = 0; i < NUM_CAPTURE_BUFFERS; i++) {
                byte[] buffer = new byte[bufSize];
                mCamera.addCallbackBuffer(buffer);
            }
            mExpectedFrameSize = bufSize;
        } catch (IOException ex) {
            Log.e(TAG, "allocate: " + ex);
            return false;
        }

        return true;
    }

    @CalledByNative
    public int queryWidth() {
        return mCurrentCapability.mWidth;
    }

    @CalledByNative
    public int queryHeight() {
        return mCurrentCapability.mHeight;
    }

    @CalledByNative
    public int queryFrameRate() {
        return mCurrentCapability.mDesiredFps;
    }

    @CalledByNative
    public int startCapture() {
        if (mCamera == null) {
            Log.e(TAG, "startCapture: camera is null");
            return -1;
        }

        mPreviewBufferLock.lock();
        try {
            if (mIsRunning) {
                return 0;
            }
            mIsRunning = true;
        } finally {
            mPreviewBufferLock.unlock();
        }
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.startPreview();
        return 0;
    }

    @CalledByNative
    public int stopCapture() {
        if (mCamera == null) {
            Log.e(TAG, "stopCapture: camera is null");
            return 0;
        }

        mPreviewBufferLock.lock();
        try {
            if (!mIsRunning) {
                return 0;
            }
            mIsRunning = false;
        } finally {
            mPreviewBufferLock.unlock();
        }

        mCamera.stopPreview();
        mCamera.setPreviewCallbackWithBuffer(null);
        return 0;
    }

    @CalledByNative
    public void deallocate() {
        if (mCamera == null)
            return;

        stopCapture();
        try {
            mCamera.setPreviewTexture(null);
            if (mGlTextures != null)
                GLES20.glDeleteTextures(1, mGlTextures, 0);
            mCurrentCapability = null;
            mCamera.release();
            mCamera = null;
        } catch (IOException ex) {
            Log.e(TAG, "deallocate: failed to deallocate camera, " + ex);
            return;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mPreviewBufferLock.lock();
        try {
            if (!mIsRunning) {
                return;
            }
            if (data.length == mExpectedFrameSize) {
                int rotation = getDeviceOrientation();
                if (rotation != mDeviceOrientation) {
                    mDeviceOrientation = rotation;
                    Log.d(TAG,
                          "onPreviewFrame: device orientation=" +
                          mDeviceOrientation + ", camera orientation=" +
                          mCameraOrientation);
                }
                boolean flipVertical = false;
                boolean flipHorizontal = false;
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    rotation = (mCameraOrientation + rotation) % 360;
                    rotation = (360 - rotation) % 360;
                    flipHorizontal = (rotation == 270 || rotation == 90);
                    flipVertical = flipHorizontal;
                } else {
                    rotation = (mCameraOrientation - rotation + 360) % 360;
                }
                if (mImageFormat == ImageFormat.NV21) {
                    convertNV21ToYV12(data);
                }
                nativeOnFrameAvailable(mNativeVideoCaptureDeviceAndroid,
                        data, mExpectedFrameSize,
                        rotation, flipVertical, flipHorizontal);
            }
        } finally {
            mPreviewBufferLock.unlock();
            if (camera != null) {
                camera.addCallbackBuffer(data);
            }
        }
    }

    // TODO(wjia): investigate whether reading from texture could give better
    // performance and frame rate.
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) { }

    private static class ChromiumCameraInfo {
        private final int mId;
        private final Camera.CameraInfo mCameraInfo;

        private ChromiumCameraInfo(int index) {
            mId = index;
            mCameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, mCameraInfo);
        }

        @CalledByNative("ChromiumCameraInfo")
        private static int getNumberOfCameras() {
            return Camera.getNumberOfCameras();
        }

        @CalledByNative("ChromiumCameraInfo")
        private static ChromiumCameraInfo getAt(int index) {
            return new ChromiumCameraInfo(index);
        }

        @CalledByNative("ChromiumCameraInfo")
        private int getId() {
            return mId;
        }

        @CalledByNative("ChromiumCameraInfo")
        private String getDeviceName() {
            return  "camera " + mId + ", facing " +
                    (mCameraInfo.facing ==
                     Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back");
        }

        @CalledByNative("ChromiumCameraInfo")
        private int getOrientation() {
            return mCameraInfo.orientation;
        }
    }

    private native void nativeOnFrameAvailable(
            int nativeVideoCaptureDeviceAndroid,
            byte[] data,
            int length,
            int rotation,
            boolean flipVertical,
            boolean flipHorizontal);

    private int getDeviceOrientation() {
        int orientation = 0;
        if (mContext != null) {
            WindowManager wm = (WindowManager)mContext.getSystemService(
                    Context.WINDOW_SERVICE);
            switch(wm.getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90:
                    orientation = 90;
                    break;
                case Surface.ROTATION_180:
                    orientation = 180;
                    break;
                case Surface.ROTATION_270:
                    orientation = 270;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = 0;
                    break;
            }
        }
        return orientation;
    }

    private void calculateImageFormat(int width, int height) {
        mImageFormat = DeviceImageFormatHack.getImageFormat();
        if (mImageFormat == ImageFormat.NV21) {
            mColorPlane = new byte[width * height / 4];
        }
    }

    private void convertNV21ToYV12(byte[] data) {
        final int ySize = mCurrentCapability.mWidth * mCurrentCapability.mHeight;
        final int uvSize = ySize / 4;
        for (int i = 0; i < uvSize; i++) {
            final int index = ySize + i * 2;
            data[ySize + i] = data[index];
            mColorPlane[i] = data[index + 1];
        }
        System.arraycopy(mColorPlane, 0, data, ySize + uvSize, uvSize);
    }
}
