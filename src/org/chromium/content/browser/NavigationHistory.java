// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import java.util.ArrayList;

/**
 * {@link NavigationHistory} captures a snapshot of the navigation history of a
 * {@link ContentView}. It is a copy and will not be updated as navigation
 * occurs on the source {@link ContentView}.
 */
public class NavigationHistory {

    private ArrayList<NavigationEntry> entries = new ArrayList<NavigationEntry>();
    private int mCurrentEntryIndex;

    protected void addEntry(NavigationEntry entry) {
        entries.add(entry);
    }

    /* package */ void setCurrentEntryIndex(int currentEntryIndex) {
        mCurrentEntryIndex = currentEntryIndex;
    }

    /**
     * @return The number of entries in the history.
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Returns the {@link NavigationEntry} for the given index.
     */
    public NavigationEntry getEntryAtIndex(int index) {
        return entries.get(index);
    }

    /**
     * Returns the index of the entry the {@link ContentView} was navigated to
     * when the history was fetched.
     */
    public int getCurrentEntryIndex() {
        return mCurrentEntryIndex;
    }

}
