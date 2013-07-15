// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.SpeechRecognitionError;

import java.util.ArrayList;

/**
 * This class uses Android's SpeechRecognizer to perform speech recognition for the Web Speech API
 * on Android. Using Android's platform recognizer offers several benefits, like good quality and
 * good local fallback when no data connection is available.
 */
@JNINamespace("content")
class SpeechRecognition {

    // We track the recognition state to remember what events we need to send when recognition is
    // being aborted. Once Android's recognizer is cancelled, its listener won't yield any more
    // events, but we still need to call OnSoundEnd and OnAudioEnd if corresponding On*Start were
    // called before.
    private static final int STATE_IDLE = 0;
    private static final int STATE_AWAITING_SPEECH = 1;
    private static final int STATE_CAPTURING_SPEECH = 2;
    private int mState;

    private final Context mContext;
    private final Intent mIntent;
    private final RecognitionListener mListener;
    private SpeechRecognizer mRecognizer;

    // Native pointer to C++ SpeechRecognizerImplAndroid.
    private int mNativeSpeechRecognizerImplAndroid;

    // Remember if we are using continuous recognition in order to know when to terminate.
    private boolean mContinuous;

    // Internal class to handle events from Android's SpeechRecognizer and route them to native.
    class Listener implements RecognitionListener {

        @Override
        public void onBeginningOfSpeech() {
            mState = STATE_CAPTURING_SPEECH;
            nativeOnSoundStart(mNativeSpeechRecognizerImplAndroid);
        }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() {
            nativeOnSoundEnd(mNativeSpeechRecognizerImplAndroid);
            // Since Android doesn't have a dedicated event for when audio capture is finished, we
            // fire it after speech has ended.
            nativeOnAudioEnd(mNativeSpeechRecognizerImplAndroid);
            mState = STATE_IDLE;
        }

        @Override
        public void onError(int error) {
            int code = SpeechRecognitionError.NONE;

            // Translate Android SpeechRecognizer errors to Web Speech API errors.
            switch(error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    code = SpeechRecognitionError.AUDIO;
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    code = SpeechRecognitionError.ABORTED;
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    code = SpeechRecognitionError.NOT_ALLOWED;
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_SERVER:
                    code = SpeechRecognitionError.NETWORK;
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    code = SpeechRecognitionError.NO_MATCH;
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    code = SpeechRecognitionError.NO_SPEECH;
                    break;
                default:
                    assert false;
                    return;
            }

            terminate(code);
        }

        @Override
        public void onEvent(int event, Bundle bundle) { }

        @Override
        public void onPartialResults(Bundle bundle) {
            handleResults(bundle, true);
        }

        @Override
        public void onReadyForSpeech(Bundle bundle) {
            mState = STATE_AWAITING_SPEECH;
            nativeOnAudioStart(mNativeSpeechRecognizerImplAndroid);
        }

        @Override
        public void onResults(Bundle bundle) {
            handleResults(bundle, false);

            // If the recognition was not continuous, or if it was stopped, we tear it down after
            // receiving the final results.
            if (!mContinuous)
                terminate(SpeechRecognitionError.NONE);
        }

        @Override
        public void onRmsChanged(float rms) { }

        private void handleResults(Bundle bundle, boolean provisional) {
            ArrayList<String> list = bundle.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            String[] results = list.toArray(new String[list.size()]);

            float[] scores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

            nativeOnRecognitionResults(mNativeSpeechRecognizerImplAndroid,
                                       results,
                                       scores,
                                       provisional);
        }
    }

    private SpeechRecognition(final Context context, int nativeSpeechRecognizerImplAndroid) {
        mContext = context;
        mContinuous = false;
        mNativeSpeechRecognizerImplAndroid = nativeSpeechRecognizerImplAndroid;
        mListener = new Listener();
        mIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        mRecognizer.setRecognitionListener(mListener);
    }

    // This function destroys everything when recognition is done, taking care to properly tear
    // down by calling On{Sound,Audio}End if corresponding On{Audio,Sound}Start were called.
    private void terminate(int error) {

        if (mState != STATE_IDLE) {
            if (mState == STATE_CAPTURING_SPEECH) {
                nativeOnSoundEnd(mNativeSpeechRecognizerImplAndroid);
            }
            nativeOnAudioEnd(mNativeSpeechRecognizerImplAndroid);
            mState = STATE_IDLE;
        }

        if (error != SpeechRecognitionError.NONE)
            nativeOnRecognitionError(mNativeSpeechRecognizerImplAndroid, error);

        mRecognizer.destroy();
        mRecognizer = null;
        nativeOnRecognitionEnd(mNativeSpeechRecognizerImplAndroid);
        mNativeSpeechRecognizerImplAndroid = 0;
    }

    @CalledByNative
    private static SpeechRecognition createSpeechRecognition(
            Context context, int nativeSpeechRecognizerImplAndroid) {
        return new SpeechRecognition(context, nativeSpeechRecognizerImplAndroid);
    }

    @CalledByNative
    private void startRecognition(boolean continuous, boolean interim_results) {
        if (mRecognizer == null)
            return;

        mContinuous = continuous;
        mIntent.putExtra("android.speech.extra.DICTATION_MODE", continuous);
        mIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, interim_results);
        mRecognizer.startListening(mIntent);
    }

    @CalledByNative
    private void abortRecognition() {
        if (mRecognizer == null)
            return;

        mRecognizer.cancel();
        terminate(SpeechRecognitionError.ABORTED);
    }

    @CalledByNative
    private void stopRecognition() {
        if (mRecognizer == null)
            return;

        mContinuous = false;
        mRecognizer.stopListening();
    }

    // Native JNI calls to content/browser/speech/speech_recognizer_impl_android.cc
    private native void nativeOnAudioStart(int nativeSpeechRecognizerImplAndroid);
    private native void nativeOnSoundStart(int nativeSpeechRecognizerImplAndroid);
    private native void nativeOnSoundEnd(int nativeSpeechRecognizerImplAndroid);
    private native void nativeOnAudioEnd(int nativeSpeechRecognizerImplAndroid);
    private native void nativeOnRecognitionResults(int nativeSpeechRecognizerImplAndroid,
                                                   String[] results,
                                                   float[] scores,
                                                   boolean provisional);
    private native void nativeOnRecognitionError(int nativeSpeechRecognizerImplAndroid, int error);
    private native void nativeOnRecognitionEnd(int nativeSpeechRecognizerImplAndroid);
}
