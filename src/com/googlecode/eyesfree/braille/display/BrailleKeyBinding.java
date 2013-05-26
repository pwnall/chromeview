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

/**
 * Represents a binding between a combination of braille device keys and a
 * command as declared in {@link BrailleInputEvent}.
 */
public class BrailleKeyBinding implements Parcelable {
    private int mCommand;
    private String[] mKeyNames;

    public BrailleKeyBinding() {
    }

    public BrailleKeyBinding(int command, String[] keyNames) {
        mCommand = command;
        mKeyNames = keyNames;
    }

    /**
     * Sets the command for this binding.
     */
    public BrailleKeyBinding setCommand(int command) {
        mCommand = command;
        return this;
    }

    /**
     * Sets the key names for this binding.
     */
    public BrailleKeyBinding setKeyNames(String[] keyNames) {
        mKeyNames = keyNames;
        return this;
    }

    /**
     * Returns the command for this key binding.
     * @see {@link BrailleInputEvent}.
     */
    public int getCommand() {
        return mCommand;
    }

    /**
     * Returns the list of device-specific keys that, when pressed
     * at the same time, will yield the command of this key binding.
     */
    public String[] getKeyNames() {
        return mKeyNames;
    }

    // For Parcelable support.

    public static final Parcelable.Creator<BrailleKeyBinding> CREATOR =
        new Parcelable.Creator<BrailleKeyBinding>() {
            @Override
            public BrailleKeyBinding createFromParcel(Parcel in) {
                return new BrailleKeyBinding(in);
            }

            @Override
            public BrailleKeyBinding[] newArray(int size) {
                return new BrailleKeyBinding[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCommand);
        out.writeStringArray(mKeyNames);
    }

    private BrailleKeyBinding(Parcel in) {
        mCommand = in.readInt();
        mKeyNames = in.createStringArray();
    }
}
