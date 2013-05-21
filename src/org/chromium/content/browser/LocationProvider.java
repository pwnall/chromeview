// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import org.chromium.base.ActivityStatus;
import org.chromium.base.CalledByNative;
import org.chromium.base.ThreadUtils;

import java.util.concurrent.FutureTask;

/**
 * Implements the Java side of LocationProviderAndroid.
 * Delegates all real functionality to the inner class.
 * See detailed documentation on
 * content/browser/geolocation/android_location_api_adapter.h.
 * Based on android.webkit.GeolocationService.java
 */
class LocationProvider {

    // Log tag
    private static final String TAG = "LocationProvider";

    /**
     * This is the core of android location provider. It is a separate class for clarity
     * so that it can manage all processing completely in the UI thread. The container class
     * ensures that the start/stop calls into this class are done in the UI thread.
     */
    private static class LocationProviderImpl
            implements LocationListener, ActivityStatus.StateListener {

        private Context mContext;
        private LocationManager mLocationManager;
        private boolean mIsRunning;
        private boolean mShouldRunAfterActivityResume;
        private boolean mIsGpsEnabled;

        LocationProviderImpl(Context context) {
            mContext = context;
        }

        @Override
        public void onActivityStateChange(int state) {
            if (state == ActivityStatus.PAUSED) {
                mShouldRunAfterActivityResume = mIsRunning;
                unregisterFromLocationUpdates();
            } else if (state == ActivityStatus.RESUMED) {
                assert !mIsRunning;
                if (mShouldRunAfterActivityResume) {
                    registerForLocationUpdates();
                }
            }
        }

        /**
         * Start listening for location updates.
         * @param gpsEnabled Whether or not we're interested in high accuracy GPS.
         */
        private void start(boolean gpsEnabled) {
            if (!mIsRunning && !mShouldRunAfterActivityResume) {
                // Currently idle so start listening to activity status changes.
                ActivityStatus.registerStateListener(this);
            }
            mIsGpsEnabled = gpsEnabled;
            if (ActivityStatus.isPaused()) {
                mShouldRunAfterActivityResume = true;
            } else {
                unregisterFromLocationUpdates();
                registerForLocationUpdates();
            }
        }

        /**
         * Stop listening for location updates.
         */
        private void stop() {
            unregisterFromLocationUpdates();
            ActivityStatus.unregisterStateListener(this);
            mShouldRunAfterActivityResume = false;
        }

        /**
         * Returns true if we are currently listening for location updates, false if not.
         */
        private boolean isRunning() {
            return mIsRunning;
        }

        @Override
        public void onLocationChanged(Location location) {
            // Callbacks from the system location sevice are queued to this thread, so it's
            // possible that we receive callbacks after unregistering. At this point, the
            // native object will no longer exist.
            if (mIsRunning) {
                nativeNewLocationAvailable(location.getLatitude(), location.getLongitude(),
                        location.getTime() / 1000.0,
                        location.hasAltitude(), location.getAltitude(),
                        location.hasAccuracy(), location.getAccuracy(),
                        location.hasBearing(), location.getBearing(),
                        location.hasSpeed(), location.getSpeed());
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        private void ensureLocationManagerCreated() {
            if (mLocationManager != null) return;
            mLocationManager = (LocationManager) mContext.getSystemService(
                    Context.LOCATION_SERVICE);
            if (mLocationManager == null) {
                Log.e(TAG, "Could not get location manager.");
            }
        }

        /**
         * Registers this object with the location service.
         */
        private void registerForLocationUpdates() {
            ensureLocationManagerCreated();

            assert !mIsRunning;
            mIsRunning = true;

            // We're running on the main thread. The C++ side is responsible to
            // bounce notifications to the Geolocation thread as they arrive in the mainLooper.
            try {
                Criteria criteria = new Criteria();
                mLocationManager.requestLocationUpdates(0, 0, criteria, this,
                        Looper.getMainLooper());
                if (mIsGpsEnabled) {
                    criteria.setAccuracy(Criteria.ACCURACY_FINE);
                    mLocationManager.requestLocationUpdates(0, 0, criteria, this,
                            Looper.getMainLooper());
                }
            } catch(SecurityException e) {
                Log.e(TAG, "Caught security exception registering for location updates from " +
                    "system. This should only happen in DumpRenderTree.");
            } catch(IllegalArgumentException e) {
                Log.e(TAG, "Caught IllegalArgumentException registering for location updates.");
            }
        }

        /**
         * Unregisters this object from the location service.
         */
        private void unregisterFromLocationUpdates() {
            if (mIsRunning) {
                mIsRunning = false;
                mLocationManager.removeUpdates(this);
            }
        }
    }

    // Delegate handling the real work in the main thread.
    private LocationProviderImpl mImpl;

    private LocationProvider(Context context) {
        mImpl = new LocationProviderImpl(context);
    }

    @CalledByNative
    static LocationProvider create(Context context) {
        return new LocationProvider(context);
    }

    /**
     * Start listening for location updates until we're told to quit. May be
     * called in any thread.
     * @param gpsEnabled Whether or not we're interested in high accuracy GPS.
     */
    @CalledByNative
    public boolean start(final boolean gpsEnabled) {
        FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                mImpl.start(gpsEnabled);
            }
        }, null);
        ThreadUtils.runOnUiThread(task);
        return true;
    }

    /**
     * Stop listening for location updates. May be called in any thread.
     */
    @CalledByNative
    public void stop() {
        FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                mImpl.stop();
            }
        }, null);
        ThreadUtils.runOnUiThread(task);
    }

    /**
     * Returns true if we are currently listening for location updates, false if not.
     * Must be called only in the UI thread.
     */
    public boolean isRunning() {
        assert Looper.myLooper() == Looper.getMainLooper();
        return mImpl.isRunning();
    }

    // Native functions
    public static native void nativeNewLocationAvailable(
            double latitude, double longitude, double timeStamp,
            boolean hasAltitude, double altitude,
            boolean hasAccuracy, double accuracy,
            boolean hasHeading, double heading,
            boolean hasSpeed, double speed);
    public static native void nativeNewErrorAvailable(String message);
}
