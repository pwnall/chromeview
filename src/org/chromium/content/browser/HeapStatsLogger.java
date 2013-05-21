// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Debug;
import android.util.Log;

import org.chromium.content.common.CommandLine;

// Logs Heap stats, such as gc count, alloc count, etc.
// It's enabled by CommandLine.ENABLE_TEST_INTENTS, and logs whenever broadcast
// intent ACTION_LOG is received, e.g.:
// adb shell am broadcast -a com.google.android.apps.chrome.LOG_HEAP_STATS
public class HeapStatsLogger {
    private static final String TAG = "HeapStatsLogger";
    private static final String ACTION_LOG = "com.google.android.apps.chrome.LOG_HEAP_STATS";

    private static HeapStatsLogger sHeapStats;

    private HeapStatsLoggerReceiver mBroadcastReceiver;
    private HeapStatsLoggerIntentFilter mIntentFilter;

    public static void init(Context context) {
        if (CommandLine.getInstance().hasSwitch(CommandLine.ENABLE_TEST_INTENTS)) {
            sHeapStats = new HeapStatsLogger(context);
        }
    }

    private HeapStatsLogger(Context context) {
        Debug.startAllocCounting();
        mBroadcastReceiver = new HeapStatsLoggerReceiver();
        mIntentFilter = new HeapStatsLoggerIntentFilter();
        context.registerReceiver(mBroadcastReceiver, mIntentFilter);
    }

    private static class HeapStatsLoggerIntentFilter extends IntentFilter {
        HeapStatsLoggerIntentFilter() {
            addAction(ACTION_LOG);
        }
    }

    private class HeapStatsLoggerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_LOG.equals(intent.getAction())) {
                log();
            } else {
                Log.e(TAG, "Unexpected intent: " + intent);
            }
        }
    }

    private void log() {
        Log.i(TAG, "heap_stats " +
              // Format is "key=value unit", and it'll be parsed by the test
              // runner in order to be added to the bot graphs.
              "gc_count=" + Debug.getGlobalGcInvocationCount() + " times " +
              "alloc_count=" + Debug.getGlobalAllocCount() + " times " +
              "alloc_size=" + Debug.getGlobalAllocSize() + " bytes " +
              "freed_count=" + Debug.getGlobalFreedCount() + " times " +
              "freed_size=" + Debug.getGlobalFreedSize() + " bytes"
        );
    }
}
