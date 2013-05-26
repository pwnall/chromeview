// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Adapts and plumbs android IME service onto the chrome text input API.
 * ImeAdapter provides an interface in both ways native <-> java:
 * 1. InputConnectionAdapter notifies native code of text composition state and
 *    dispatch key events from java -> WebKit.
 * 2. Native ImeAdapter notifies java side to clear composition text.
 *
 * The basic flow is:
 * 1. When InputConnectionAdapter gets called with composition or result text:
 *    If we receive a composition text or a result text, then we just need to
 *    dispatch a synthetic key event with special keycode 229, and then dispatch
 *    the composition or result text.
 * 2. Intercept dispatchKeyEvent() method for key events not handled by IME, we
 *   need to dispatch them to webkit and check webkit's reply. Then inject a
 *   new key event for further processing if webkit didn't handle it.
 *
 * Note that the native peer object does not take any strong reference onto the
 * instance of this java object, hence it is up to the client of this class (e.g.
 * the ViewEmbedder implementor) to hold a strong reference to it for the required
 * lifetime of the object.
 */
@JNINamespace("content")
public class ImeAdapter {
    public interface ViewEmbedder {
        /**
         * @param isFinish whether the event is occurring because input is finished.
         */
        void onImeEvent(boolean isFinish);
        void onSetFieldValue();
        void onDismissInput();
        View getAttachedView();
        ResultReceiver getNewShowKeyboardReceiver();
    }

    private class DelayedDismissInput implements Runnable {
        private final int mNativeImeAdapter;

        DelayedDismissInput(int nativeImeAdapter) {
            mNativeImeAdapter = nativeImeAdapter;
        }

        @Override
        public void run() {
            attach(mNativeImeAdapter, sTextInputTypeNone, AdapterInputConnection.INVALID_SELECTION,
                    AdapterInputConnection.INVALID_SELECTION);
            dismissInput(true);
        }
    }

    private static final int COMPOSITION_KEY_CODE = 229;

    // Delay introduced to avoid hiding the keyboard if new show requests are received.
    // The time required by the unfocus-focus events triggered by tab has been measured in soju:
    // Mean: 18.633 ms, Standard deviation: 7.9837 ms.
    // The value here should be higher enough to cover these cases, but not too high to avoid
    // letting the user perceiving important delays.
    private static final int INPUT_DISMISS_DELAY = 150;

    // All the constants that are retrieved from the C++ code.
    // They get set through initializeWebInputEvents and initializeTextInputTypes calls.
    static int sEventTypeRawKeyDown;
    static int sEventTypeKeyUp;
    static int sEventTypeChar;
    static int sTextInputTypeNone;
    static int sTextInputTypeText;
    static int sTextInputTypeTextArea;
    static int sTextInputTypePassword;
    static int sTextInputTypeSearch;
    static int sTextInputTypeUrl;
    static int sTextInputTypeEmail;
    static int sTextInputTypeTel;
    static int sTextInputTypeNumber;
    static int sTextInputTypeWeek;
    static int sTextInputTypeContentEditable;
    static int sModifierShift;
    static int sModifierAlt;
    static int sModifierCtrl;
    static int sModifierCapsLockOn;
    static int sModifierNumLockOn;

    private int mNativeImeAdapterAndroid;
    private final Context mContext;
    private InputMethodManagerWrapper mInputMethodManagerWrapper;
    private AdapterInputConnection mInputConnection;
    private final ViewEmbedder mViewEmbedder;
    private final Handler mHandler;
    private DelayedDismissInput mDismissInput = null;
    private final SelectionHandleController mSelectionHandleController;
    private final InsertionHandleController mInsertionHandleController;
    private int mTextInputType;
    private int mInitialSelectionStart;
    private int mInitialSelectionEnd;

    @VisibleForTesting
    boolean mIsShowWithoutHideOutstanding = false;

    /**
     * @param context View context.
     * @param selectionHandleController The controller that handles selection.
     * @param insertionHandleController The controller that handles insertion.
     * @param embedder The view that is used for callbacks from ImeAdapter.
     */
    public ImeAdapter(Context context, SelectionHandleController selectionHandleController,
            InsertionHandleController insertionHandleController, ViewEmbedder embedder) {
        mContext = context;
        mInputMethodManagerWrapper = new InputMethodManagerWrapper(context);
        mSelectionHandleController = selectionHandleController;
        mInsertionHandleController = insertionHandleController;
        mViewEmbedder = embedder;
        mHandler = new Handler();
    }

    public static class AdapterInputConnectionFactory {
        public AdapterInputConnection get(View view, ImeAdapter imeAdapter,
                EditorInfo outAttrs) {
            return new AdapterInputConnection(view, imeAdapter, outAttrs);
        }
    }

