/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.display;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Properties of a braille display such as dimensions and keyboard
 * configuration.
 */
public class BrailleDisplayProperties implements Parcelable {
    private final int mNumTextCells;
    private final int mNumStatusCells;
    private final BrailleKeyBinding[] mKeyBindings;
    private final Map<String, String> mFriendlyKeyNames;

    public BrailleDisplayProperties(int numTextCells, int numStatusCells,
            BrailleKeyBinding[] keyBindings,
            Map<String, String> friendlyKeyNames) {
        mNumTextCells = numTextCells;
        mNumStatusCells = numStatusCells;
        mKeyBindings = keyBindings;
        mFriendlyKeyNames = friendlyKeyNames;
    }

    /**
     * Returns the number of cells on the main display intended for display of
     * text or other content.
     */
    public int getNumTextCells() {
        return mNumTextCells;
    }

    /**
     * Returns the number of status cells that are separated from the main
     * display.  This value will be {@code 0} for displays without any separate
     * status cells.
     */
    public int getNumStatusCells() {
        return mNumStatusCells;
    }

    /**
     * Returns the list of key bindings for this display.
     */
    public BrailleKeyBinding[] getKeyBindings() {
        return mKeyBindings;
    }

    /**
     * Returns an unmodifiable map mapping key names in {@link BrailleKeyBinding}
     * objects to localized user-friendly key names.
     */
    public Map<String, String> getFriendlyKeyNames() {
        return mFriendlyKeyNames;
    }

    @Override
    public String toString() {
        return String.format(
            "BrailleDisplayProperties [numTextCells: %d, numStatusCells: %d, "
            + "keyBindings: %d]",
            mNumTextCells, mNumStatusCells, mKeyBindings.length);
    }

    // For Parcelable support.

    public static final Parcelable.Creator<BrailleDisplayProperties> CREATOR =
        new Parcelable.Creator<BrailleDisplayProperties>() {
            @Override
            public BrailleDisplayProperties createFromParcel(Parcel in) {
                return new BrailleDisplayProperties(in);
            }

            @Override
            public BrailleDisplayProperties[] newArray(int size) {
                return new BrailleDisplayProperties[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mNumTextCells);
        out.writeInt(mNumStatusCells);
        out.writeTypedArray(mKeyBindings, flags);
        out.writeInt(mFriendlyKeyNames.size());
        for (Map.Entry<String, String> entry : mFriendlyKeyNames.entrySet()) {
            out.writeString(entry.getKey());
            out.writeString(entry.getValue());
        }
    }

    private BrailleDisplayProperties(Parcel in) {
        mNumTextCells = in.readInt();
        mNumStatusCells = in.readInt();
        mKeyBindings = in.createTypedArray(BrailleKeyBinding.CREATOR);
        int size = in.readInt();
        Map<String, String> names = new HashMap<String, String>(size);
        for (int i = 0; i < size; ++i) {
            names.put(in.readString(), in.readString());
        }
        mFriendlyKeyNames = Collections.unmodifiableMap(names);
    }
}
