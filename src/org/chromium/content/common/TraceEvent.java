// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Log;
import android.util.Printer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Java mirror of Chrome trace event API.  See
// base/debug/trace_event.h.  Unlike the native version, Java does not
// have stack objects, so a TRACE_EVENT() which does both
// TRACE_EVENT_BEGIN() and TRACE_EVENT_END() in ctor/dtor is not
// possible.
// It is OK to use tracing before the native library has loaded, but such traces will
// be ignored. (Perhaps we could devise to buffer them up in future?).
public class TraceEvent {

    private static volatile boolean sEnabled = false;

    /**
     * A class that records, traces and logs statistics about the main Looper.
     * The output of this class can be used in a number of interesting ways:
     * <p>
     * <ol><li>
     * When using chrometrace, there will be a near-continuous line of
     * measurements showing both event dispatches as well as idles;
     * </li><li>
     * Logging messages are output for events that run too long on the
     * event dispatcher, making it easy to identify problematic areas;
     * </li><li>
     * Statistics are output whenever there is an idle after a non-trivial
     * amount of activity, allowing information to be gathered about task
     * density and execution cadence on the Looper;
     * </li></ol>
     * <p>
     * The class attaches itself as an idle handler to the main Looper, and
     * monitors the execution of events and idle notifications. Task counters
     * accumulate between idle notifications and get reset when a new idle
     * notification is received.
     */
    private final static class LooperMonitor implements Printer, MessageQueue.IdleHandler {
        // Tags for dumping to logcat or TraceEvent
        private final static String TAG = "TraceEvent.LooperMonitor";
        private final static String IDLE_EVENT_NAME = "Looper.queueIdle";
        private final static String DISPATCH_EVENT_NAME =
                "Looper.dispatchMessage";

        // Calculation constants
        private final static long FRAME_DURATION_MILLIS = 1000L / 60L; // 60 FPS
        // A reasonable threshold for defining a Looper event as "long running"
        private final static long MIN_INTERESTING_DURATION_MILLIS =
                FRAME_DURATION_MILLIS;
        // A reasonable threshold for a "burst" of tasks on the Looper
        private final static long MIN_INTERESTING_BURST_DURATION_MILLIS =
                MIN_INTERESTING_DURATION_MILLIS * 3;

        // Stats tracking
        private long mLastIdleStartedAt = 0L;
        private long mLastWorkStartedAt = 0L;
        private int mNumTasksSeen = 0;
        private int mNumIdlesSeen = 0;
        private int mNumTasksSinceLastIdle = 0;

        // State
        private boolean mIdleMonitorAttached = false;

        @Override
        public void println(final String line) {
            if (line.startsWith(">")) {
                begin(line);
            } else {
                assert line.startsWith("<");
                end(line);
            }
        }

        // Called from within the begin/end methods only.
        // This method can only execute on the looper thread, because that is
        // the only thread that is permitted to call Looper.myqueue().
        private final void syncIdleMonitoring() {
            if (sEnabled && !mIdleMonitorAttached) {
                // approximate start time for computational purposes
                mLastIdleStartedAt = SystemClock.elapsedRealtime();
                Looper.myQueue().addIdleHandler(
                        LooperMonitor.getInstance());
                mIdleMonitorAttached = true;
                Log.v(TAG, "attached idle handler");
            } else if (mIdleMonitorAttached && !sEnabled) {
                Looper.myQueue().removeIdleHandler(
                        LooperMonitor.getInstance());
                mIdleMonitorAttached = false;
                Log.v(TAG, "detached idle handler");
            }
        }

        private final void begin(final String line) {
            // Close-out any prior 'idle' period before starting new task.
            if (mNumTasksSinceLastIdle == 0) {
                TraceEvent.end(IDLE_EVENT_NAME);
            }
            TraceEvent.begin(DISPATCH_EVENT_NAME, line);
            mLastWorkStartedAt = SystemClock.elapsedRealtime();
            syncIdleMonitoring();
        }

        private final void end(final String line) {
            final long elapsed = SystemClock.elapsedRealtime()
                    - mLastWorkStartedAt;
            if (elapsed > MIN_INTERESTING_DURATION_MILLIS) {
                traceAndLog(Log.WARN, "observed a task that took "
                        + elapsed + "ms: " + line);
            }
            TraceEvent.end(DISPATCH_EVENT_NAME);
            syncIdleMonitoring();
            mNumTasksSeen++;
            mNumTasksSinceLastIdle++;
        }

        private static void traceAndLog(int level, String message) {
            TraceEvent.instant("TraceEvent.LooperMonitor:IdleStats", message);
            Log.println(level, TAG, message);
        }

