// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.WeakContext;

import java.util.List;
import java.util.Set;

/**
 * Android implementation of the device motion and orientation APIs.
 */
@JNINamespace("content")
class DeviceMotionAndOrientation implements SensorEventListener {

    private static final String TAG = "DeviceMotionAndOrientation";

    // These fields are lazily initialized by getHandler().
    private Thread mThread;
    private Handler mHandler;

    // The lock to access the mHandler.
    private Object mHandlerLock = new Object();

    // Non-zero if and only if we're listening for events.
    // To avoid race conditions on the C++ side, access must be synchronized.
    private int mNativePtr;

    // The lock to access the mNativePtr.
    private Object mNativePtrLock = new Object();

    // The acceleration vector including gravity expressed in the body frame.
    private float[] mAccelerationVector;

    // The geomagnetic vector expressed in the body frame.
    private float[] mMagneticFieldVector;

    // Lazily initialized when registering for notifications.
    private SensorManagerProxy mSensorManagerProxy;

    // The only instance of that class and its associated lock.
    private static DeviceMotionAndOrientation sSingleton;
    private static Object sSingletonLock = new Object();

    /**
     * constants for using in JNI calls, also see
     * content/browser/device_orientation/data_fetcher_impl_android.cc
     */
    static final int DEVICE_ORIENTATION = 0;
    static final int DEVICE_MOTION = 1;

    static final ImmutableSet<Integer> DEVICE_ORIENTATION_SENSORS = ImmutableSet.of(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_MAGNETIC_FIELD);

    static final ImmutableSet<Integer> DEVICE_MOTION_SENSORS = ImmutableSet.of(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GYROSCOPE);

    @VisibleForTesting
    final Set<Integer> mActiveSensors = Sets.newHashSet();
    boolean mDeviceMotionIsActive = false;
    boolean mDeviceOrientationIsActive = false;

    protected DeviceMotionAndOrientation() {
    }

    /**
     * Start listening for sensor events. If this object is already listening
     * for events, the old callback is unregistered first.
     *
     * @param nativePtr Value to pass to nativeGotOrientation() for each event.
     * @param rateInMilliseconds Requested callback rate in milliseconds. The
     *            actual rate may be higher. Unwanted events should be ignored.
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION or
     *                  DEVICE_MOTION.
     * @return True on success.
     */
    @CalledByNative
    public boolean start(int nativePtr, int eventType, int rateInMilliseconds) {
        boolean success = false;
        synchronized (mNativePtrLock) {
            switch (eventType) {
                case DEVICE_ORIENTATION:
                    success = registerSensors(DEVICE_ORIENTATION_SENSORS, rateInMilliseconds,
                            true);
                    break;
                case DEVICE_MOTION:
                    // note: device motion spec does not require all sensors to be available
                    success = registerSensors(DEVICE_MOTION_SENSORS, rateInMilliseconds, false);
                    break;
                default:
                    Log.e(TAG, "Unknown event type: " + eventType);
                    return false;
            }
            if (success) {
                mNativePtr = nativePtr;
                setEventTypeActive(eventType, true);
            }
            return success;
        }
    }

