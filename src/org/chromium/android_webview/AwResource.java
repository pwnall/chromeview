// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

@JNINamespace("android_webview::AwResource")
public class AwResource {
    // The following resource ID's must be initialized by the embedder.

    // Raw resource ID for an HTML page to be displayed in the case of
    // a specific load error.
    public static int RAW_LOAD_ERROR;

    // Raw resource ID for an HTML page to be displayed in the case of
    // a generic load error. (It's called NO_DOMAIN for legacy reasons).
    public static int RAW_NO_DOMAIN;

    // String resource ID for the default text encoding to use.
    public static int STRING_DEFAULT_TEXT_ENCODING;

    // The embedder should inject a Resources object that will be used
    // to resolve Resource IDs into the actual resources.
    private static Resources sResources;

    // Loading some resources is expensive, so cache the results.
    private static Map<Integer, SoftReference<String> > sResourceCache;

    private static final int TYPE_STRING = 0;
    private static final int TYPE_RAW = 1;

    public static void setResources(Resources resources) {
        sResources = resources;
        sResourceCache = new HashMap<Integer, SoftReference<String> >();
    }

    @CalledByNative
    public static String getDefaultTextEncoding() {
        return getResource(STRING_DEFAULT_TEXT_ENCODING, TYPE_STRING);
    }

    @CalledByNative
    public static String getNoDomainPageContent() {
        return getResource(RAW_NO_DOMAIN, TYPE_RAW);
    }

    @CalledByNative
    public static String getLoadErrorPageContent() {
        return getResource(RAW_LOAD_ERROR, TYPE_RAW);
    }

    private static String getResource(int resid, int type) {
        assert resid != 0;
        assert sResources != null;
        assert sResourceCache != null;

        String result = sResourceCache.get(resid) == null ?
                null : sResourceCache.get(resid).get();
        if (result == null) {
            switch (type) {
                case TYPE_STRING:
                    result = sResources.getString(resid);
                    break;
                case TYPE_RAW:
                    result = getRawFileResourceContent(resid);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown resource type");
            }

            sResourceCache.put(resid, new SoftReference<String>(result));
        }
        return result;
    }

    private static String getRawFileResourceContent(int resid) {
        assert resid != 0;
        assert sResources != null;

        InputStreamReader isr = null;
        String result = null;

        try {
            isr = new InputStreamReader(
                    sResources.openRawResource(resid));
            // \A tells the scanner to use the beginning of the input
            // as the delimiter, hence causes it to read the entire text.
            result = new Scanner(isr).useDelimiter("\\A").next();
        } catch (Resources.NotFoundException e) {
            return "";
        } catch (NoSuchElementException e) {
            return "";
        }
        finally {
            try {
                if (isr != null) {
                    isr.close();
                }
            } catch(IOException e) {
            }
        }
        return result;
    }
}
