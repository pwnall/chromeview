// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.chromium.base.ActivityStatus;

/**
 * Used by the NetworkChangeNotifier to listens to platform changes in connectivity.
 * Note that use of this class requires that the app have the platform
 * ACCESS_NETWORK_STATE permission.
 */
public class NetworkChangeNotifierAutoDetect extends BroadcastReceiver
        implements ActivityStatus.StateListener {

    /** Queries the ConnectivityManager for information about the current connection. */
    static class ConnectivityManagerDelegate {
        private final ConnectivityManager mConnectivityManager;

        ConnectivityManagerDelegate(Context context) {
            mConnectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        // For testing.
        ConnectivityManagerDelegate() {
            // All the methods below should be overridden.
            mConnectivityManager = null;
        }

        boolean activeNetworkExists() {
            return mConnectivityManager.getActiveNetworkInfo() != null;
        }

        boolean isConnected() {
            return mConnectivityManager.getActiveNetworkInfo().isConnected();
        }

        int getNetworkType() {
            return mConnectivityManager.getActiveNetworkInfo().getType();
        }

        int getNetworkSubtype() {
            return mConnectivityManager.getActiveNetworkInfo().getSubtype();
        }
    }

    private static final String TAG = "NetworkChangeNotifierAutoDetect";

    private final NetworkConnectivityIntentFilter mIntentFilter =
            new NetworkConnectivityIntentFilter();

    private final Observer mObserver;

    private final Context mContext;
    private ConnectivityManagerDelegate mConnectivityManagerDelegate;
    private boolean mRegistered;
    private int mConnectionType;

    /**
     * Observer notified on the UI thread whenever a new connection type was detected.
     */
    public static interface Observer {
        public void onConnectionTypeChanged(int newConnectionType);
    }

    public NetworkChangeNotifierAutoDetect(Observer observer, Context context) {
        mObserver = observer;
        mContext = context.getApplicationContext();
        mConnectivityManagerDelegate = new ConnectivityManagerDelegate(context);
        mConnectionType = getCurrentConnectionType();
        ActivityStatus.registerStateListener(this);
    }

    /**
     * Allows overriding the ConnectivityManagerDelegate for tests.
     */
    void setConnectivityManagerDelegateForTests(ConnectivityManagerDelegate delegate) {
        mConnectivityManagerDelegate = delegate;
    }

    public void destroy() {
        unregisterReceiver();
    }

    /**
     * Register a BroadcastReceiver in the given context.
     */
    private void registerReceiver() {
        if (!mRegistered) {
          mRegistered = true;
          mContext.registerReceiver(this, mIntentFilter);
        }
    }

    /**
     * Unregister the BroadcastReceiver in the given context.
     */
    private void unregisterReceiver() {
        if (mRegistered) {
           mRegistered = false;
           mContext.unregisterReceiver(this);
        }
    }

    public int getCurrentConnectionType() {
        // Track exactly what type of connection we have.
        if (!mConnectivityManagerDelegate.activeNetworkExists() ||
                !mConnectivityManagerDelegate.isConnected()) {
            return NetworkChangeNotifier.CONNECTION_NONE;
        }

        switch (mConnectivityManagerDelegate.getNetworkType()) {
            case ConnectivityManager.TYPE_ETHERNET:
                return NetworkChangeNotifier.CONNECTION_ETHERNET;
            case ConnectivityManager.TYPE_WIFI:
                return NetworkChangeNotifier.CONNECTION_WIFI;
            case ConnectivityManager.TYPE_WIMAX:
                return NetworkChangeNotifier.CONNECTION_4G;
            case ConnectivityManager.TYPE_MOBILE:
                // Use information from TelephonyManager to classify the connection.
                switch (mConnectivityManagerDelegate.getNetworkSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return NetworkChangeNotifier.CONNECTION_2G;
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        return NetworkChangeNotifier.CONNECTION_3G;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        return NetworkChangeNotifier.CONNECTION_4G;
                    default:
                        return NetworkChangeNotifier.CONNECTION_UNKNOWN;
                }
            default:
                return NetworkChangeNotifier.CONNECTION_UNKNOWN;
        }
    }

    // BroadcastReceiver
    @Override
    public void onReceive(Context context, Intent intent) {
        connectionTypeChanged();
    }

    // ActivityStatus.StateListener
    @Override
    public void onActivityStateChange(int state) {
        if (state == ActivityStatus.RESUMED) {
            // Note that this also covers the case where the main activity is created. The CREATED
            // event is always followed by the RESUMED event. This is a temporary "hack" until
            // http://crbug.com/176837 is fixed. The CREATED event can't be used reliably for now
            // since its notification is deferred. This means that it can immediately follow a
            // DESTROYED/STOPPED/... event which is problematic.
            // TODO(pliard): fix http://crbug.com/176837.
            connectionTypeChanged();
            registerReceiver();
        } else if (state == ActivityStatus.PAUSED) {
            unregisterReceiver();
        }
    }

    private void connectionTypeChanged() {
        int newConnectionType = getCurrentConnectionType();
        if (newConnectionType == mConnectionType) return;

        mConnectionType = newConnectionType;
        Log.d(TAG, "Network connectivity changed, type is: " + mConnectionType);
        mObserver.onConnectionTypeChanged(newConnectionType);
    }

    private static class NetworkConnectivityIntentFilter extends IntentFilter {
        NetworkConnectivityIntentFilter() {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
    }
}