    /**
     * Stop listening to sensors for a given event type. Ensures that sensors are not disabled
     * if they are still in use by a different event type.
     *
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION or
     *                  DEVICE_MOTION.
     * We strictly guarantee that the corresponding native*() methods will not be called
     * after this method returns.
     */
    @CalledByNative
    public void stop(int eventType) {
        Set<Integer> sensorsToRemainActive = Sets.newHashSet();
        synchronized (mNativePtrLock) {
            switch (eventType) {
                case DEVICE_ORIENTATION:
                    if (mDeviceMotionIsActive) {
                        sensorsToRemainActive.addAll(DEVICE_MOTION_SENSORS);
                    }
                    break;
                case DEVICE_MOTION:
                    if (mDeviceOrientationIsActive) {
                        sensorsToRemainActive.addAll(DEVICE_ORIENTATION_SENSORS);
                    }
                    break;
                default:
                    Log.e(TAG, "Unknown event type: " + eventType);
                    return;
            }

            Set<Integer> sensorsToDeactivate = Sets.newHashSet(mActiveSensors);
            sensorsToDeactivate.removeAll(sensorsToRemainActive);
            unregisterSensors(sensorsToDeactivate);
            setEventTypeActive(eventType, false);
            if (mActiveSensors.isEmpty()) {
                mNativePtr = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorChanged(event.sensor.getType(), event.values);
    }

    @VisibleForTesting
    void sensorChanged(int type, float[] values) {

        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                if (mAccelerationVector == null) {
                    mAccelerationVector = new float[3];
                }
                System.arraycopy(values, 0, mAccelerationVector, 0,
                        mAccelerationVector.length);
                if (mDeviceMotionIsActive) {
                    gotAccelerationIncludingGravity(mAccelerationVector[0], mAccelerationVector[1],
                        mAccelerationVector[2]);
                }
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                if (mDeviceMotionIsActive) {
                    gotAcceleration(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                if (mDeviceMotionIsActive) {
                    gotRotationRate(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (mMagneticFieldVector == null) {
                    mMagneticFieldVector = new float[3];
                }
                System.arraycopy(values, 0, mMagneticFieldVector, 0,
                        mMagneticFieldVector.length);
                break;
            default:
                // Unexpected
                return;
        }

        if (mDeviceOrientationIsActive) {
            getOrientationUsingGetRotationMatrix();
        }
    }

    private void getOrientationUsingGetRotationMatrix() {
        if (mAccelerationVector == null || mMagneticFieldVector == null) {
            return;
        }

        // Get the rotation matrix.
        // The rotation matrix that transforms from the body frame to the earth
        // frame.
        float[] deviceRotationMatrix = new float[9];
        if (!SensorManager.getRotationMatrix(deviceRotationMatrix, null, mAccelerationVector,
                mMagneticFieldVector)) {
            return;
        }

        // Convert rotation matrix to rotation angles.
        // Assuming that the rotations are appied in the order listed at
        // http://developer.android.com/reference/android/hardware/SensorEvent.html#values
        // the rotations are applied about the same axes and in the same order as required by the
        // API. The only conversions are sign changes as follows.  The angles are in radians

        float[] rotationAngles = new float[3];
        SensorManager.getOrientation(deviceRotationMatrix, rotationAngles);

        double alpha = Math.toDegrees(-rotationAngles[0]);
        while (alpha < 0.0) {
            alpha += 360.0; // [0, 360)
        }

        double beta = Math.toDegrees(-rotationAngles[1]);
        while (beta < -180.0) {
            beta += 360.0; // [-180, 180)
        }

        double gamma = Math.toDegrees(rotationAngles[2]);
        while (gamma < -90.0) {
            gamma += 360.0; // [-90, 90)
        }

        gotOrientation(alpha, beta, gamma);
    }

    private SensorManagerProxy getSensorManagerProxy() {
        if (mSensorManagerProxy != null) {
            return mSensorManagerProxy;
        }
        SensorManager sensorManager = (SensorManager)WeakContext.getSystemService(
                Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            mSensorManagerProxy = new SensorManagerProxyImpl(sensorManager);
        }
        return mSensorManagerProxy;
    }

    @VisibleForTesting
    void setSensorManagerProxy(SensorManagerProxy sensorManagerProxy) {
        mSensorManagerProxy = sensorManagerProxy;
    }

    private void setEventTypeActive(int eventType, boolean value) {
        switch (eventType) {
            case DEVICE_ORIENTATION:
                mDeviceOrientationIsActive = value;
                return;
            case DEVICE_MOTION:
                mDeviceMotionIsActive = value;
                return;
        }
    }

    /**
     * @param sensorTypes List of sensors to activate.
     * @param rateInMilliseconds Intended delay (in milliseconds) between sensor readings.
     * @param failOnMissingSensor If true the method returns true only if all sensors could be
     *                            activated. When false the method return true if at least one
     *                            sensor in sensorTypes could be activated.
     */
    private boolean registerSensors(Iterable<Integer> sensorTypes, int rateInMilliseconds,
            boolean failOnMissingSensor) {
        Set<Integer> sensorsToActivate = Sets.newHashSet(sensorTypes);
        sensorsToActivate.removeAll(mActiveSensors);
        boolean success = false;

        for (Integer sensorType : sensorsToActivate) {
            boolean result = registerForSensorType(sensorType, rateInMilliseconds);
            if (!result && failOnMissingSensor) {
                // restore the previous state upon failure
                unregisterSensors(sensorsToActivate);
                return false;
            }
            if (result) {
                mActiveSensors.add(sensorType);
                success = true;
            }
        }
        return success;
    }

    private void unregisterSensors(Iterable<Integer> sensorTypes) {
        for (Integer sensorType : sensorTypes) {
            if (mActiveSensors.contains(sensorType)) {
                getSensorManagerProxy().unregisterListener(this, sensorType);
                mActiveSensors.remove(sensorType);
            }
        }
    }

    private boolean registerForSensorType(int type, int rateInMilliseconds) {
        SensorManagerProxy sensorManager = getSensorManagerProxy();
        if (sensorManager == null) {
            return false;
        }
        final int rateInMicroseconds = 1000 * rateInMilliseconds;
        return sensorManager.registerListener(this, type, rateInMicroseconds, getHandler());
    }

    protected void gotOrientation(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
              nativeGotOrientation(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    protected void gotAcceleration(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
              nativeGotAcceleration(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotAccelerationIncludingGravity(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
              nativeGotAccelerationIncludingGravity(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotRotationRate(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
              nativeGotRotationRate(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    private Handler getHandler() {
        // TODO(timvolodine): Remove the mHandlerLock when sure that getHandler is not called
        // from multiple threads. This will be the case when device motion and device orientation
        // use the same polling thread (also see crbug/234282).
        synchronized (mHandlerLock) {
            if (mHandler == null) {
                HandlerThread thread = new HandlerThread("DeviceMotionAndOrientation");
                thread.start();
                mHandler = new Handler(thread.getLooper());  // blocks on thread start
            }
            return mHandler;
        }
    }

    @CalledByNative
    static DeviceMotionAndOrientation getInstance() {
        synchronized (sSingletonLock) {
            if (sSingleton == null) {
                sSingleton = new DeviceMotionAndOrientation();
            }
            return sSingleton;
        }
    }

    /**
     * Native JNI calls,
     * see content/browser/device_orientation/data_fetcher_impl_android.cc
     */

    /**
     * Orientation of the device with respect to its reference frame.
     */
    private native void nativeGotOrientation(
            int nativeDataFetcherImplAndroid,
            double alpha, double beta, double gamma);

    /**
     * Linear acceleration without gravity of the device with respect to its body frame.
     */
    private native void nativeGotAcceleration(
            int nativeDataFetcherImplAndroid,
            double x, double y, double z);

    /**
     * Acceleration including gravity of the device with respect to its body frame.
     */
    private native void nativeGotAccelerationIncludingGravity(
            int nativeDataFetcherImplAndroid,
            double x, double y, double z);

    /**
     * Rotation rate of the device with respect to its body frame.
     */
    private native void nativeGotRotationRate(
            int nativeDataFetcherImplAndroid,
            double alpha, double beta, double gamma);

    /**
     * Need the an interface for SensorManager for testing.
     */
    interface SensorManagerProxy {
        public boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                Handler handler);
        public void unregisterListener(SensorEventListener listener, int sensorType);
    }

    static class SensorManagerProxyImpl implements SensorManagerProxy {
        private final SensorManager mSensorManager;

        SensorManagerProxyImpl(SensorManager sensorManager) {
            mSensorManager = sensorManager;
        }

        public boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                Handler handler) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (sensors.isEmpty()) {
                return false;
            }
            return mSensorManager.registerListener(listener, sensors.get(0), rate, handler);
        }

        public void unregisterListener(SensorEventListener listener, int sensorType) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (!sensors.isEmpty()) {
                mSensorManager.unregisterListener(listener, sensors.get(0));
            }
        }
    }

}