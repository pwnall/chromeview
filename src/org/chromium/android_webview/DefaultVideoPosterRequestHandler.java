// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import org.chromium.base.ThreadUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;

/**
 * This class takes advantage of shouldInterceptRequest(), returns the bitmap from
 * WebChromeClient.getDefaultVidoePoster() when the mDefaultVideoPosterURL is requested.
 *
 * The shouldInterceptRequest is used to get the default video poster, if the url is
 * the mDefaultVideoPosterURL.
 */
public class DefaultVideoPosterRequestHandler {
    private static InputStream getInputStream(final AwContentsClient contentClient)
            throws IOException {
        final PipedInputStream inputStream = new PipedInputStream();
        final PipedOutputStream outputStream = new PipedOutputStream(inputStream);

        // Send the request to UI thread to callback to the client, and if it provides a
        // valid bitmap bounce on to the worker thread pool to compress it into the piped
        // input/output stream.
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Bitmap defaultVideoPoster = contentClient.getDefaultVideoPoster();
                if (defaultVideoPoster == null) {
                    closeOutputStream(outputStream);
                    return;
                }
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            defaultVideoPoster.compress(Bitmap.CompressFormat.PNG, 100,
                                    outputStream);
                            outputStream.flush();
                        } catch (IOException e) {
                            Log.e(TAG, null, e);
                        } finally {
                            closeOutputStream(outputStream);
                        }
                    }
                });
            }
        });
        return inputStream;
    }

    private static void closeOutputStream(OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, null, e);
        }
    }

    private static final String TAG = "DefaultVideoPosterRequestHandler";
    private String mDefaultVideoPosterURL;
    private AwContentsClient mContentClient;

    public DefaultVideoPosterRequestHandler(AwContentsClient contentClient) {
        mDefaultVideoPosterURL = GenerateDefaulVideoPosterURL();
        mContentClient = contentClient;
    }

    /**
     * Used to get the image if the url is mDefaultVideoPosterURL.
     *
     * @param url the url requested
     * @return InterceptedRequestData which caller can get the image if the url is
     * the default video poster URL, otherwise null is returned.
     */
    public InterceptedRequestData shouldInterceptRequest(final String url) {
        if (!mDefaultVideoPosterURL.equals(url)) return null;

        try {
            return new InterceptedRequestData("image/png", null, getInputStream(mContentClient));
        } catch (IOException e) {
            Log.e(TAG, null, e);
            return null;
        }
    }

    public String getDefaultVideoPosterURL() {
        return mDefaultVideoPosterURL;
    }

    /**
     * @return a unique URL which has little chance to be used by application.
     */
    private static String GenerateDefaulVideoPosterURL() {
        Random randomGenerator = new Random();
        String path = String.valueOf(randomGenerator.nextLong());
        return "android-webview:default_video_poster/" + path;
    }
}
