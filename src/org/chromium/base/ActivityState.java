package org.chromium.base;
interface ActivityState {
public final int CREATED = 1;
public final int STARTED = 2;
public final int RESUMED = 3;
public final int PAUSED = 4;
public final int STOPPED = 5;
public final int DESTROYED = 6;
}
