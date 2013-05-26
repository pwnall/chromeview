// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.chromium.base.PathUtils;
import org.chromium.ui.LocalizationUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Handles extracting the necessary resources bundled in an APK and moving them to a location on
 * the file system accessible from the native code.
 */
public class ResourceExtractor {

    private static final String LOGTAG = "ResourceExtractor";
    private static final String LAST_LANGUAGE = "Last language";
    private static final String PAK_FILENAMES = "Pak filenames";

    private static String[] sMandatoryPaks = null;

    // By default, we attempt to extract a pak file for the users
    // current device locale. Use setExtractImplicitLocale() to
    // change this behavior.
    private static boolean sExtractImplicitLocalePak = true;

    private class ExtractTask extends AsyncTask<Void, Void, Void> {
        private static final int BUFFER_SIZE = 16 * 1024;

        public ExtractTask() {
        }

        @Override
        protected Void doInBackground(Void... unused) {
            if (sMandatoryPaks == null) {
                assert false : "No pak files specified.  Call setMandatoryPaksToExtract before "
                        + "beginning the resource extractions";
                return null;
            }
            if (!mOutputDir.exists() && !mOutputDir.mkdirs()) {
                Log.e(LOGTAG, "Unable to create pak resources directory!");
                return null;
            }

            String timestampFile = checkPakTimestamp();
            if (timestampFile != null) {
                deleteFiles(mContext);
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            HashSet<String> filenames = (HashSet<String>) prefs.getStringSet(
                    PAK_FILENAMES, new HashSet<String>());
            String currentLocale = LocalizationUtils.getDefaultLocale();
            String currentLanguage = currentLocale.split("-", 2)[0];

            if (prefs.getString(LAST_LANGUAGE, "").equals(currentLanguage)
                    &&  filenames.size() >= sMandatoryPaks.length) {
                boolean filesPresent = true;
                for (String file : filenames) {
                    if (!new File(mOutputDir, file).exists()) {
                        filesPresent = false;
                        break;
                    }
                }
                if (filesPresent) return null;
            } else {
                prefs.edit().putString(LAST_LANGUAGE, currentLanguage).apply();
            }

            StringBuilder p = new StringBuilder();
            for (String mandatoryPak : sMandatoryPaks) {
                if (p.length() > 0) p.append('|');
                p.append("\\Q" + mandatoryPak + "\\E");
            }

            if (sExtractImplicitLocalePak) {
                if (p.length() > 0) p.append('|');
                // As well as the minimum required set of .paks above, we'll also add all .paks that
                // we have for the user's currently selected language.

                p.append(currentLanguage);
                p.append("(-\\w+)?\\.pak");
            }
            Pattern paksToInstall = Pattern.compile(p.toString());

            AssetManager manager = mContext.getResources().getAssets();
            try {
                // Loop through every asset file that we have in the APK, and look for the
                // ones that we need to extract by trying to match the Patterns that we
                // created above.
                byte[] buffer = null;
                String[] files = manager.list("");
                for (String file : files) {
                    if (!paksToInstall.matcher(file).matches()) {
                        continue;
                    }
                    File output = new File(mOutputDir, file);
                    if (output.exists()) {
                        continue;
                    }

                    InputStream is = null;
                    OutputStream os = null;
                    try {
                        is = manager.open(file);
                        os = new FileOutputStream(output);
                        Log.i(LOGTAG, "Extracting resource " + file);
                        if (buffer == null) {
                            buffer = new byte[BUFFER_SIZE];
                        }

                        int count = 0;
                        while ((count = is.read(buffer, 0, BUFFER_SIZE)) != -1) {
                            os.write(buffer, 0, count);
                        }
                        os.flush();

                        // Ensure something reasonable was written.
                        if (output.length() == 0) {
                            throw new IOException(file + " extracted with 0 length!");
                        }

                        filenames.add(file);
                    } finally {
                        try {
                            if (is != null) {
                                is.close();
                            }
                        } finally {
                            if (os != null) {
                                os.close();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // TODO(benm): See crbug/152413.
                // Try to recover here, can we try again after deleting files instead of
                // returning null? It might be useful to gather UMA here too to track if
                // this happens with regularity.
                Log.w(LOGTAG, "Exception unpacking required pak resources: " + e.getMessage());
                deleteFiles(mContext);
                return null;
            }

            // Finished, write out a timestamp file if we need to.

            if (timestampFile != null) {
                try {
                    new File(mOutputDir, timestampFile).createNewFile();
                } catch (IOException e) {
                    // Worst case we don't write a timestamp, so we'll re-extract the resource
                    // paks next start up.
                    Log.w(LOGTAG, "Failed to write resource pak timestamp!");
                }
            }
            // TODO(yusufo): Figure out why remove is required here.
            prefs.edit().remove(PAK_FILENAMES).apply();
            prefs.edit().putStringSet(PAK_FILENAMES, filenames).apply();
            return null;
        }

        // Looks for a timestamp file on disk that indicates the version of the APK that
        // the resource paks were extracted from. Returns null if a timestamp was found
        // and it indicates that the resources match the current APK. Otherwise returns
        // a String that represents the filename of a timestamp to create.
        // Note that we do this to avoid adding a BroadcastReceiver on
        // android.content.Intent#ACTION_PACKAGE_CHANGED as that causes process churn
        // on (re)installation of *all* APK files.
        private String checkPakTimestamp() {
            final String TIMESTAMP_PREFIX = "pak_timestamp-";
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pi = null;

            try {
                pi = pm.getPackageInfo(mContext.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                return TIMESTAMP_PREFIX;
            }

            if (pi == null) {
                return TIMESTAMP_PREFIX;
            }

            String expectedTimestamp = TIMESTAMP_PREFIX + pi.versionCode + "-" + pi.lastUpdateTime;

            String[] timestamps = mOutputDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(TIMESTAMP_PREFIX);
                }
            });

            if (timestamps.length != 1) {
                // If there's no timestamp, nuke to be safe as we can't tell the age of the files.
                // If there's multiple timestamps, something's gone wrong so nuke.
                return expectedTimestamp;
            }

            if (!expectedTimestamp.equals(timestamps[0])) {
                return expectedTimestamp;
            }

            // timestamp file is already up-to date.
            return null;
        }
    }

    private Context mContext;
    private ExtractTask mExtractTask;
    private File mOutputDir;

    private static ResourceExtractor sInstance;

    public static ResourceExtractor get(Context context) {
        if (sInstance == null) {
            sInstance = new ResourceExtractor(context);
        }
        return sInstance;
    }

    /**
     * Specifies the .pak files that should be extracted from the APK's asset resources directory
     * and moved to {@link #getOutputDirFromContext(Context)}.
     * @param mandatoryPaks The list of pak files to be loaded.
     */
    public static void setMandatoryPaksToExtract(String... mandatoryPaks) {
        assert (sInstance == null || sInstance.mExtractTask == null)
                : "Must be called before startExtractingResources is called";
        sMandatoryPaks = mandatoryPaks;

    }

    /**
     * By default the ResourceExtractor will attempt to extract a pak resource for the users
     * currently specified locale. This behavior can be changed with this function and is
     * only needed by tests.
     * @param extract False if we should not attempt to extract a pak file for
     *         the users currently selected locale and try to extract only the
     *         pak files specified in sMandatoryPaks.
     */
    public static void setExtractImplicitLocaleForTesting(boolean extract) {
        assert (sInstance == null || sInstance.mExtractTask == null)
                : "Must be called before startExtractingResources is called";
        sExtractImplicitLocalePak = extract;
    }

    private ResourceExtractor(Context context) {
        mContext = context;
        mOutputDir = getOutputDirFromContext(mContext);
    }

    public void waitForCompletion() {
        assert mExtractTask != null;

        try {
            mExtractTask.get();
        }
        catch (CancellationException e) {
            // Don't leave the files in an inconsistent state.
            deleteFiles(mContext);
        }
        catch (ExecutionException e2) {
            deleteFiles(mContext);
        }
        catch (InterruptedException e3) {
            deleteFiles(mContext);
        }
    }

    // This will extract the application pak resources in an
    // AsyncTask. Call waitForCompletion() at the point resources
    // are needed to block until the task completes.
    public void startExtractingResources() {
        if (mExtractTask != null) {
            return;
        }

        mExtractTask = new ExtractTask();
        mExtractTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static File getOutputDirFromContext(Context context) {
        return new File(PathUtils.getDataDirectory(context.getApplicationContext()), "paks");
    }

    public static void deleteFiles(Context context) {
        File dir = getOutputDirFromContext(context);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (File file : files) {
                if (!file.delete()) {
                    Log.w(LOGTAG, "Unable to remove existing resource " + file.getName());
                }
            }
        }
    }
}