    @VisibleForTesting
    protected void setInputMethodManagerWrapper(InputMethodManagerWrapper immw) {
        mInputMethodManagerWrapper = immw;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return InputMethodManagerWrapper that should receive all the calls directed to
     *         InputMethodManager.
     */
    InputMethodManagerWrapper getInputMethodManagerWrapper() {
        return mInputMethodManagerWrapper;
    }

    /**
     * Set the current active InputConnection when a new InputConnection is constructed.
     * @param inputConnection The input connection that is currently used with IME.
     */
    void setInputConnection(AdapterInputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The input type of currently focused element.
     */
    int getTextInputType() {
        return mTextInputType;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The starting index of the initial text selection.
     */
    int getInitialSelectionStart() {
        return mInitialSelectionStart;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The ending index of the initial text selection.
     */
    int getInitialSelectionEnd() {
        return mInitialSelectionEnd;
    }

    public static int getTextInputTypeNone() {
        return sTextInputTypeNone;
    }

    private static int getModifiers(int metaState) {
        int modifiers = 0;
        if ((metaState & KeyEvent.META_SHIFT_ON) != 0) {
          modifiers |= sModifierShift;
        }
        if ((metaState & KeyEvent.META_ALT_ON) != 0) {
          modifiers |= sModifierAlt;
        }
        if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
          modifiers |= sModifierCtrl;
        }
        if ((metaState & KeyEvent.META_CAPS_LOCK_ON) != 0) {
          modifiers |= sModifierCapsLockOn;
        }
        if ((metaState & KeyEvent.META_NUM_LOCK_ON) != 0) {
          modifiers |= sModifierNumLockOn;
        }
        return modifiers;
    }

    void hideSelectionAndInsertionHandleControllers() {
        mSelectionHandleController.hideAndDisallowAutomaticShowing();
        mInsertionHandleController.hideAndDisallowAutomaticShowing();
    }

    public boolean isActive() {
        return mInputConnection != null && mInputConnection.isActive();
    }

    private boolean isFor(int nativeImeAdapter, int textInputType) {
        return mNativeImeAdapterAndroid == nativeImeAdapter &&
               mTextInputType == textInputType;
    }

    public void attachAndShowIfNeeded(int nativeImeAdapter, int textInputType,
            int selectionStart, int selectionEnd, boolean showIfNeeded) {
        mHandler.removeCallbacks(mDismissInput);

        // If current input type is none and showIfNeeded is false, IME should not be shown
        // and input type should remain as none.
        if (mTextInputType == sTextInputTypeNone && !showIfNeeded) {
            return;
        }

        if (!isFor(nativeImeAdapter, textInputType)) {
            // Set a delayed task to perform unfocus. This avoids hiding the keyboard when tabbing
            // through text inputs or when JS rapidly changes focus to another text element.
            if (textInputType == sTextInputTypeNone) {
                mDismissInput = new DelayedDismissInput(nativeImeAdapter);
                mHandler.postDelayed(mDismissInput, INPUT_DISMISS_DELAY);
                return;
            }

            int previousType = mTextInputType;
            attach(nativeImeAdapter, textInputType, selectionStart, selectionEnd);

            mInputMethodManagerWrapper.restartInput(mViewEmbedder.getAttachedView());
            if (showIfNeeded) {
                showKeyboard();
            }
        } else if (hasInputType() && showIfNeeded) {
            showKeyboard();
        }
    }

    public void attach(int nativeImeAdapter, int textInputType, int selectionStart,
            int selectionEnd) {
        if (mNativeImeAdapterAndroid != 0) {
            nativeResetImeAdapter(mNativeImeAdapterAndroid);
        }
        mNativeImeAdapterAndroid = nativeImeAdapter;
        mTextInputType = textInputType;
        mInitialSelectionStart = selectionStart;
        mInitialSelectionEnd = selectionEnd;
        nativeAttachImeAdapter(mNativeImeAdapterAndroid);
    }

    /**
     * Attaches the imeAdapter to its native counterpart. This is needed to start forwarding
     * keyboard events to WebKit.
     * @param nativeImeAdapter The pointer to the native ImeAdapter object.
     */
    public void attach(int nativeImeAdapter) {
        if (mNativeImeAdapterAndroid != 0) {
            nativeResetImeAdapter(mNativeImeAdapterAndroid);
        }
        mNativeImeAdapterAndroid = nativeImeAdapter;
        if (nativeImeAdapter != 0) {
            nativeAttachImeAdapter(mNativeImeAdapterAndroid);
        }
    }

    /**
     * Used to check whether the native counterpart of the ImeAdapter has been attached yet.
     * @return Whether native ImeAdapter has been attached and its pointer is currently nonzero.
     */
    public boolean isNativeImeAdapterAttached() {
        return mNativeImeAdapterAndroid != 0;
    }

    private void showKeyboard() {
        mIsShowWithoutHideOutstanding = true;
        mInputMethodManagerWrapper.showSoftInput(mViewEmbedder.getAttachedView(), 0,
                mViewEmbedder.getNewShowKeyboardReceiver());
    }

    private void dismissInput(boolean unzoomIfNeeded) {
        hideKeyboard(unzoomIfNeeded);
        mViewEmbedder.onDismissInput();
    }

    private void hideKeyboard(boolean unzoomIfNeeded) {
        mIsShowWithoutHideOutstanding  = false;
        View view = mViewEmbedder.getAttachedView();
        if (mInputMethodManagerWrapper.isActive(view)) {
            mInputMethodManagerWrapper.hideSoftInputFromWindow(view.getWindowToken(), 0,
                    unzoomIfNeeded ? mViewEmbedder.getNewShowKeyboardReceiver() : null);
        }
    }

    private boolean hasInputType() {
        return mTextInputType != sTextInputTypeNone;
    }

    static boolean isTextInputType(int type) {
        return type != sTextInputTypeNone && !InputDialogContainer.isDialogInputType(type);
    }

    public boolean hasTextInputType() {
        return isTextInputType(mTextInputType);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        return translateAndSendNativeEvents(event);
    }

    private int shouldSendKeyEventWithKeyCode(String text) {
        if (text.length() != 1) return COMPOSITION_KEY_CODE;

        if (text.equals("\n")) return KeyEvent.KEYCODE_ENTER;
        else if (text.equals("\t")) return KeyEvent.KEYCODE_TAB;
        else return COMPOSITION_KEY_CODE;
    }

    void sendKeyEventWithKeyCode(int keyCode, int flags) {
        long eventTime = System.currentTimeMillis();
        translateAndSendNativeEvents(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
        translateAndSendNativeEvents(new KeyEvent(System.currentTimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
    }

    // Calls from Java to C++

    @VisibleForTesting
    boolean checkCompositionQueueAndCallNative(String text, int newCursorPosition,
            boolean isCommit) {
        if (mNativeImeAdapterAndroid == 0) return false;

        // Committing an empty string finishes the current composition.
        boolean isFinish = text.isEmpty();
        if (!isFinish) {
            mSelectionHandleController.hideAndDisallowAutomaticShowing();
            mInsertionHandleController.hideAndDisallowAutomaticShowing();
        }
        mViewEmbedder.onImeEvent(isFinish);
        int keyCode = shouldSendKeyEventWithKeyCode(text);
        long timeStampMs = System.currentTimeMillis();

        if (keyCode != COMPOSITION_KEY_CODE) {
            sendKeyEventWithKeyCode(keyCode,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        } else {
            nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeRawKeyDown,
                    timeStampMs, keyCode, 0);
            if (isCommit) {
                nativeCommitText(mNativeImeAdapterAndroid, text);
            } else {
                nativeSetComposingText(mNativeImeAdapterAndroid, text, newCursorPosition);
            }
            nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeKeyUp,
                    timeStampMs, keyCode, 0);
        }

        return true;
    }

    boolean translateAndSendNativeEvents(KeyEvent event) {
        if (mNativeImeAdapterAndroid == 0) return false;

        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN &&
            action != KeyEvent.ACTION_UP) {
            // action == KeyEvent.ACTION_MULTIPLE
            // TODO(bulach): confirm the actual behavior. Apparently:
            // If event.getKeyCode() == KEYCODE_UNKNOWN, we can send a
            // composition key down (229) followed by a commit text with the
            // string from event.getUnicodeChars().
            // Otherwise, we'd need to send an event with a
            // WebInputEvent::IsAutoRepeat modifier. We also need to verify when
            // we receive ACTION_MULTIPLE: we may receive it after an ACTION_DOWN,
            // and if that's the case, we'll need to review when to send the Char
            // event.
            return false;
        }
        mViewEmbedder.onImeEvent(false);
        return nativeSendKeyEvent(mNativeImeAdapterAndroid, event, event.getAction(),
                getModifiers(event.getMetaState()), event.getEventTime(), event.getKeyCode(),
                                event.isSystem(), event.getUnicodeChar());
    }

    boolean sendSyntheticKeyEvent(
            int eventType, long timestampMs, int keyCode, int unicodeChar) {
        if (mNativeImeAdapterAndroid == 0) return false;

        nativeSendSyntheticKeyEvent(
                mNativeImeAdapterAndroid, eventType, timestampMs, keyCode, unicodeChar);
        return true;
    }

    boolean deleteSurroundingText(int leftLength, int rightLength) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeDeleteSurroundingText(mNativeImeAdapterAndroid, leftLength, rightLength);
        return true;
    }

    @VisibleForTesting
    protected boolean setEditableSelectionOffsets(int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSetEditableSelectionOffsets(mNativeImeAdapterAndroid, start, end);
        return true;
    }

    void batchStateChanged(boolean isBegin) {
        if (mNativeImeAdapterAndroid == 0) return;
        nativeImeBatchStateChanged(mNativeImeAdapterAndroid, isBegin);
    }

    void commitText() {
        cancelComposition();
        if (mNativeImeAdapterAndroid != 0) {
            nativeCommitText(mNativeImeAdapterAndroid, "");
        }
    }

    /**
     * Send a request to the native counterpart to set compositing region to given indices.
     * @param start The start of the composition.
     * @param end The end of the composition.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean setComposingRegion(int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSetComposingRegion(mNativeImeAdapterAndroid, start, end);
        return true;
    }

    /**
     * Send a request to the native counterpart to unselect text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean unselect() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeUnselect(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to select all the text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean selectAll() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSelectAll(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to cut the selected text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean cut() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeCut(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to copy the selected text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean copy() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeCopy(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to paste the text from the clipboard.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean paste() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativePaste(mNativeImeAdapterAndroid);
        return true;
    }

    // Calls from C++ to Java

    @CalledByNative
    private static void initializeWebInputEvents(int eventTypeRawKeyDown, int eventTypeKeyUp,
            int eventTypeChar, int modifierShift, int modifierAlt, int modifierCtrl,
            int modifierCapsLockOn, int modifierNumLockOn) {
        sEventTypeRawKeyDown = eventTypeRawKeyDown;
        sEventTypeKeyUp = eventTypeKeyUp;
        sEventTypeChar = eventTypeChar;
        sModifierShift = modifierShift;
        sModifierAlt = modifierAlt;
        sModifierCtrl = modifierCtrl;
        sModifierCapsLockOn = modifierCapsLockOn;
        sModifierNumLockOn = modifierNumLockOn;
    }

    @CalledByNative
    private static void initializeTextInputTypes(int textInputTypeNone, int textInputTypeText,
            int textInputTypeTextArea, int textInputTypePassword, int textInputTypeSearch,
            int textInputTypeUrl, int textInputTypeEmail, int textInputTypeTel,
            int textInputTypeNumber, int textInputTypeDate, int textInputTypeDateTime,
            int textInputTypeDateTimeLocal, int textInputTypeMonth, int textInputTypeTime,
            int textInputTypeWeek, int textInputTypeContentEditable) {
        sTextInputTypeNone = textInputTypeNone;
        sTextInputTypeText = textInputTypeText;
        sTextInputTypeTextArea = textInputTypeTextArea;
        sTextInputTypePassword = textInputTypePassword;
        sTextInputTypeSearch = textInputTypeSearch;
        sTextInputTypeUrl = textInputTypeUrl;
        sTextInputTypeEmail = textInputTypeEmail;
        sTextInputTypeTel = textInputTypeTel;
        sTextInputTypeNumber = textInputTypeNumber;
        sTextInputTypeWeek = textInputTypeWeek;
        sTextInputTypeContentEditable = textInputTypeContentEditable;
    }

    @CalledByNative
    private void cancelComposition() {
        if (mInputConnection != null) {
            mInputConnection.restartInput();
        }
    }

    @CalledByNative
    void detach() {
        mNativeImeAdapterAndroid = 0;
        mTextInputType = 0;
    }

    private native boolean nativeSendSyntheticKeyEvent(int nativeImeAdapterAndroid,
            int eventType, long timestampMs, int keyCode, int unicodeChar);

    private native boolean nativeSendKeyEvent(int nativeImeAdapterAndroid, KeyEvent event,
            int action, int modifiers, long timestampMs, int keyCode, boolean isSystemKey,
            int unicodeChar);

    private native void nativeSetComposingText(int nativeImeAdapterAndroid, String text,
            int newCursorPosition);

    private native void nativeCommitText(int nativeImeAdapterAndroid, String text);

    private native void nativeAttachImeAdapter(int nativeImeAdapterAndroid);

    private native void nativeSetEditableSelectionOffsets(int nativeImeAdapterAndroid,
            int start, int end);

    private native void nativeSetComposingRegion(int nativeImeAdapterAndroid, int start, int end);

    private native void nativeDeleteSurroundingText(int nativeImeAdapterAndroid,
            int before, int after);

    private native void nativeImeBatchStateChanged(int nativeImeAdapterAndroid, boolean isBegin);

    private native void nativeUnselect(int nativeImeAdapterAndroid);
    private native void nativeSelectAll(int nativeImeAdapterAndroid);
    private native void nativeCut(int nativeImeAdapterAndroid);
    private native void nativeCopy(int nativeImeAdapterAndroid);
    private native void nativePaste(int nativeImeAdapterAndroid);
    private native void nativeResetImeAdapter(int nativeImeAdapterAndroid);
}
