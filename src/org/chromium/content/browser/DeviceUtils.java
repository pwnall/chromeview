// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.pm.PackageManager;

import org.chromium.content.common.CommandLine;

/**
 * A utility class that has helper methods for device configuration.
 */
public class DeviceUtils {

    /**
     * The minimum width that would classify the device as a tablet.
     */
    private static final int MINIMUM_TABLET_WIDTH_DP = 600;

    /**
     * @param context Android's context
     * @return        Whether the app is should treat the device as a tablet for layout.
     */
    public static boolean isTablet(Context context) {
        if (isTv(context)) {
            return true;
        }
        int minimumScreenWidthDp = context.getResources().getConfiguration().smallestScreenWidthDp;
        return minimumScreenWidthDp >= MINIMUM_TABLET_WIDTH_DP;
    }

    /**
     * Checks if the device should be treated as TV. Note that this should be
     * invoked before {@link #isTablet(Context)} to get the correct result
     * since they are not orthogonal.
     *
     * @param context Android context
     * @return {@code true} if the device should be treated as TV.
     */
    public static boolean isTv(Context context) {
        PackageManager manager = context.getPackageManager();
        if (manager != null) {
            return manager.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
        }
        return false;
    }

    /**
     * Appends the switch specifying which user agent should be used for this device.
     * @param context The context for the caller activity.
     */
    public static void addDeviceSpecificUserAgentSwitch(Context context) {
        if (isTablet(context)) {
            CommandLine.getInstance().appendSwitch(CommandLine.TABLET_UI);
        } else {
            CommandLine.getInstance().appendSwitch(CommandLine.USE_MOBILE_UA);
        }
    }
}
