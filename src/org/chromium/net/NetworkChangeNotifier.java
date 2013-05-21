// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.content.Context;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.NativeClassQualifiedName;
import org.chromium.base.ObserverList;

import java.util.ArrayList;

/**
 * Triggers updates to the underlying network state in Chrome.
 *
 * By default, connectivity is assumed and changes must pushed from the embedder via the
 * forceConnectivityState function.
 * Embedders may choose to have this class auto-detect changes in network connectivity by invoking
 * the setAutoDetectConnectivityState function.
 *
 * WARNING: This class is not thread-safe.
 */
@JNINamespace("net")
public class NetworkChangeNotifier {
    /**
     * Alerted when the connection type of the network changes.
     * The alert is fired on the UI thread.
     */
    public interface ConnectionTypeObserver {
        public void onConnectionTypeChanged(int connectionType);
    }

    // These constants must always match the ones in network_change_notifier.h.
    public static final int CONNECTION_UNKNOWN = 0;
    public static final int CONNECTION_ETHERNET = 1;
    public static final int CONNECTION_WIFI = 2;
    public static final int CONNECTION_2G = 3;
    public static final int CONNECTION_3G = 4;
    public static final int CONNECTION_4G = 5;
    public static final int CONNECTION_NONE = 6;

    private final Context mContext;
    private final ArrayList<Integer> mNativeChangeNotifiers;
    private final ObserverList<ConnectionTypeObserver> mConnectionTypeObservers;
    private NetworkChangeNotifierAutoDetect mAutoDetector;
    private int mCurrentConnectionType = CONNECTION_UNKNOWN;

    private static NetworkChangeNotifier sInstance;

    private NetworkChangeNotifier(Context context) {
        mContext = context;
        mNativeChangeNotifiers = new ArrayList<Integer>();
        mConnectionTypeObservers = new ObserverList<ConnectionTypeObserver>();
    }

    /**
     * Initializes the singleton once.
     */
    @CalledByNative
    public static NetworkChangeNotifier init(Context context) {
        if (sInstance == null) {
            sInstance = new NetworkChangeNotifier(context);
        }
        return sInstance;
    }

    public static boolean isInitialized() {
        return sInstance != null;
    }

    static void resetInstanceForTests(Context context) {
        sInstance = new NetworkChangeNotifier(context);
    }

    @CalledByNative
    public int getCurrentConnectionType() {
        return mCurrentConnectionType;
    }

    /**
     * Adds a native-side observer.
     */
    @CalledByNative
    public void addNativeObserver(int nativeChangeNotifier) {
        mNativeChangeNotifiers.add(nativeChangeNotifier);
    }

    /**
     * Removes a native-side observer.
     */
    @CalledByNative
    public void removeNativeObserver(int nativeChangeNotifier) {
        // Please keep the cast performing the boxing below. It ensures that the right method
        // overload is used. ArrayList<T> has both remove(int index) and remove(T element).
        mNativeChangeNotifiers.remove((Integer) nativeChangeNotifier);
    }

    /**
     * Returns the singleton instance.
     */
    public static NetworkChangeNotifier getInstance() {
        assert sInstance != null;
        return sInstance;
    }

    /**
     * Enables auto detection of the current network state based on notifications from the system.
     * Note that passing true here requires the embedding app have the platform ACCESS_NETWORK_STATE
     * permission.
     *
     * @param shouldAutoDetect true if the NetworkChangeNotifier should listen for system changes in
     *    network connectivity.
     */
    public static void setAutoDetectConnectivityState(boolean shouldAutoDetect) {
        getInstance().setAutoDetectConnectivityStateInternal(shouldAutoDetect);
    }

    private void destroyAutoDetector() {
        if (mAutoDetector != null) {
            mAutoDetector.destroy();
            mAutoDetector = null;
        }
    }

    private void setAutoDetectConnectivityStateInternal(boolean shouldAutoDetect) {
        if (shouldAutoDetect) {
            if (mAutoDetector == null) {
                mAutoDetector = new NetworkChangeNotifierAutoDetect(
                    new NetworkChangeNotifierAutoDetect.Observer() {
                        @Override
                        public void onConnectionTypeChanged(int newConnectionType) {
                            updateCurrentConnectionType(newConnectionType);
                        }
                    },
                    mContext);
                mCurrentConnectionType = mAutoDetector.getCurrentConnectionType();
            }
        } else {
            destroyAutoDetector();
        }
    }

    /**
     * Updates the perceived network state when not auto-detecting changes to connectivity.
     *
     * @param networkAvailable True if the NetworkChangeNotifier should perceive a "connected"
     *    state, false implies "disconnected".
     */
    @CalledByNative
    public static void forceConnectivityState(boolean networkAvailable) {
        setAutoDetectConnectivityState(false);
        getInstance().forceConnectivityStateInternal(networkAvailable);
    }

    private void forceConnectivityStateInternal(boolean forceOnline) {
        boolean connectionCurrentlyExists = mCurrentConnectionType != CONNECTION_NONE;
        if (connectionCurrentlyExists != forceOnline) {
            updateCurrentConnectionType(forceOnline ? CONNECTION_UNKNOWN : CONNECTION_NONE);
        }
    }

    private void updateCurrentConnectionType(int newConnectionType) {
        mCurrentConnectionType = newConnectionType;
        notifyObserversOfConnectionTypeChange(newConnectionType);
    }

    /**
     * Alerts all observers of a connection change.
     */
    void notifyObserversOfConnectionTypeChange(int newConnectionType) {
        for (Integer nativeChangeNotifier : mNativeChangeNotifiers) {
            nativeNotifyConnectionTypeChanged(nativeChangeNotifier, newConnectionType);
        }
        for (ConnectionTypeObserver observer : mConnectionTypeObservers) {
            observer.onConnectionTypeChanged(newConnectionType);
        }
    }

    /**
     * Adds an observer for any connection type changes.
     */
    public static void addConnectionTypeObserver(ConnectionTypeObserver observer) {
        getInstance().addConnectionTypeObserverInternal(observer);
    }

    private void addConnectionTypeObserverInternal(ConnectionTypeObserver observer) {
        if (!mConnectionTypeObservers.hasObserver(observer)) {
            mConnectionTypeObservers.addObserver(observer);
        }
    }

    /**
     * Removes an observer for any connection type changes.
     */
    public static void removeConnectionTypeObserver(ConnectionTypeObserver observer) {
        getInstance().removeConnectionTypeObserverInternal(observer);
    }

    private void removeConnectionTypeObserverInternal(ConnectionTypeObserver observer) {
        mConnectionTypeObservers.removeObserver(observer);
    }

    @NativeClassQualifiedName("NetworkChangeNotifierDelegateAndroid")
    private native void nativeNotifyConnectionTypeChanged(int nativePtr, int newConnectionType);

    @NativeClassQualifiedName("NetworkChangeNotifierDelegateAndroid")
    private native int nativeGetConnectionType(int nativePtr);

    // For testing only.
    public static NetworkChangeNotifierAutoDetect getAutoDetectorForTest() {
        return getInstance().mAutoDetector;
    }

    /**
     * Checks if there currently is connectivity.
     */
    public static boolean isOnline() {
        int connectionType = getInstance().getCurrentConnectionType();
        return connectionType != CONNECTION_UNKNOWN && connectionType != CONNECTION_NONE;
    }
}
