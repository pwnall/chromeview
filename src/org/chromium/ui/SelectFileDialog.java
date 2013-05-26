// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.ui.WindowAndroid;

/**
 * A dialog that is triggered from a file input field that allows a user to select a file based on
 * a set of accepted file types. The path of the selected file is passed to the native dialog.
 */
@JNINamespace("ui")
class SelectFileDialog implements WindowAndroid.IntentCallback{
    private static final String IMAGE_TYPE = "image/";
    private static final String VIDEO_TYPE = "video/";
    private static final String AUDIO_TYPE = "audio/";
    private static final String ALL_IMAGE_TYPES = IMAGE_TYPE + "*";
    private static final String ALL_VIDEO_TYPES = VIDEO_TYPE + "*";
    private static final String ALL_AUDIO_TYPES = AUDIO_TYPE + "*";
    private static final String ANY_TYPES = "*/*";
    private static final String CAPTURE_CAMERA = "camera";
    private static final String CAPTURE_CAMCORDER = "camcorder";
    private static final String CAPTURE_MICROPHONE = "microphone";
    private static final String CAPTURE_FILESYSTEM = "filesystem";
    private static final String CAPTURE_IMAGE_DIRECTORY = "browser-photos";

    private final int mNativeSelectFileDialog;
    private List<String> mFileTypes;
    private String mCapture;  // May be null if no capture parameter was set.
    private Uri mCameraOutputUri;

    private SelectFileDialog(int nativeSelectFileDialog) {
        mNativeSelectFileDialog = nativeSelectFileDialog;
    }

    /**
     * Creates and starts an intent based on the passed fileTypes and capture value.
     * @param fileTypes MIME types requested (i.e. "image/*")
     * @param capture The capture value as described in http://www.w3.org/TR/html-media-capture/
     * @param window The WindowAndroid that can show intents
     */
    @CalledByNative
    private void selectFile(String[] fileTypes, String capture, WindowAndroid window) {
        mFileTypes = new ArrayList<String>(Arrays.asList(fileTypes));
        mCapture = capture;

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mCameraOutputUri = Uri.fromFile(getFileForImageCapture());
        camera.putExtra(MediaStore.EXTRA_OUTPUT, mCameraOutputUri);
        Intent camcorder = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        Intent soundRecorder = new Intent(
                MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        String lowMemoryError = window.getContext().getString(R.string.low_memory_error);

        // Quick check - if a capture parameter other than filesystem (the default) is specified we
        // should just launch the appropriate intent. Otherwise build up a chooser based on the
        // accept type and then display that to the user.
        if (captureCamera()) {
            if (window.showIntent(camera, this, lowMemoryError)) return;
        } else if (captureCamcorder()) {
            if (window.showIntent(camcorder, this, lowMemoryError)) return;
        } else if (captureMicrophone()) {
            if (window.showIntent(soundRecorder, this, lowMemoryError)) return;
        }

        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        ArrayList<Intent> extraIntents = new ArrayList<Intent>();
        if (!noSpecificType()) {
            // Create a chooser based on the accept type that was specified in the webpage. Note
            // that if the web page specified multiple accept types, we will have built a generic
            // chooser above.
            if (shouldShowImageTypes()) {
                extraIntents.add(camera);
                getContentIntent.setType("image/*");
            } else if (shouldShowVideoTypes()) {
                extraIntents.add(camcorder);
                getContentIntent.setType("video/*");
            } else if (shouldShowAudioTypes()) {
                extraIntents.add(soundRecorder);
                getContentIntent.setType("audio/*");
            }
        }

        if (extraIntents.isEmpty()) {
            // We couldn't resolve an accept type, so fallback to a generic chooser.
            getContentIntent.setType("*/*");
            extraIntents.add(camera);
            extraIntents.add(camcorder);
            extraIntents.add(soundRecorder);
        }

        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                extraIntents.toArray(new Intent[] { }));

        chooser.putExtra(Intent.EXTRA_INTENT, getContentIntent);

        if (!window.showIntent(chooser, this, lowMemoryError)) onFileNotSelected();
    }

    /**
     * Get a file for the image capture in the CAPTURE_IMAGE_DIRECTORY directory.
     */
    private File getFileForImageCapture() {
        File externalDataDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        File cameraDataDir = new File(externalDataDir.getAbsolutePath() +
                File.separator + CAPTURE_IMAGE_DIRECTORY);
        if (!cameraDataDir.exists() && !cameraDataDir.mkdirs()) {
            cameraDataDir = externalDataDir;
        }
        File photoFile = new File(cameraDataDir.getAbsolutePath() +
                File.separator + System.currentTimeMillis() + ".jpg");
        return photoFile;
    }