        @Override
        public final boolean queueIdle() {
            final long now =  SystemClock.elapsedRealtime();
            if (mLastIdleStartedAt == 0) mLastIdleStartedAt = now;
            final long elapsed = now - mLastIdleStartedAt;
            mNumIdlesSeen++;
            TraceEvent.begin(IDLE_EVENT_NAME, mNumTasksSinceLastIdle + " tasks since last idle.");
            if (elapsed > MIN_INTERESTING_BURST_DURATION_MILLIS) {
                // Dump stats
                String statsString = mNumTasksSeen + " tasks and "
                        + mNumIdlesSeen + " idles processed so far, "
                        + mNumTasksSinceLastIdle + " tasks bursted and "
                        + elapsed + "ms elapsed since last idle";
                traceAndLog(Log.DEBUG, statsString);
            }
            mLastIdleStartedAt = now;
            mNumTasksSinceLastIdle = 0;
            return true; // stay installed
        }

        // Holder for monitor avoids unnecessary construction on non-debug runs
        private final static class Holder {
            private final static LooperMonitor sInstance = new LooperMonitor();
        }
        public final static LooperMonitor getInstance() {
            return Holder.sInstance;
        }
    }

    private static long sTraceTagView;
    private static Method sSystemPropertiesGetLongMethod;
    private static final String PROPERTY_TRACE_TAG_ENABLEFLAGS = "debug.atrace.tags.enableflags";

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            try {
                Class<?> traceClass = Class.forName("android.os.Trace");
                sTraceTagView = traceClass.getField("TRACE_TAG_VIEW").getLong(null);

                Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
                sSystemPropertiesGetLongMethod = systemPropertiesClass.getDeclaredMethod(
                        "getLong", String.class, Long.TYPE);
                Method addChangeCallbackMethod = systemPropertiesClass.getDeclaredMethod(
                        "addChangeCallback", Runnable.class);

                // Won't reach here if any of the above reflect lookups fail.
                addChangeCallbackMethod.invoke(null, new Runnable() {
                    @Override
                    public void run() {
                        setEnabledToMatchNative();
                    }
                });
            } catch (ClassNotFoundException e) {
                Log.e("TraceEvent", "init", e);
            } catch (NoSuchMethodException e) {
                Log.e("TraceEvent", "init", e);
            } catch (IllegalArgumentException e) {
                Log.e("TraceEvent", "init", e);
            } catch (IllegalAccessException e) {
                Log.e("TraceEvent", "init", e);
            } catch (InvocationTargetException e) {
                Log.e("TraceEvent", "init", e);
            } catch (NoSuchFieldException e) {
                Log.e("TraceEvent", "init", e);
            }
        }
    }

    /**
     * Calling this will cause enabled() to be updated to match that set on the native side.
     * The native library must be loaded before calling this method.
     */
    public static void setEnabledToMatchNative() {
        boolean enabled = nativeTraceEnabled();

        if (sSystemPropertiesGetLongMethod != null) {
            try {
                long enabledFlags = (Long) sSystemPropertiesGetLongMethod.invoke(
                        null, PROPERTY_TRACE_TAG_ENABLEFLAGS, 0);
                if ((enabledFlags & sTraceTagView) != 0) {
                    nativeStartATrace();
                    enabled = true;
                } else {
                    nativeStopATrace();
                }
            } catch (IllegalArgumentException e) {
                Log.e("TraceEvent", "setEnabledToMatchNative", e);
            } catch (IllegalAccessException e) {
                Log.e("TraceEvent", "setEnabledToMatchNative", e);
            } catch (InvocationTargetException e) {
                Log.e("TraceEvent", "setEnabledToMatchNative", e);
            }
        }

        setEnabled(enabled);
    }

    /**
     * Enables or disables tracing.
     * The native library must be loaded before the first call with enabled == true.
     */
    public static synchronized void setEnabled(boolean enabled) {
        if (sEnabled == enabled) return;
        sEnabled = enabled;
        Looper.getMainLooper().setMessageLogging(
                enabled ? LooperMonitor.getInstance() : null);
    }

    /**
     * @return True if tracing is enabled, false otherwise.
     * It is safe to call trace methods without checking if TraceEvent
     * is enabled.
     */
    public static boolean enabled() {
        return sEnabled;
    }

    /**
     * Triggers the 'instant' native trace event with no arguments.
     * @param name The name of the event.
     */
    public static void instant(String name) {
        if (sEnabled) nativeInstant(name, null);
    }

    /**
     * Triggers the 'instant' native trace event.
     * @param name The name of the event.
     * @param arg  The arguments of the event.
     */
    public static void instant(String name, String arg) {
        if (sEnabled) nativeInstant(name, arg);
    }

    /**
     * Convenience wrapper around the versions of startAsync() that take string parameters.
     * @param id The id of the asynchronous event.  Will automatically figure out the name from
     *           calling {@link #getCallerName()}.
     * @see #begin()
     */
    public static void startAsync(long id) {
        if (sEnabled) nativeStartAsync(getCallerName(), id, null);
    }

    /**
     * Triggers the 'start' native trace event with no arguments.
     * @param name The name of the event.
     * @param id   The id of the asynchronous event.
     * @see #begin()
     */
    public static void startAsync(String name, long id) {
        if (sEnabled) nativeStartAsync(name, id, null);
    }

    /**
     * Triggers the 'start' native trace event.
     * @param name The name of the event.
     * @param id   The id of the asynchronous event.
     * @param arg  The arguments of the event.
     * @see #begin()
     */
    public static void startAsync(String name, long id, String arg) {
        if (sEnabled) nativeStartAsync(name, id, arg);
    }

    /**
     * Convenience wrapper around the versions of finishAsync() that take string parameters.
     * @param id The id of the asynchronous event.  Will automatically figure out the name from
     *           calling {@link #getCallerName()}.
     * @see #finish()
     */
    public static void finishAsync(long id) {
        if (sEnabled) nativeFinishAsync(getCallerName(), id, null);
    }

    /**
     * Triggers the 'finish' native trace event with no arguments.
     * @param name The name of the event.
     * @param id   The id of the asynchronous event.
     * @see #begin()
     */
    public static void finishAsync(String name, long id) {
        if (sEnabled) nativeFinishAsync(name, id, null);
    }

    /**
     * Triggers the 'finish' native trace event.
     * @param name The name of the event.
     * @param id   The id of the asynchronous event.
     * @param arg  The arguments of the event.
     * @see #begin()
     */
    public static void finishAsync(String name, long id, String arg) {
        if (sEnabled) nativeFinishAsync(name, id, arg);
    }

    /**
     * Convenience wrapper around the versions of begin() that take string parameters.
     * The name of the event will be derived from the class and function name that call this.
     * IMPORTANT: if using this version, ensure end() (no parameters) is always called from the
     * same calling context.
     */
    public static void begin() {
        if (sEnabled) nativeBegin(getCallerName(), null);
    }

    /**
     * Triggers the 'begin' native trace event with no arguments.
     * @param name The name of the event.
     */
    public static void begin(String name) {
        if (sEnabled) nativeBegin(name, null);
    }

    /**
     * Triggers the 'begin' native trace event.
     * @param name The name of the event.
     * @param arg  The arguments of the event.
     */
    public static void begin(String name, String arg) {
        if (sEnabled) nativeBegin(name, arg);
    }

    /**
     * Convenience wrapper around the versions of end() that take string parameters. See begin()
     * for more information.
     */
    public static void end() {
        if (sEnabled) nativeEnd(getCallerName(), null);
    }

    /**
     * Triggers the 'end' native trace event with no arguments.
     * @param name The name of the event.
     */
    public static void end(String name) {
        if (sEnabled) nativeEnd(name, null);
    }

    /**
     * Triggers the 'end' native trace event.
     * @param name The name of the event.
     * @param arg  The arguments of the event.
     */
    public static void end(String name, String arg) {
        if (sEnabled) nativeEnd(name, arg);
    }

    private static String getCallerName() {
        // This was measured to take about 1ms on Trygon device.
        StackTraceElement[] stack = java.lang.Thread.currentThread().getStackTrace();

        // Commented out to avoid excess call overhead, but these lines can be useful to debug
        // exactly where the TraceEvent's client is on the callstack.
        //  int index = 0;
        //  while (!stack[index].getClassName().equals(TraceEvent.class.getName())) ++index;
        //  while (stack[index].getClassName().equals(TraceEvent.class.getName())) ++index;
        //  System.logW("TraceEvent caller is at stack index " + index);

        // '4' Was derived using the above commented out code snippet.
        return stack[4].getClassName() + "." + stack[4].getMethodName();
    }

    private static native boolean nativeTraceEnabled();
    private static native void nativeStartATrace();
    private static native void nativeStopATrace();
    private static native void nativeInstant(String name, String arg);
    private static native void nativeBegin(String name, String arg);
    private static native void nativeEnd(String name, String arg);
    private static native void nativeStartAsync(String name, long id, String arg);
    private static native void nativeFinishAsync(String name, long id, String arg);
}
