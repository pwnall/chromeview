// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.ViewAndroidDelegate;
import org.chromium.ui.autofill.AutofillPopup;
import org.chromium.ui.autofill.AutofillSuggestion;

// Java counterpart to the AwAutofillManagerDelegate. This class is owned by
// AwContents and has a weak reference from native side.
@JNINamespace("android_webview")
public class AwAutofillManagerDelegate {

    private final int mNativeAwAutofillManagerDelegate;
    private AutofillPopup mAutofillPopup;
    private ViewGroup mContainerView;
    private double mDIPScale;
    private ContentViewCore mContentViewCore;

    @CalledByNative
    public static AwAutofillManagerDelegate create(int nativeDelegate) {
        return new AwAutofillManagerDelegate(nativeDelegate);
    }

    private AwAutofillManagerDelegate(int nativeAwAutofillManagerDelegate) {
        mNativeAwAutofillManagerDelegate = nativeAwAutofillManagerDelegate;
    }

    public void init(ContentViewCore contentViewCore, double DIPScale) {
        mContentViewCore = contentViewCore;
        mContainerView = contentViewCore.getContainerView();
        mDIPScale = DIPScale;
    }

    @CalledByNative
    private void showAutofillPopup(float x, float y, float width, float height,
            AutofillSuggestion[] suggestions) {

        if (mContentViewCore == null) return;

        if (mAutofillPopup == null) {
            mAutofillPopup = new AutofillPopup(
                mContentViewCore.getContext(),
                getViewAndroidDelegate(),
                new AutofillPopup.AutofillPopupDelegate() {
                    public void requestHide() { }
                    public void suggestionSelected(int listIndex) {
                        nativeSuggestionSelected(mNativeAwAutofillManagerDelegate, listIndex);
                    }
                });
        }
        mAutofillPopup.setAnchorRect(x, y, width, height);
        mAutofillPopup.show(suggestions);
    }

    @CalledByNative
    public void hideAutofillPopup() {
        if (mAutofillPopup == null)
            return;
        mAutofillPopup.hide();
        mAutofillPopup = null;
    }

    private ViewAndroidDelegate getViewAndroidDelegate() {
        return new ViewAndroidDelegate() {
            @Override
            public View acquireAnchorView() {
                View anchorView = new View(mContentViewCore.getContext());
                mContainerView.addView(anchorView);
                return anchorView;
            }

            @Override
            public void setAnchorViewPosition(
                    View view, float x, float y, float width, float height) {
                assert(view.getParent() == mContainerView);

                int leftMargin = (int)Math.round(x * mDIPScale);
                int topMargin = (int)mContentViewCore.getRenderCoordinates().getContentOffsetYPix()
                        + (int)Math.round(y * mDIPScale);

                AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams((int)width,
                        (int)height, leftMargin, topMargin);
                view.setLayoutParams(lp);
            }

            @Override
            public void releaseAnchorView(View anchorView) {
                mContainerView.removeView(anchorView);
            }
        };
    }

    @CalledByNative
    private static AutofillSuggestion[] createAutofillSuggestionArray(int size) {
        return new AutofillSuggestion[size];
    }

    /**
     * @param array AutofillSuggestion array that should get a new suggestion added.
     * @param index Index in the array where to place a new suggestion.
     * @param name Name of the suggestion.
     * @param label Label of the suggestion.
     * @param uniqueId Unique suggestion id.
     */
    @CalledByNative
    private static void addToAutofillSuggestionArray(AutofillSuggestion[] array, int index,
            String name, String label, int uniqueId) {
        array[index] = new AutofillSuggestion(name, label, uniqueId);
    }

    private native void nativeSuggestionSelected(int nativeAwAutofillManagerDelegate, int position);
}