    /**
     * Callback method to handle the intent results and pass on the path to the native
     * SelectFileDialog.
     * @param window The window that has access to the application activity.
     * @param resultCode The result code whether the intent returned successfully.
     * @param contentResolver The content resolver used to extract the path of the selected file.
     * @param results The results of the requested intent.
     */
    @Override
    public void onIntentCompleted(WindowAndroid window, int resultCode,
            ContentResolver contentResolver, Intent results) {
        if (resultCode != Activity.RESULT_OK) {
            onFileNotSelected();
            return;
        }
        boolean success = false;
        if (results == null) {
            // If we have a successful return but no data, then assume this is the camera returning
            // the photo that we requested.
            nativeOnFileSelected(mNativeSelectFileDialog, mCameraOutputUri.getPath());
            success = true;

            // Broadcast to the media scanner that there's a new photo on the device so it will
            // show up right away in the gallery (rather than waiting until the next time the media
            // scanner runs).
            window.sendBroadcast(new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mCameraOutputUri));
        } else {
            // We get back a content:// URI from the system if the user picked a file from the
            // gallery. The ContentView has functionality that will convert that content:// URI to
            // a file path on disk that Chromium understands.
            Cursor c = contentResolver.query(results.getData(),
                    new String[] { MediaStore.MediaColumns.DATA }, null, null, null);
            if (c != null) {
                if (c.getCount() == 1) {
                    c.moveToFirst();
                    String path = c.getString(0);
                    if (path != null) {
                        // Not all providers support the MediaStore.DATA column. For example,
                        // Gallery3D (com.android.gallery3d.provider) does not support it for
                        // Picasa Web Album images.
                        nativeOnFileSelected(mNativeSelectFileDialog, path);
                        success = true;
                    }
                }
                c.close();
            }
        }
        if (!success) {
            onFileNotSelected();
            String openingFileError = window.getContext().getString(R.string.opening_file_error);
            window.showError(openingFileError);
        }
    }

    private void onFileNotSelected() {
        nativeOnFileNotSelected(mNativeSelectFileDialog);
    }

    private boolean noSpecificType() {
        // We use a single Intent to decide the type of the file chooser we display to the user,
        // which means we can only give it a single type. If there are multiple accept types
        // specified, we will fallback to a generic chooser (unless a capture parameter has been
        // specified, in which case we'll try to satisfy that first.
        return mFileTypes.size() != 1 || mFileTypes.contains(ANY_TYPES);
    }

    private boolean shouldShowTypes(String allTypes, String specificType) {
        if (noSpecificType() || mFileTypes.contains(allTypes)) return true;
        return acceptSpecificType(specificType);
    }

    private boolean shouldShowImageTypes() {
        return shouldShowTypes(ALL_IMAGE_TYPES,IMAGE_TYPE);
    }

    private boolean shouldShowVideoTypes() {
        return shouldShowTypes(ALL_VIDEO_TYPES, VIDEO_TYPE);
    }

    private boolean shouldShowAudioTypes() {
        return shouldShowTypes(ALL_AUDIO_TYPES, AUDIO_TYPE);
    }

    private boolean captureCamera() {
        return shouldShowImageTypes() && mCapture != null && mCapture.startsWith(CAPTURE_CAMERA);
    }

    private boolean captureCamcorder() {
        return shouldShowVideoTypes() && mCapture != null &&
                mCapture.startsWith(CAPTURE_CAMCORDER);
    }

    private boolean captureMicrophone() {
        return shouldShowAudioTypes() && mCapture != null &&
                mCapture.startsWith(CAPTURE_MICROPHONE);
    }

    private boolean captureFilesystem() {
        return mCapture != null && mCapture.startsWith(CAPTURE_FILESYSTEM);
    }

    private boolean acceptSpecificType(String accept) {
        for (String type : mFileTypes) {
            if (type.startsWith(accept)) {
                return true;
            }
        }
        return false;
    }

    @CalledByNative
    private static SelectFileDialog create(int nativeSelectFileDialog) {
        return new SelectFileDialog(nativeSelectFileDialog);
    }

    private native void nativeOnFileSelected(int nativeSelectFileDialogImpl,
            String filePath);
    private native void nativeOnFileNotSelected(int nativeSelectFileDialogImpl);
}
