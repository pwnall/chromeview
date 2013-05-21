// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Provides functionality needed to query and page history and the ability to access
 * items in the history.
 */
public interface NavigationClient {

    /**
     * Get a directed copy of the navigation history of the view.
     * @param isForward Whether the returned history should be entries after the current entry.
     * @param itemLimit The limit on the number of items included in the history.
     * @return A directed navigation for the page.
     */
    public NavigationHistory getDirectedNavigationHistory(boolean isForward, int itemLimit);

    /**
     * Navigates to the specified index in the navigation entry for this page.
     * @param index The navigation index to navigate to.
     */
    public void goToNavigationIndex(int index);
}
