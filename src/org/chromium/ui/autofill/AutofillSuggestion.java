// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.autofill;

/**
 * Autofill suggestion container used to store information needed for each Autofill popup entry.
 */
public class AutofillSuggestion {
    final String mLabel;
    final String mSublabel;
    final int mUniqueId;

    /**
     * Constructs a Autofill suggestion container.
     * @param name The name of the Autofill suggestion.
     * @param label The describing label of the Autofill suggestion.
     * @param uniqueId The unique id used to identify the Autofill suggestion.
     */
    public AutofillSuggestion(String name, String label, int uniqueId) {
        mLabel = name;
        mSublabel = label;
        mUniqueId = uniqueId;
    }
}