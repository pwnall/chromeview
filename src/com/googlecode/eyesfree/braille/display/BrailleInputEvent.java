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
import android.util.SparseArray;

import java.util.HashMap;

/**
 * An input event, originating from a braille display.
 *
 * An event contains a command that is a high-level representation of the
 * key or key combination that was pressed on the display such as a
 * navigation key or braille keyboard combination.  For some commands, there is
 * also an integer argument that contains additional information.
 */
public class BrailleInputEvent implements Parcelable {

    // Movement commands.

    /** Keyboard command: Used when there is no actual command. */
    public static final int CMD_NONE = -1;

    /** Keyboard command: Navigate upwards. */
    public static final int CMD_NAV_LINE_PREVIOUS = 1;
    /** Keyboard command: Navigate downwards. */
    public static final int CMD_NAV_LINE_NEXT = 2;
    /** Keyboard command: Navigate left one item. */
    public static final int CMD_NAV_ITEM_PREVIOUS = 3;
    /** Keyboard command: Navigate right one item. */
    public static final int CMD_NAV_ITEM_NEXT = 4;
    /** Keyboard command: Navigate one display window to the left. */
    public static final int CMD_NAV_PAN_LEFT = 5;
    /** Keyboard command: Navigate one display window to the right. */
    public static final int CMD_NAV_PAN_RIGHT = 6;
    /** Keyboard command: Navigate to the top or beginning. */
    public static final int CMD_NAV_TOP = 7;
    /** Keyboard command: Navigate to the bottom or end. */
    public static final int CMD_NAV_BOTTOM = 8;

    // Activation commands.

    /** Keyboard command: Activate the currently selected/focused item. */
    public static final int CMD_ACTIVATE_CURRENT = 20;

    // Scrolling.

    /** Keyboard command: Scroll backward. */
    public static final int CMD_SCROLL_BACKWARD = 30;
    /** Keyboard command: Scroll forward. */
    public static final int CMD_SCROLL_FORWARD = 31;

    // Selection commands.

    /** Keyboard command: Set the start ot the selection. */
    public static final int CMD_SELECTION_START = 40;
    /** Keyboard command: Set the end of the selection. */
    public static final int CMD_SELECTION_END = 41;
    /** Keyboard command: Select all content of the current field. */
    public static final int CMD_SELECTION_SELECT_ALL = 42;
    /** Keyboard command: Cut the content of the selection. */
    public static final int CMD_SELECTION_CUT = 43;
    /** Keyboard command: Copy the current selection. */
    public static final int CMD_SELECTION_COPY = 44;
    /**
     * Keyboard command: Paste the content of the clipboard at the current
     * insertion point.
     */
    public static final int CMD_SELECTION_PASTE = 45;

    /**
     * Keyboard command: Primary routing key pressed, typically
     * used to move the insertion point or click/tap on the item
     * under the key.
     * The argument is the zero-based position, relative to the first cell
     * on the display, of the cell that is closed to the key that
     * was pressed.
     */
    public static final int CMD_ROUTE = 50;

    // Braille keyboard input.

    /**
     * Keyboard command: A key combination was pressed on the braille
     * keyboard.
     * The argument contains the dots that were pressed as a bitmask.
     */
    public static final int CMD_BRAILLE_KEY = 60;

    // Editing keys.

    /** Keyboard command: Enter key. */
    public static final int CMD_KEY_ENTER = 70;
    /** Keyboard command: Delete backward. */
    public static final int CMD_KEY_DEL = 71;
    /** Keyboard command: Delete forward. */
    public static final int CMD_KEY_FORWARD_DEL = 72;

    // Glboal navigation keys.

    /** Keyboard command: Back button. */
    public static final int CMD_GLOBAL_BACK = 90;
    /** Keyboard command: Home button. */
    public static final int CMD_GLOBAL_HOME = 91;
    /** Keyboard command: Recent apps button. */
    public static final int CMD_GLOBAL_RECENTS = 92;
    /** Keyboard command: Show notificaitons. */
    public static final int CMD_GLOBAL_NOTIFICATIONS = 93;

    // Miscelanous commands.

    /** Keyboard command: Invoke keyboard help. */
    public static final int CMD_HELP = 100;

    // Meanings of the argument to a command.

    /** This command doesn't have an argument. */
    public static final int ARGUMENT_NONE = 0;
    /**
     * The lower order bits of the arguemnt to this command represent braille
     * dots.  Dot 1 is represented by the rightmost bit and so on until dot 8,
     * which is represented by bit 7, counted from the right.
     */
    public static final int ARGUMENT_DOTS = 1;
    /**
     * The argument represents a 0-based position on the display counted from
     * the leftmost cell.
     */
    public static final int ARGUMENT_POSITION = 2;

