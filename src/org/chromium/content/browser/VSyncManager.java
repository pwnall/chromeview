// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

public abstract class VSyncManager {
    /**
     * Interface for requesting notification of the display vsync signal. The provider will call
     * Listener.onVSync() to notify about vsync. The number of registrations and unregistrations of
     * a given listener must be balanced.
     */
    public static interface Provider {
        void registerVSyncListener(Listener listener);
        void unregisterVSyncListener(Listener listener);
    }

    /**
     * Interface for receiving vsync notifications and information about the display refresh
     * interval.
     */
    public static interface Listener {
        /**
         * Notification of a vsync event.
         * @param frameTimeMicros The latest vsync frame time in microseconds.
         */
        void onVSync(long frameTimeMicros);

        /**
         * Update with the latest vsync parameters.
         * @param tickTimeMicros The latest vsync tick time in microseconds.
         * @param intervalMicros The vsync interval in microseconds.
         */
        void updateVSync(long tickTimeMicros, long intervalMicros);
    }
}
