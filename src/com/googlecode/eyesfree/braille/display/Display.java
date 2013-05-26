/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.display;

import android.os.Message;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * A client for the braille display service.
 */
public class Display {
    private static final String LOG_TAG = Display.class.getSimpleName();
    /** Service name used for connecting to the service. */
    public static final String ACTION_DISPLAY_SERVICE =
            "com.googlecode.eyesfree.braille.service.ACTION_DISPLAY_SERVICE";

    /** Initial value, which is never reported to the listener. */
    private static final int STATE_UNKNOWN = -2;
    public static final int STATE_ERROR = -1;
    public static final int STATE_NOT_CONNECTED = 0;
    public static final int STATE_CONNECTED = 1;

    private final OnConnectionStateChangeListener
            mConnectionStateChangeListener;
    private final Context mContext;
    private final DisplayHandler mHandler;
    private volatile OnInputEventListener mInputEventListener;
    private static final Intent mServiceIntent =
            new Intent(ACTION_DISPLAY_SERVICE);
    private Connection mConnection;
    private int currentConnectionState = STATE_UNKNOWN;
    private BrailleDisplayProperties mDisplayProperties;
    private ServiceCallback mServiceCallback = new ServiceCallback();
    /**
     * Delay before the first rebind attempt on bind error or service
     * disconnect.
     */
    private static final int REBIND_DELAY_MILLIS = 500;
    private static final int MAX_REBIND_ATTEMPTS = 5;
    private int mNumFailedBinds = 0;

    /**
     * A callback interface to get informed about connection state changes.
     */
    public interface OnConnectionStateChangeListener {
        void onConnectionStateChanged(int state);
    }

    /**
     * A callback interface for input from the braille display.
     */
    public interface OnInputEventListener {
        void onInputEvent(BrailleInputEvent inputEvent);
    }
    
    /**
     * Constructs an instance and connects to the braille display service.
     * The current thread must have an {@link android.os.Looper} associated
     * with it.  Callbacks from this object will all be executed on the
     * current thread.  Connection state will be reported to {@code listener).
     */
    public Display(Context context, OnConnectionStateChangeListener listener) {
        this(context, listener, null);
    }

    /**
     * Constructs an instance and connects to the braille display service.
     * Callbacks from this object will all be executed on the thread
     * associated with {@code handler}.  If {@code handler} is {@code null},
     * the current thread must have an {@link android.os.Looper} associated
     * with it, which will then be used to execute callbacks.  Connection
     * state will be reported to {@code listener).
     */
    public Display(Context context, OnConnectionStateChangeListener listener,
            Handler handler) {
        mContext = context;
        mConnectionStateChangeListener = listener;
        if (handler == null) {
            mHandler = new DisplayHandler();
        } else {
            mHandler = new DisplayHandler(handler);
        }
            
        doBindService();
    }

    /**
     * Sets a {@code listener} for input events.  {@code listener} can be
     * {@code null} to remove a previously set listener.
     */
    public void setOnInputEventListener(OnInputEventListener listener) {
        mInputEventListener = listener;
    }

    /**
     * Returns the display properties, or {@code null} if not connected
     * to a display.
     */
    public BrailleDisplayProperties getDisplayProperties() {
        return mDisplayProperties;
    }

    /**
     * Displays a given dots configuration on the braille display.
     * @param patterns Dots configuration to be displayed.
     */
    public void displayDots(byte[] patterns) {
        IBrailleService localService = getBrailleService();
        if (localService != null) {
            try {
                localService.displayDots(patterns);
            } catch (RemoteException ex) {
                Log.e(LOG_TAG, "Error in displayDots", ex);
            }
        } else {
            Log.v(LOG_TAG, "Error in displayDots: service not connected");
        }
    }

    /**
     * Unbinds from the braille display service and deallocates any
     * resources.  This method should be called when the braille display
     * is no longer in use by this client.
     */
    public void shutdown() {
        doUnbindService();
    }

