// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import org.chromium.base.JNINamespace;
import org.chromium.content.app.ContentMain;
import org.chromium.content.app.LibraryLoader;
import org.chromium.content.common.ProcessInitException;

@JNINamespace("content")
public class AndroidBrowserProcess {
    private static final String TAG = "BrowserProcessMain";

    // Prevents initializing the process more than once.
    private static boolean sInitialized = false;

    // Use single-process mode that runs the renderer on a separate thread in the main application.
    public static final int MAX_RENDERERS_SINGLE_PROCESS = 0;

    // Cap on the maximum number of renderer processes that can be requested.
    // This is currently set to account for:
    //  13: The maximum number of sandboxed processes we have available
    // - 1: The regular New Tab Page
    // - 1: The incognito New Tab Page
    // - 1: A regular incognito tab
    // - 1: Safety buffer (http://crbug.com/251279)
    public static final int MAX_RENDERERS_LIMIT =
        ChildProcessLauncher.MAX_REGISTERED_SANDBOXED_SERVICES - 4;

    /**
     * Initialize the process as a ContentView host. This must be called from the main UI thread.
     * This should be called by the ContentView constructor to prepare this process for ContentView
     * use outside of the browser. In the case where ContentView is used in the browser then
     * initBrowserProcess() should already have been called and this is a no-op.
     *
     * @param context Context used to obtain the application context.
     * @param maxRendererProcesses Limit on the number of renderers to use. Each tab runs in its own
     * process until the maximum number of processes is reached. The special value of
     * MAX_RENDERERS_SINGLE_PROCESS requests single-process mode where the renderer will run in the
     * application process in a separate thread. The maximum number of allowed renderers is capped
     * by MAX_RENDERERS_LIMIT.
     *
     * @return Whether the process actually needed to be initialized (false if already running).
     */
     public static boolean init(Context context, int maxRendererProcesses)
             throws ProcessInitException {
        assert maxRendererProcesses >= 0;
        assert maxRendererProcesses <= MAX_RENDERERS_LIMIT;
        if (sInitialized) return false;
        sInitialized = true;
        Log.i(TAG, "Initializing chromium process, renderers=" + maxRendererProcesses);

        // Normally Main.java will have kicked this off asynchronously for Chrome. But other
        // ContentView apps like tests also need them so we make sure we've extracted resources
        // here. We can still make it a little async (wait until the library is loaded).
        ResourceExtractor resourceExtractor = ResourceExtractor.get(context);
        resourceExtractor.startExtractingResources();

        // Normally Main.java will have already loaded the library asynchronously, we only need to
        // load it here if we arrived via another flow, e.g. bookmark access & sync setup.
        LibraryLoader.ensureInitialized();
        // TODO(yfriedman): Remove dependency on a command line flag for this.
        DeviceUtils.addDeviceSpecificUserAgentSwitch(context);

        Context appContext = context.getApplicationContext();
        // Now we really need to have the resources ready.
        resourceExtractor.waitForCompletion();

        nativeSetCommandLineFlags(maxRendererProcesses,
                nativeIsPluginEnabled() ? getPlugins(context) : null);
        ContentMain.initApplicationContext(appContext);
        int result = ContentMain.start();
        if (result > 0) throw new ProcessInitException(result);
        return true;
    }

    /**
     * Initialization needed for tests. Mainly used by content browsertests.
     */
    public static void initChromiumBrowserProcessForTests(Context context) {
        ResourceExtractor resourceExtractor = ResourceExtractor.get(context);
        resourceExtractor.startExtractingResources();
        resourceExtractor.waitForCompletion();

        // Having a single renderer should be sufficient for tests. We can't have more than
        // MAX_RENDERERS_LIMIT.
        nativeSetCommandLineFlags(1 /* maxRenderers */, null);
    }

    private static String getPlugins(final Context context) {
        return PepperPluginManager.getPlugins(context);
    }

    private static native void nativeSetCommandLineFlags(int maxRenderProcesses,
            String pluginDescriptor);

    // Is this an official build of Chrome? Only native code knows for sure. Official build
    // knowledge is needed very early in process startup.
    private static native boolean nativeIsOfficialBuild();

    private static native boolean nativeIsPluginEnabled();
}
