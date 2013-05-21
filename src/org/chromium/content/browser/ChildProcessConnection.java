// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.chromium.base.CalledByNative;
import org.chromium.base.CpuFeatures;
import org.chromium.base.ThreadUtils;
import org.chromium.content.app.ChildProcessService;
import org.chromium.content.common.CommandLine;
import org.chromium.content.common.IChildProcessCallback;
import org.chromium.content.common.IChildProcessService;
import org.chromium.content.common.TraceEvent;

public class ChildProcessConnection implements ServiceConnection {
    interface DeathCallback {
        void onChildProcessDied(int pid);
    }

    // Names of items placed in the bind intent or connection bundle.
    public static final String EXTRA_COMMAND_LINE =
            "com.google.android.apps.chrome.extra.command_line";
    // Note the FDs may only be passed in the connection bundle.
    public static final String EXTRA_FILES_PREFIX =
            "com.google.android.apps.chrome.extra.extraFile_";
    public static final String EXTRA_FILES_ID_SUFFIX = "_id";
    public static final String EXTRA_FILES_FD_SUFFIX = "_fd";

    // Used to pass the CPU core count to child processes.
    public static final String EXTRA_CPU_COUNT =
            "com.google.android.apps.chrome.extra.cpu_count";
    // Used to pass the CPU features mask to child processes.
    public static final String EXTRA_CPU_FEATURES =
            "com.google.android.apps.chrome.extra.cpu_features";

    private final Context mContext;
    private final int mServiceNumber;
    private final boolean mInSandbox;
    private final ChildProcessConnection.DeathCallback mDeathCallback;
    private final Class<? extends ChildProcessService> mServiceClass;

    // Synchronization: While most internal flow occurs on the UI thread, the public API
    // (specifically bind and unbind) may be called from any thread, hence all entry point methods
    // into the class are synchronized on the ChildProcessConnection instance to protect access
    // to these members. But see also the TODO where AsyncBoundServiceConnection is created.
    private final Object mUiThreadLock = new Object();
    private IChildProcessService mService = null;
    private boolean mServiceConnectComplete = false;
    private int mPID = 0;  // Process ID of the corresponding child process.
    private HighPriorityConnection mHighPriorityConnection = null;
    private int mHighPriorityConnectionCount = 0;

    private static final String TAG = "ChildProcessConnection";

    private static class ConnectionParams {
        final String[] mCommandLine;
        final FileDescriptorInfo[] mFilesToBeMapped;
        final IChildProcessCallback mCallback;
        final Runnable mOnConnectionCallback;

        ConnectionParams(
                String[] commandLine,
                FileDescriptorInfo[] filesToBeMapped,
                IChildProcessCallback callback,
                Runnable onConnectionCallback) {
            mCommandLine = commandLine;
            mFilesToBeMapped = filesToBeMapped;
            mCallback = callback;
            mOnConnectionCallback = onConnectionCallback;
        }
    }

    // This is only valid while the connection is being established.
    private ConnectionParams mConnectionParams;
    private boolean mIsBound;

    ChildProcessConnection(Context context, int number, boolean inSandbox,
            ChildProcessConnection.DeathCallback deathCallback,
            Class<? extends ChildProcessService> serviceClass) {
        mContext = context;
        mServiceNumber = number;
        mInSandbox = inSandbox;
        mDeathCallback = deathCallback;
        mServiceClass = serviceClass;
    }

    int getServiceNumber() {
        return mServiceNumber;
    }

    boolean isInSandbox() {
        return mInSandbox;
    }

    IChildProcessService getService() {
        synchronized(mUiThreadLock) {
            return mService;
        }
    }

    private Intent createServiceBindIntent() {
        Intent intent = new Intent();
        intent.setClassName(mContext, mServiceClass.getName() + mServiceNumber);
        intent.setPackage(mContext.getPackageName());
        return intent;
    }

