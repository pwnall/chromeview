// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import com.google.common.annotations.VisibleForTesting;

import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

/**
 * InputConnection is created by ContentView.onCreateInputConnection.
 * It then adapts android's IME to chrome's RenderWidgetHostView using the
 * native ImeAdapterAndroid via the class ImeAdapter.
 */
public class AdapterInputConnection extends BaseInputConnection {
    private static final String TAG =
            "org.chromium.content.browser.input.AdapterInputConnection";
    private static final boolean DEBUG = false;
    /**
     * Selection value should be -1 if not known. See EditorInfo.java for details.
     */
    public static final int INVALID_SELECTION = -1;
    public static final int INVALID_COMPOSITION = -1;

    private final View mInternalView;
    private final ImeAdapter mImeAdapter;

    private boolean mSingleLine;
    private int mNumNestedBatchEdits = 0;
    private boolean mIgnoreTextInputStateUpdates = false;

    private int mLastUpdateSelectionStart = INVALID_SELECTION;
    private int mLastUpdateSelectionEnd = INVALID_SELECTION;
    private int mLastUpdateCompositionStart = INVALID_COMPOSITION;
    private int mLastUpdateCompositionEnd = INVALID_COMPOSITION;

    @VisibleForTesting
    AdapterInputConnection(View view, ImeAdapter imeAdapter, EditorInfo outAttrs) {
        super(view, true);
        mInternalView = view;
        mImeAdapter = imeAdapter;
        mImeAdapter.setInputConnection(this);
        mSingleLine = true;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;

        if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeText) {
            // Normal text field
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeTextArea ||
                imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeContentEditable) {
            // TextArea or contenteditable.
            outAttrs.inputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
                    | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NONE;
            mSingleLine = false;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypePassword) {
            // Password
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeSearch) {
            // Search
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_SEARCH;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeUrl) {
            // Url
            // TYPE_TEXT_VARIATION_URI prevents Tab key from showing, so
            // exclude it for now.
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeEmail) {
            // Email
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_GO;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeTel) {
            // Telephone
            // Number and telephone do not have both a Tab key and an
            // action in default OSK, so set the action to NEXT
            outAttrs.inputType = InputType.TYPE_CLASS_PHONE;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
        } else if (imeAdapter.getTextInputType() == ImeAdapter.sTextInputTypeNumber) {
            // Number
            outAttrs.inputType = InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_VARIATION_NORMAL;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
        }
        outAttrs.initialSelStart = imeAdapter.getInitialSelectionStart();
        outAttrs.initialSelEnd = imeAdapter.getInitialSelectionStart();
    }

    /**
     * Updates the AdapterInputConnection's internal representation of the text
     * being edited and its selection and composition properties. The resulting
     * Editable is accessible through the getEditable() method.
     * If the text has not changed, this also calls updateSelection on the InputMethodManager.
     * @param text The String contents of the field being edited
     * @param selectionStart The character offset of the selection start, or the caret
     * position if there is no selection
     * @param selectionEnd The character offset of the selection end, or the caret
     * position if there is no selection
     * @param compositionStart The character offset of the composition start, or -1
     * if there is no composition
     * @param compositionEnd The character offset of the composition end, or -1
     * if there is no selection
     */
    public void setEditableText(String text, int selectionStart, int selectionEnd,
            int compositionStart, int compositionEnd) {
        if (DEBUG) {
            Log.w(TAG, "setEditableText [" + text + "] [" + selectionStart + " " + selectionEnd
                    + "] [" + compositionStart + " " + compositionEnd + "]");
        }
        // Non-breaking spaces can cause the IME to get confused. Replace with normal spaces.
        text = text.replace('\u00A0', ' ');

        Editable editable = getEditable();

        int prevSelectionStart = Selection.getSelectionStart(editable);
        int prevSelectionEnd = Selection.getSelectionEnd(editable);
        int prevCompositionStart = getComposingSpanStart(editable);
        int prevCompositionEnd = getComposingSpanEnd(editable);
        String prevText = editable.toString();

        selectionStart = Math.min(selectionStart, text.length());
        selectionEnd = Math.min(selectionEnd, text.length());
        compositionStart = Math.min(compositionStart, text.length());
        compositionEnd = Math.min(compositionEnd, text.length());

        boolean textUnchanged = prevText.equals(text);

        if (!textUnchanged) {
            editable.replace(0, editable.length(), text);
        }

        if (prevSelectionStart == selectionStart && prevSelectionEnd == selectionEnd
                && prevCompositionStart == compositionStart
                && prevCompositionEnd == compositionEnd) {
            // Nothing has changed; don't need to do anything
            return;
        }

        Selection.setSelection(editable, selectionStart, selectionEnd);

        if (compositionStart == compositionEnd) {
            removeComposingSpans(editable);
        } else {
            super.setComposingRegion(compositionStart, compositionEnd);
        }

        if (mIgnoreTextInputStateUpdates) return;
        updateSelection(selectionStart, selectionEnd, compositionStart, compositionEnd);
    }

    @VisibleForTesting
    protected void updateSelection(
            int selectionStart, int selectionEnd,
            int compositionStart, int compositionEnd) {
        // Avoid sending update if we sent an exact update already previously.
        if (mLastUpdateSelectionStart == selectionStart &&
                mLastUpdateSelectionEnd == selectionEnd &&
                mLastUpdateCompositionStart == compositionStart &&
                mLastUpdateCompositionEnd == compositionEnd) {
            return;
        }
        if (DEBUG) {
            Log.w(TAG, "updateSelection [" + selectionStart + " " + selectionEnd + "] ["
                    + compositionStart + " " + compositionEnd + "]");
        }
        // updateSelection should be called every time the selection or composition changes
        // if it happens not within a batch edit, or at the end of each top level batch edit.
        getInputMethodManagerWrapper().updateSelection(mInternalView,
                selectionStart, selectionEnd, compositionStart, compositionEnd);
        mLastUpdateSelectionStart = selectionStart;
        mLastUpdateSelectionEnd = selectionEnd;
        mLastUpdateCompositionStart = compositionStart;
        mLastUpdateCompositionEnd = compositionEnd;
    }

    /**
     * @see BaseInputConnection#setComposingText(java.lang.CharSequence, int)
     */
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.w(TAG, "setComposingText [" + text + "] [" + newCursorPosition + "]");
        super.setComposingText(text, newCursorPosition);
        return mImeAdapter.checkCompositionQueueAndCallNative(text.toString(),
                newCursorPosition, false);
    }

    /**
     * @see BaseInputConnection#commitText(java.lang.CharSequence, int)
     */
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.w(TAG, "commitText [" + text + "] [" + newCursorPosition + "]");
        super.commitText(text, newCursorPosition);
        return mImeAdapter.checkCompositionQueueAndCallNative(text.toString(),
                newCursorPosition, text.length() > 0);
    }

    /**
     * @see BaseInputConnection#performEditorAction(int)
     */
    @Override
    public boolean performEditorAction(int actionCode) {
        if (DEBUG) Log.w(TAG, "performEditorAction [" + actionCode + "]");
        if (actionCode == EditorInfo.IME_ACTION_NEXT) {
            restartInput();
            // Send TAB key event
            long timeStampMs = System.currentTimeMillis();
            mImeAdapter.sendSyntheticKeyEvent(
                    ImeAdapter.sEventTypeRawKeyDown, timeStampMs, KeyEvent.KEYCODE_TAB, 0);
        } else {
            mImeAdapter.sendKeyEventWithKeyCode(KeyEvent.KEYCODE_ENTER,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION);
        }
        return true;
    }

    /**
     * @see BaseInputConnection#performContextMenuAction(int)
     */
    @Override
    public boolean performContextMenuAction(int id) {
        if (DEBUG) Log.w(TAG, "performContextMenuAction [" + id + "]");
        switch (id) {
            case android.R.id.selectAll:
                return mImeAdapter.selectAll();
            case android.R.id.cut:
                return mImeAdapter.cut();
            case android.R.id.copy:
                return mImeAdapter.copy();
            case android.R.id.paste:
                return mImeAdapter.paste();
            default:
                return false;
        }
    }

    /**
     * @see BaseInputConnection#getExtractedText(android.view.inputmethod.ExtractedTextRequest,
     *                                           int)
     */
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (DEBUG) Log.w(TAG, "getExtractedText");
        ExtractedText et = new ExtractedText();
        Editable editable = getEditable();
        et.text = editable.toString();
        et.partialEndOffset = editable.length();
        et.selectionStart = Selection.getSelectionStart(editable);
        et.selectionEnd = Selection.getSelectionEnd(editable);
        et.flags = mSingleLine ? ExtractedText.FLAG_SINGLE_LINE : 0;
        return et;
    }

    /**
     * @see BaseInputConnection#beginBatchEdit()
     */
    @Override
    public boolean beginBatchEdit() {
        if (DEBUG) Log.w(TAG, "beginBatchEdit [" + (mNumNestedBatchEdits == 0) + "]");
        if (mNumNestedBatchEdits == 0) mImeAdapter.batchStateChanged(true);

        mNumNestedBatchEdits++;
        return false;
    }

    /**
     * @see BaseInputConnection#endBatchEdit()
     */
    @Override
    public boolean endBatchEdit() {
        if (mNumNestedBatchEdits == 0) return false;

        --mNumNestedBatchEdits;
        if (DEBUG) Log.w(TAG, "endBatchEdit [" + (mNumNestedBatchEdits == 0) + "]");
        if (mNumNestedBatchEdits == 0) mImeAdapter.batchStateChanged(false);
        return false;
    }

    /**
     * @see BaseInputConnection#deleteSurroundingText(int, int)
     */
    @Override
    public boolean deleteSurroundingText(int leftLength, int rightLength) {
        if (DEBUG) {
            Log.w(TAG, "deleteSurroundingText [" + leftLength + " " + rightLength + "]");
        }
        if (!super.deleteSurroundingText(leftLength, rightLength)) {
            return false;
        }
        return mImeAdapter.deleteSurroundingText(leftLength, rightLength);
    }

    /**
     * @see BaseInputConnection#sendKeyEvent(android.view.KeyEvent)
     */
    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (DEBUG) Log.w(TAG, "sendKeyEvent [" + event.getAction() + "]");
        mImeAdapter.hideSelectionAndInsertionHandleControllers();

        // If this is a key-up, and backspace/del or if the key has a character representation,
        // need to update the underlying Editable (i.e. the local representation of the text
        // being edited).
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                super.deleteSurroundingText(1, 0);
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL) {
                super.deleteSurroundingText(0, 1);
            } else {
                int unicodeChar = event.getUnicodeChar();
                if (unicodeChar != 0) {
                    Editable editable = getEditable();
                    int selectionStart = Selection.getSelectionStart(editable);
                    int selectionEnd = Selection.getSelectionEnd(editable);
                    if (selectionStart > selectionEnd) {
                        int temp = selectionStart;
                        selectionStart = selectionEnd;
                        selectionEnd = temp;
                    }
                    editable.replace(selectionStart, selectionEnd,
                            Character.toString((char)unicodeChar));
                }
            }
        }
        mImeAdapter.translateAndSendNativeEvents(event);
        return true;
    }

    /**
     * @see BaseInputConnection#finishComposingText()
     */
    @Override
    public boolean finishComposingText() {
        if (DEBUG) Log.w(TAG, "finishComposingText");
        Editable editable = getEditable();
        if (getComposingSpanStart(editable) == getComposingSpanEnd(editable)) {
            return true;
        }

        // TODO(aurimas): remove this workaround of changing composition before confirmComposition
        //                Blink should support keeping the cursor (http://crbug.com/239923)
        int selectionStart = Selection.getSelectionStart(editable);
        int compositionStart = getComposingSpanStart(editable);
        super.finishComposingText();

        beginBatchEdit();
        if (compositionStart != -1 && compositionStart < selectionStart
                && !mImeAdapter.setComposingRegion(compositionStart, selectionStart)) {
            return false;
        }
        if (!mImeAdapter.checkCompositionQueueAndCallNative("", 0, true)) return false;
        endBatchEdit();
        return true;
    }

    /**
     * @see BaseInputConnection#setSelection(int, int)
     */
    @Override
    public boolean setSelection(int start, int end) {
        if (DEBUG) Log.w(TAG, "setSelection");
        if (start < 0 || end < 0) return true;
        super.setSelection(start, end);
        return mImeAdapter.setEditableSelectionOffsets(start, end);
    }

    /**
     * Informs the InputMethodManager and InputMethodSession (i.e. the IME) that the text
     * state is no longer what the IME has and that it needs to be updated.
     */
    void restartInput() {
        if (DEBUG) Log.w(TAG, "restartInput");
        getInputMethodManagerWrapper().restartInput(mInternalView);
        mIgnoreTextInputStateUpdates = false;
        mNumNestedBatchEdits = 0;
    }

    /**
     * @see BaseInputConnection#setComposingRegion(int, int)
     */
    @Override
    public boolean setComposingRegion(int start, int end) {
        if (DEBUG) Log.w(TAG, "setComposingRegion [" + start + " " + end + "]");
        int a = Math.min(start, end);
        int b = Math.max(start, end);
        if (a < 0) a = 0;
        if (b < 0) b = 0;

        if (a == b) {
            removeComposingSpans(getEditable());
        } else {
            super.setComposingRegion(a, b);
        }
        return mImeAdapter.setComposingRegion(a, b);
    }

    boolean isActive() {
        return getInputMethodManagerWrapper().isActive(mInternalView);
    }

    public void setIgnoreTextInputStateUpdates(boolean shouldIgnore) {
        mIgnoreTextInputStateUpdates = shouldIgnore;
        if (shouldIgnore) return;

        Editable editable = getEditable();
        updateSelection(Selection.getSelectionStart(editable),
                Selection.getSelectionEnd(editable),
                getComposingSpanStart(editable),
                getComposingSpanEnd(editable));
    }

    @VisibleForTesting
    protected boolean isIgnoringTextInputStateUpdates() {
        return mIgnoreTextInputStateUpdates;
    }

    private InputMethodManagerWrapper getInputMethodManagerWrapper() {
        return mImeAdapter.getInputMethodManagerWrapper();
    }
}
