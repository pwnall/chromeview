// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

@JNINamespace("media")
class MediaPlayerBridge {

    private static final String TAG = "MediaPlayerBridge";

    private static class AllowedOperations {
        private final boolean mCanPause;
        private final boolean mCanSeekForward;
        private final boolean mCanSeekBackward;

        private AllowedOperations(boolean canPause, boolean canSeekForward,
                boolean canSeekBackward) {
            mCanPause = canPause;
            mCanSeekForward = canSeekForward;
            mCanSeekBackward = canSeekBackward;
        }

        @CalledByNative("AllowedOperations")
        private boolean canPause() { return mCanPause; }

        @CalledByNative("AllowedOperations")
        private boolean canSeekForward() { return mCanSeekForward; }

        @CalledByNative("AllowedOperations")
        private boolean canSeekBackward() { return mCanSeekBackward; }
    }

    @CalledByNative
    private static boolean setDataSource(MediaPlayer player, Context context, String url,
            String cookies, boolean hideUrlLog) {
        Uri uri = Uri.parse(url);
        HashMap<String, String> headersMap = new HashMap<String, String>();
        if (hideUrlLog)
            headersMap.put("x-hide-urls-from-log", "true");
        if (!TextUtils.isEmpty(cookies))
            headersMap.put("Cookie", cookies);
        try {
            player.setDataSource(context, uri, headersMap);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns an AllowedOperations object to show all the operations that are
     * allowed on the media player.
     */
    @CalledByNative
    private static AllowedOperations getAllowedOperations(MediaPlayer player) {
        boolean canPause = true;
        boolean canSeekForward = true;
        boolean canSeekBackward = true;
        try {
            Method getMetadata = player.getClass().getDeclaredMethod(
                    "getMetadata", boolean.class, boolean.class);
            getMetadata.setAccessible(true);
            Object data = getMetadata.invoke(player, false, false);
            if (data != null) {
                Class<?> metadataClass = data.getClass();
                Method hasMethod = metadataClass.getDeclaredMethod("has", int.class);
                Method getBooleanMethod = metadataClass.getDeclaredMethod("getBoolean", int.class);

                int pause = (Integer) metadataClass.getField("PAUSE_AVAILABLE").get(null);
                int seekForward =
                    (Integer) metadataClass.getField("SEEK_FORWARD_AVAILABLE").get(null);
                int seekBackward =
                        (Integer) metadataClass.getField("SEEK_BACKWARD_AVAILABLE").get(null);
                hasMethod.setAccessible(true);
                getBooleanMethod.setAccessible(true);
                canPause = !((Boolean) hasMethod.invoke(data, pause))
                        || ((Boolean) getBooleanMethod.invoke(data, pause));
                canSeekForward = !((Boolean) hasMethod.invoke(data, seekForward))
                        || ((Boolean) getBooleanMethod.invoke(data, seekForward));
                canSeekBackward = !((Boolean) hasMethod.invoke(data, seekBackward))
                        || ((Boolean) getBooleanMethod.invoke(data, seekBackward));
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Cannot find getMetadata() method: " + e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Cannot invoke MediaPlayer.getMetadata() method: " + e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Cannot access metadata: " + e);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Cannot find matching fields in Metadata class: " + e);
        }
        return new AllowedOperations(canPause, canSeekForward, canSeekBackward);
    }
}