    // NOTE: The methods in this class will be executed in the main
    // application thread.
    private class Connection implements ServiceConnection {
        private volatile IBrailleService mService;

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder binder) {
            Log.i(LOG_TAG, "Connected to braille service");
            IBrailleService localService =
                IBrailleService.Stub.asInterface(binder);
            try {
                localService.registerCallback(mServiceCallback);
                mService = localService;
                synchronized (mHandler) {
                    mNumFailedBinds = 0;
                }
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do
                // anything with it.
                Log.e(LOG_TAG, "Failed to register callback on service", e);
                // We should get a disconnected call and the rebind
                // and failure reporting happens in that handler.
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            Log.e(LOG_TAG, "Disconnected from braille service");
            // Report display disconnected for now, this will turn into a
            // connected state or error state depending on how the retrying
            // goes.
            mHandler.reportConnectionState(STATE_NOT_CONNECTED, null);
            mHandler.scheduleRebind();
        }
    }

    // NOTE: The methods of this class will be executed in the IPC
    // thread pool and not on the main application thread.
    private class ServiceCallback extends IBrailleServiceCallback.Stub {
        @Override
        public void onDisplayConnected(
            BrailleDisplayProperties displayProperties) {
            mHandler.reportConnectionState(STATE_CONNECTED, displayProperties);
        }

        @Override
        public void onDisplayDisconnected() {
            mHandler.reportConnectionState(STATE_NOT_CONNECTED, null);
        }

        @Override
        public void onInput(BrailleInputEvent inputEvent) {
            mHandler.reportInputEvent(inputEvent);
        }
    }

    private void doBindService() {
        Connection localConnection = new Connection();
        if (!mContext.bindService(mServiceIntent, localConnection,
                Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to bind Service");
            mHandler.scheduleRebind();
            return;
        }
        mConnection = localConnection;
        Log.i(LOG_TAG, "Bound to braille service");
    }

    private void doUnbindService() {
        IBrailleService localService = getBrailleService();
        if (localService != null) {
            try {
                localService.unregisterCallback(mServiceCallback);
            } catch (RemoteException e) {
                // Nothing to do if the service can't be reached.
            }
        }
        if (mConnection != null) {
            mContext.unbindService(mConnection);
            mConnection = null;
        }
    }

    private IBrailleService getBrailleService() {
        Connection localConnection = mConnection;
        if (localConnection != null) {
            return localConnection.mService;
        }
        return null;
    }

    private class DisplayHandler extends Handler {
        private static final int MSG_REPORT_CONNECTION_STATE = 1;
        private static final int MSG_REPORT_INPUT_EVENT = 2;
        private static final int MSG_REBIND_SERVICE = 3;

        public DisplayHandler() {
        }

        public DisplayHandler(Handler handler) {
            super(handler.getLooper());
        }

        public void reportConnectionState(final int newState,
                final BrailleDisplayProperties displayProperties) {
            obtainMessage(MSG_REPORT_CONNECTION_STATE, newState, 0,
                    displayProperties)
                    .sendToTarget();
        }

        public void reportInputEvent(BrailleInputEvent event) {
            obtainMessage(MSG_REPORT_INPUT_EVENT, event).sendToTarget();
        }

        public void scheduleRebind() {
            synchronized (this) {
                if (mNumFailedBinds < MAX_REBIND_ATTEMPTS) {
                    int delay = REBIND_DELAY_MILLIS << mNumFailedBinds;
                    sendEmptyMessageDelayed(MSG_REBIND_SERVICE, delay);
                    ++mNumFailedBinds;
                    Log.w(LOG_TAG, String.format(
                        "Will rebind to braille service in %d ms.", delay));
                } else {
                    reportConnectionState(STATE_ERROR, null);
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REPORT_CONNECTION_STATE:
                    handleReportConnectionState(msg.arg1,
                            (BrailleDisplayProperties) msg.obj);
                    break;
                case MSG_REPORT_INPUT_EVENT:
                    handleReportInputEvent((BrailleInputEvent) msg.obj);
                    break;
                case MSG_REBIND_SERVICE:
                    handleRebindService();
                    break;
            }
        }

        private void handleReportConnectionState(int newState,
                BrailleDisplayProperties displayProperties) {
            mDisplayProperties = displayProperties;
            if (newState != currentConnectionState
                    && mConnectionStateChangeListener != null) {
                mConnectionStateChangeListener.onConnectionStateChanged(
                    newState);
            }
            currentConnectionState = newState;
        }

        private void handleReportInputEvent(BrailleInputEvent event) {
            OnInputEventListener localListener = mInputEventListener;
            if (localListener != null) {
                localListener.onInputEvent(event);
            }
        }

        private void handleRebindService() {
            if (mConnection != null) {
                doUnbindService();
            }
            doBindService();
        }
    }
}