    /**
     * Bind to an IChildProcessService. This must be followed by a call to setupConnection()
     * to setup the connection parameters. (These methods are separated to allow the client
     * to pass whatever parameters they have available here, and complete the remainder
     * later while reducing the connection setup latency).
     * @param commandLine (Optional) Command line for the child process. If omitted, then
     *                    the command line parameters must instead be passed to setupConnection().
     */
    void bind(String[] commandLine) {
        synchronized(mUiThreadLock) {
            TraceEvent.begin();
            assert !ThreadUtils.runningOnUiThread();

            final Intent intent = createServiceBindIntent();

            if (commandLine != null) {
                intent.putExtra(EXTRA_COMMAND_LINE, commandLine);
            }

            mIsBound = mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!mIsBound) {
                onBindFailed();
            }
            TraceEvent.end();
        }
    }

    /** Setup a connection previous bound via a call to bind().
     *
     * This establishes the parameters that were not already supplied in bind.
     * @param commandLine (Optional) will be ignored if the command line was already sent in bind()
     * @param fileToBeMapped a list of file descriptors that should be registered
     * @param callback Used for status updates regarding this process connection.
     * @param onConnectionCallback will be run when the connection is setup and ready to use.
     */
    void setupConnection(
            String[] commandLine,
            FileDescriptorInfo[] filesToBeMapped,
            IChildProcessCallback callback,
            Runnable onConnectionCallback) {
        synchronized(mUiThreadLock) {
            TraceEvent.begin();
            assert mConnectionParams == null;
            mConnectionParams = new ConnectionParams(commandLine, filesToBeMapped, callback,
                    onConnectionCallback);
            if (mServiceConnectComplete) {
                doConnectionSetup();
            }
            TraceEvent.end();
        }
    }

    /**
     * Unbind the IChildProcessService. It is safe to call this multiple times.
     */
    void unbind() {
        synchronized(mUiThreadLock) {
            if (mIsBound) {
                mContext.unbindService(this);
                mIsBound = false;
            }
            if (mService != null) {
                if (mHighPriorityConnection != null) {
                    unbindHighPriority(true);
                }
                mService = null;
                mPID = 0;
            }
            mConnectionParams = null;
            mServiceConnectComplete = false;
        }
    }

    // Called on the main thread to notify that the service is connected.
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        synchronized(mUiThreadLock) {
            TraceEvent.begin();
            mServiceConnectComplete = true;
            mService = IChildProcessService.Stub.asInterface(service);
            if (mConnectionParams != null) {
                doConnectionSetup();
            }
            TraceEvent.end();
        }
    }

    // Called on the main thread to notify that the bindService() call failed (returned false).
    private void onBindFailed() {
        mServiceConnectComplete = true;
        if (mConnectionParams != null) {
            doConnectionSetup();
        }
    }

    /**
     * Called when the connection parameters have been set, and a connection has been established
     * (as signaled by onServiceConnected), or if the connection failed (mService will be false).
     */
    private void doConnectionSetup() {
        TraceEvent.begin();
        assert mServiceConnectComplete && mConnectionParams != null;
        // Capture the callback before it is potentially nulled in unbind().
        Runnable onConnectionCallback = mConnectionParams.mOnConnectionCallback;
        if (onConnectionCallback == null) {
            unbind();
        } else if (mService != null) {
            Bundle bundle = new Bundle();
            bundle.putStringArray(EXTRA_COMMAND_LINE, mConnectionParams.mCommandLine);

            FileDescriptorInfo[] fileInfos = mConnectionParams.mFilesToBeMapped;
            ParcelFileDescriptor[] parcelFiles = new ParcelFileDescriptor[fileInfos.length];
            for (int i = 0; i < fileInfos.length; i++) {
                if (fileInfos[i].mFd == -1) {
                    // If someone provided an invalid FD, they are doing something wrong.
                    Log.e(TAG, "Invalid FD (id=" + fileInfos[i].mId + ") for process connection, "
                          + "aborting connection.");
                    return;
                }
                String idName = EXTRA_FILES_PREFIX + i + EXTRA_FILES_ID_SUFFIX;
                String fdName = EXTRA_FILES_PREFIX + i + EXTRA_FILES_FD_SUFFIX;
                if (fileInfos[i].mAutoClose) {
                    // Adopt the FD, it will be closed when we close the ParcelFileDescriptor.
                    parcelFiles[i] = ParcelFileDescriptor.adoptFd(fileInfos[i].mFd);
                } else {
                    try {
                        parcelFiles[i] = ParcelFileDescriptor.fromFd(fileInfos[i].mFd);
                    } catch(IOException e) {
                        Log.e(TAG,
                              "Invalid FD provided for process connection, aborting connection.",
                              e);
                        return;
                    }

                }
                bundle.putParcelable(fdName, parcelFiles[i]);
                bundle.putInt(idName, fileInfos[i].mId);
            }
            // Add the CPU properties now.
            bundle.putInt(EXTRA_CPU_COUNT, CpuFeatures.getCount());
            bundle.putLong(EXTRA_CPU_FEATURES, CpuFeatures.getMask());

            try {
                mPID = mService.setupConnection(bundle, mConnectionParams.mCallback);
            } catch (android.os.RemoteException re) {
                Log.e(TAG, "Failed to setup connection.", re);
            }
            // We proactivley close the FDs rather than wait for GC & finalizer.
            try {
                for (ParcelFileDescriptor parcelFile : parcelFiles) {
                    if (parcelFile != null) parcelFile.close();
                }
            } catch (IOException ioe) {
                Log.w(TAG, "Failed to close FD.", ioe);
            }
        }
        mConnectionParams = null;
        if (onConnectionCallback != null) {
            onConnectionCallback.run();
        }
        TraceEvent.end();
    }

    // Called on the main thread to notify that the child service did not disconnect gracefully.
    @Override
    public void onServiceDisconnected(ComponentName className) {
        int pid = mPID;  // Stash pid & connection callback since unbind() will clear them.
        Runnable onConnectionCallback =
            mConnectionParams != null ? mConnectionParams.mOnConnectionCallback : null;
        Log.w(TAG, "onServiceDisconnected (crash?): pid=" + pid);
        unbind();  // We don't want to auto-restart on crash. Let the browser do that.
        if (pid != 0) {
            mDeathCallback.onChildProcessDied(pid);
        }
        if (onConnectionCallback != null) {
            onConnectionCallback.run();
        }
    }

    /**
     * Bind the service with a new high priority connection. This will make the service
     * as important as the main process.
     */
    void bindHighPriority() {
        synchronized(mUiThreadLock) {
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPID);
                return;
            }
            if (mHighPriorityConnection == null) {
                mHighPriorityConnection = new HighPriorityConnection();
                mHighPriorityConnection.bind();
            }
            mHighPriorityConnectionCount++;
        }
    }

    /**
     * Unbind the service as the high priority connection.
     */
    void unbindHighPriority(boolean force) {
        synchronized(mUiThreadLock) {
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPID);
                return;
            }
            mHighPriorityConnectionCount--;
            if (force || (mHighPriorityConnectionCount == 0 && mHighPriorityConnection != null)) {
                mHighPriorityConnection.unbind();
                mHighPriorityConnection = null;
            }
        }
    }

    private class HighPriorityConnection implements ServiceConnection {

        private boolean mHBound = false;

        void bind() {
            final Intent intent = createServiceBindIntent();

            mHBound = mContext.bindService(intent, this,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        }

        void unbind() {
            if (mHBound) {
                mContext.unbindService(this);
                mHBound = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
        }
    }

    /**
     * @return The connection PID, or 0 if not yet connected.
     */
    public int getPid() {
        synchronized(mUiThreadLock) {
            return mPID;
        }
    }
}