    private static final SparseArray<String> CMD_NAMES =
            new SparseArray<String>();
    private static final HashMap<String, Integer> NAMES_TO_CMDS
            = new HashMap<String, Integer>();
    static {
        CMD_NAMES.append(CMD_NAV_LINE_PREVIOUS, "CMD_NAV_LINE_PREVIOUS");
        CMD_NAMES.append(CMD_NAV_LINE_NEXT, "CMD_NAV_LINE_NEXT");
        CMD_NAMES.append(CMD_NAV_ITEM_PREVIOUS, "CMD_NAV_ITEM_PREVIOUS");
        CMD_NAMES.append(CMD_NAV_ITEM_NEXT, "CMD_NAV_ITEM_NEXT");
        CMD_NAMES.append(CMD_NAV_PAN_LEFT, "CMD_NAV_PAN_LEFT");
        CMD_NAMES.append(CMD_NAV_PAN_RIGHT, "CMD_NAV_PAN_RIGHT");
        CMD_NAMES.append(CMD_NAV_TOP, "CMD_NAV_TOP");
        CMD_NAMES.append(CMD_NAV_BOTTOM, "CMD_NAV_BOTTOM");
        CMD_NAMES.append(CMD_ACTIVATE_CURRENT, "CMD_ACTIVATE_CURRENT");
        CMD_NAMES.append(CMD_SCROLL_BACKWARD, "CMD_SCROLL_BACKWARD");
        CMD_NAMES.append(CMD_SCROLL_FORWARD, "CMD_SCROLL_FORWARD");
        CMD_NAMES.append(CMD_SELECTION_START, "CMD_SELECTION_START");
        CMD_NAMES.append(CMD_SELECTION_END, "CMD_SELECTION_END");
        CMD_NAMES.append(CMD_SELECTION_SELECT_ALL, "CMD_SELECTION_SELECT_ALL");
        CMD_NAMES.append(CMD_SELECTION_CUT, "CMD_SELECTION_CUT");
        CMD_NAMES.append(CMD_SELECTION_COPY, "CMD_SELECTION_COPY");
        CMD_NAMES.append(CMD_SELECTION_PASTE, "CMD_SELECTION_PASTE");
        CMD_NAMES.append(CMD_ROUTE, "CMD_ROUTE");
        CMD_NAMES.append(CMD_BRAILLE_KEY, "CMD_BRAILLE_KEY");
        CMD_NAMES.append(CMD_KEY_ENTER, "CMD_KEY_ENTER");
        CMD_NAMES.append(CMD_KEY_DEL, "CMD_KEY_DEL");
        CMD_NAMES.append(CMD_KEY_FORWARD_DEL, "CMD_KEY_FORWARD_DEL");
        CMD_NAMES.append(CMD_GLOBAL_BACK, "CMD_GLOBAL_BACK");
        CMD_NAMES.append(CMD_GLOBAL_HOME, "CMD_GLOBAL_HOME");
        CMD_NAMES.append(CMD_GLOBAL_RECENTS, "CMD_GLOBAL_RECENTS");
        CMD_NAMES.append(CMD_GLOBAL_NOTIFICATIONS, "CMD_GLOBAL_NOTIFICATIONS");
        CMD_NAMES.append(CMD_HELP, "CMD_HELP");
        for (int i = 0; i < CMD_NAMES.size(); ++i) {
            NAMES_TO_CMDS.put(CMD_NAMES.valueAt(i),
                    CMD_NAMES.keyAt(i));
        }
    }

    private final int mCommand;
    private final int mArgument;
    private final long mEventTime;

    public BrailleInputEvent(int command, int argument, long eventTime) {
        mCommand = command;
        mArgument = argument;
        mEventTime = eventTime;
    }

    /**
     * Returns the keyboard command that this event represents.
     */
    public int getCommand() {
        return mCommand;
    }

    /**
     * Returns the command-specific argument of the event, or zero if the
     * command doesn't have an argument.  See the individual command constants
     * for more details.
     */
    public int getArgument() {
        return mArgument;
    }

    /**
     * Returns the approximate time when this event happened as
     * returned by {@link android.os.SystemClock#uptimeMillis}.
     */
    public long getEventTime() {
        return mEventTime;
    }

    /**
     * Returns a string representation of {@code command}, or the string
     * {@code (unknown)} if the command is unknown.
     */
    public static String commandToString(int command) {
        String ret = CMD_NAMES.get(command);
        return ret != null ? ret : "(unknown)";
    }

    /**
     * Returns the command corresponding to {@code commandName}, or
     * {@link #CMD_NONE} if the name doesn't match any existing command.
     */
    public static int stringToCommand(String commandName) {
        Integer command = NAMES_TO_CMDS.get(commandName);
        if (command == null) {
            return CMD_NONE;
        }
        return command;
    }

    /**
     * Returns the type of argument for the given {@code command}.
     */
    public static int argumentType(int command) {
        switch (command) {
            case CMD_SELECTION_START:
            case CMD_SELECTION_END:
            case CMD_ROUTE:
                return ARGUMENT_POSITION;
            case CMD_BRAILLE_KEY:
                return ARGUMENT_DOTS;
            default:
                return ARGUMENT_NONE;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BrailleInputEvent {");
        sb.append("amd=");
        sb.append(commandToString(mCommand));
        sb.append(", arg=");
        sb.append(mArgument);
        sb.append("}");
        return sb.toString();
    }

    // For Parcelable support.

    public static final Parcelable.Creator<BrailleInputEvent> CREATOR =
        new Parcelable.Creator<BrailleInputEvent>() {
            @Override
            public BrailleInputEvent createFromParcel(Parcel in) {
                return new BrailleInputEvent(in);
            }

            @Override
            public BrailleInputEvent[] newArray(int size) {
                return new BrailleInputEvent[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCommand);
        out.writeInt(mArgument);
        out.writeLong(mEventTime);
    }

    private BrailleInputEvent(Parcel in) {
        mCommand = in.readInt();
        mArgument = in.readInt();
        mEventTime = in.readLong();
    }
}
