// Copyright 2012 The Chromium Authors. All rights reserved.
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

/**
 * Manages a connection between the browser activity and a child service. The class is responsible
 * for estabilishing the connection (start()), closing it (stop()) and increasing the priority of
 * the service when it is in active use (between calls to attachAsActive() and detachAsActive()).
 */
public class ChildProcessConnection {
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
    // (specifically start and stop) may be called from any thread, hence all entry point methods
    // into the class are synchronized on the ChildProcessConnection instance to protect access to
    // these members. But see also the TODO where AsyncBoundServiceConnection is created.
    private final Object mUiThreadLock = new Object();
    private IChildProcessService mService = null;
    // Set to true when the service connect is finished, even if it fails.
    private boolean mServiceConnectComplete = false;
    private int mPID = 0;  // Process ID of the corresponding child process.
    // Initial binding protects the newly spawned process from being killed before it is put to use,
    // it is maintained between calls to start() and removeInitialBinding().
    private ChildServiceConnection mInitialBinding = null;
    // Strong binding will make the service priority equal to the priority of the activity. We want
    // the OS to be able to kill background renderers as it kills other background apps, so strong
    // bindings are maintained only for services that are active at the moment (between
    // attachAsActive() and detachAsActive()).
    private ChildServiceConnection mStrongBinding = null;
    // Low priority binding maintained in the entire lifetime of the connection, i.e. between calls
    // to start() and stop().
    private ChildServiceConnection mWaivedBinding = null;
    // Incremented on attachAsActive(), decremented on detachAsActive().
    private int mAttachAsActiveCount = 0;

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

    // This is set by the consumer of the class in setupConnection() and is later used in
    // doSetupConnection(), after which the variable is cleared. Therefore this is only valid while
    // the connection is being set up.
    private ConnectionParams mConnectionParams;

    private class ChildServiceConnection implements ServiceConnection {
        private boolean mBound = false;

        private final int mBindFlags;

        public ChildServiceConnection(int bindFlags) {
            mBindFlags = bindFlags;
        }

        boolean bind(String[] commandLine) {
            if (!mBound) {
                final Intent intent = createServiceBindIntent();
                if (commandLine != null) {
                    intent.putExtra(EXTRA_COMMAND_LINE, commandLine);
                }
                mBound = mContext.bindService(intent, this, mBindFlags);
            }
            return mBound;
        }

        void unbind() {
            if (mBound) {
                mContext.unbindService(this);
                mBound = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized(mUiThreadLock) {
                // A flag from the parent class ensures we run the post-connection logic only once
                // (instead of once per each ChildServiceConnection).
                if (mServiceConnectComplete) {
                    return;
                }
                TraceEvent.begin();
                mServiceConnectComplete = true;
                mService = IChildProcessService.Stub.asInterface(service);
                if (mConnectionParams != null) {
                    doConnectionSetup();
                }
                TraceEvent.end();
            }
        }


        // Called on the main thread to notify that the child service did not disconnect gracefully.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            // Ensure that the disconnection logic runs only once (instead of once per each
            // ChildServiceConnection).
            if (mService == null) {
                return;
            }
            int pid = mPID;  // Stash pid & connection callback since stop() will clear them.
            Runnable onConnectionCallback =
                mConnectionParams != null ? mConnectionParams.mOnConnectionCallback : null;
            Log.w(TAG, "onServiceDisconnected (crash or killed by oom): pid=" + pid);
            stop();  // We don't want to auto-restart on crash. Let the browser do that.
            if (pid != 0) {
                mDeathCallback.onChildProcessDied(pid);
            }
            if (onConnectionCallback != null) {
                onConnectionCallback.run();
            }
        }
    }

    ChildProcessConnection(Context context, int number, boolean inSandbox,
            ChildProcessConnection.DeathCallback deathCallback,
            Class<? extends ChildProcessService> serviceClass) {
        mContext = context;
        mServiceNumber = number;
        mInSandbox = inSandbox;
        mDeathCallback = deathCallback;
        mServiceClass = serviceClass;
        mInitialBinding = new ChildServiceConnection(Context.BIND_AUTO_CREATE);
        mStrongBinding = new ChildServiceConnection(
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        mWaivedBinding = new ChildServiceConnection(
                Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY);
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
     * Starts a connection to an IChildProcessService. This must be followed by a call to
     * setupConnection() to setup the connection parameters. start() and setupConnection() are
     * separate to allow the client to pass whatever parameters they have available here, and
     * complete the remainder later while reducing the connection setup latency.
     * @param commandLine (Optional) Command line for the child process. If omitted, then
     *                    the command line parameters must instead be passed to setupConnection().
     */
    void start(String[] commandLine) {
        synchronized(mUiThreadLock) {
            TraceEvent.begin();
            assert !ThreadUtils.runningOnUiThread();

            if (!mInitialBinding.bind(commandLine)) {
                onBindFailed();
            } else {
                mWaivedBinding.bind(null);
            }
            TraceEvent.end();
        }
    }

    /**
     * Setups the connection after it was started with start(). This method should be called by the
     * consumer of the class to set up additional connection parameters.
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
     * Terminates the connection to IChildProcessService, closing all bindings. It is safe to call
     * this multiple times.
     */
    void stop() {
        synchronized(mUiThreadLock) {
            mInitialBinding.unbind();
            mStrongBinding.unbind();
            mWaivedBinding.unbind();
            mAttachAsActiveCount = 0;
            if (mService != null) {
                mService = null;
                mPID = 0;
            }
            mConnectionParams = null;
            mServiceConnectComplete = false;
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
     * (as signaled by onServiceConnected), or if the connection failed (as signaled by
     * onBindFailed), in which case mService will be false.
     */
    private void doConnectionSetup() {
        TraceEvent.begin();
        assert mServiceConnectComplete && mConnectionParams != null;
        // Capture the callback before it is potentially nulled in unbind().
        Runnable onConnectionCallback = mConnectionParams.mOnConnectionCallback;
        if (onConnectionCallback == null) {
            stop();
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

    /**
     * Called to remove the strong binding estabilished when the connection was started. It is safe
     * to call this multiple times.
     */
    void removeInitialBinding() {
        synchronized(mUiThreadLock) {
            mInitialBinding.unbind();
        }
    }

    /**
     * Called when the service becomes active, ie important to the caller. This is handled by
     * setting up a binding that will make the service as important as the main process. We allow
     * callers to indicate the same connection as active multiple times. Instead of maintaining
     * multiple bindings, we count the requests and unbind when the count drops to zero.
     */
    void attachAsActive() {
        synchronized(mUiThreadLock) {
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPID);
                return;
            }
            if (mAttachAsActiveCount == 0) {
                mStrongBinding.bind(null);
            }
            mAttachAsActiveCount++;
        }
    }

    /**
     * Called when the service is no longer considered active.
     */
    void detachAsActive() {
        synchronized(mUiThreadLock) {
            assert mAttachAsActiveCount > 0;
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPID);
                return;
            }
            mAttachAsActiveCount--;
            if (mAttachAsActiveCount == 0) {
                mStrongBinding.unbind();
            }
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
