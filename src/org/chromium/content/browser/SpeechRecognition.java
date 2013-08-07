// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.SpeechRecognitionError;

import java.util.ArrayList;
import java.util.List;

/**
 * This class uses Android's SpeechRecognizer to perform speech recognition for the Web Speech API
 * on Android. Using Android's platform recognizer offers several benefits, like good quality and
 * good local fallback when no data connection is available.
 */
@JNINamespace("content")
public class SpeechRecognition {

    // Constants describing the speech recognition provider we depend on.
    private static final String PROVIDER_PACKAGE_NAME = "com.google.android.googlequicksearchbox";
    private static final int PROVIDER_MIN_VERSION = 300207030;

    // We track the recognition state to remember what events we need to send when recognition is
    // being aborted. Once Android's recognizer is cancelled, its listener won't yield any more
    // events, but we still need to call OnSoundEnd and OnAudioEnd if corresponding On*Start were
    // called before.
    private static final int STATE_IDLE = 0;
    private static final int STATE_AWAITING_SPEECH = 1;
    private static final int STATE_CAPTURING_SPEECH = 2;
    private int mState;

    // The speech recognition provider (if any) matching PROVIDER_PACKAGE_NAME and
    // PROVIDER_MIN_VERSION as selected by initialize().
    private static ComponentName mRecognitionProvider;

    private final Context mContext;
    private final Intent mIntent;
    private final RecognitionListener mListener;
    private SpeechRecognizer mRecognizer;

    // Native pointer to C++ SpeechRecognizerImplAndroid.
    private int mNativeSpeechRecognizerImplAndroid;

    // Remember if we are using continuous recognition.
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
            // Ignore onEndOfSpeech in continuous mode to let terminate() take care of ending
            // events. The Android API documentation is vague as to when onEndOfSpeech is called in
            // continuous mode, whereas the Web Speech API defines a stronger semantic on the
            // equivalent (onsoundend) event. Thus, the only way to provide a valid onsoundend
            // event is to trigger it when the last result is received or the session is aborted.
            if (!mContinuous) {
                nativeOnSoundEnd(mNativeSpeechRecognizerImplAndroid);
                // Since Android doesn't have a dedicated event for when audio capture is finished,
                // we fire it after speech has ended.
                nativeOnAudioEnd(mNativeSpeechRecognizerImplAndroid);
                mState = STATE_IDLE;
            }
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
            // We assume that onResults is called only once, at the end of a session, thus we
            // terminate. If one day the recognition provider changes dictation mode behavior to
            // call onResults several times, we should terminate only if (!mContinuous).
            terminate(SpeechRecognitionError.NONE);
        }

        @Override
        public void onRmsChanged(float rms) { }

        private void handleResults(Bundle bundle, boolean provisional) {
            if (mContinuous && provisional) {
                // In continuous mode, Android's recognizer sends final results as provisional.
                provisional = false;
            }

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

    // This method must be called before any instance of SpeechRecognition can be created. It will
    // query Android's package manager to find a suitable speech recognition provider that supports
    // continuous recognition.
    public static boolean initialize(Context context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context))
            return false;

        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
        final List<ResolveInfo> list = pm.queryIntentServices(intent, PackageManager.GET_SERVICES);

        for (ResolveInfo resolve : list) {
            ServiceInfo service = resolve.serviceInfo;

            if (!service.packageName.equals(PROVIDER_PACKAGE_NAME))
                continue;

            int versionCode;
            try {
                versionCode = pm.getPackageInfo(service.packageName, 0).versionCode;
            } catch (NameNotFoundException e) {
                continue;
            }

            if (versionCode < PROVIDER_MIN_VERSION)
                continue;

            mRecognitionProvider = new ComponentName(service.packageName, service.name);

            return true;
        }

        // If we reach this point, we failed to find a suitable recognition provider.
        return false;
    }

    private SpeechRecognition(final Context context, int nativeSpeechRecognizerImplAndroid) {
        mContext = context;
        mContinuous = false;
        mNativeSpeechRecognizerImplAndroid = nativeSpeechRecognizerImplAndroid;
        mListener = new Listener();
        mIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        if (mRecognitionProvider != null) {
            mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext, mRecognitionProvider);
        } else {
            // It is possible to force-enable the speech recognition web platform feature (using a
            // command-line flag) even if initialize() failed to find the PROVIDER_PACKAGE_NAME
            // provider, in which case the first available speech recognition provider is used.
            // Caveat: Continuous mode may not work as expected with a different provider.
            mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        }

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
    private void startRecognition(String language, boolean continuous, boolean interim_results) {
        if (mRecognizer == null)
            return;

        mContinuous = continuous;
        mIntent.putExtra("android.speech.extra.DICTATION_MODE", continuous);
        mIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
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
