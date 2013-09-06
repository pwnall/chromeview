// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

/**
 * Provide Android internal resources to Chrome's ui layer.  This allows classes
 * that access resources via org.chromium.ui.R to function properly in webview.
 * In a normal Chrome build, ui resources live in a res folder in the ui layer
 * and the org.chromium.ui.R class is generated at build time based on these
 * resources.  In webview, resources live in the Android framework and can't be
 * accessed directly from the ui layer.  Instead, we copy resources needed by ui
 * into the Android framework and use this R class to map resources IDs from
 * org.chromium.ui.R to com.android.internal.R.
 */
public final class R {
    public static final class string {
        public static int low_memory_error;
        public static int opening_file_error;
        public static int color_picker_button_more;
        public static int color_picker_hue;
        public static int color_picker_saturation;
        public static int color_picker_value;
        public static int color_picker_button_set;
        public static int color_picker_button_cancel;
        public static int color_picker_dialog_title;
    }
    public static final class id {
        public static int autofill_label;
        public static int autofill_popup_window;
        public static int autofill_sublabel;
        public static int selected_color_view;
        public static int title;
        public static int more_colors_button;
        public static int color_picker_advanced;
        public static int color_picker_simple;
        public static int more_colors_button_border;
        public static int color_picker_simple_border;
        public static int gradient;
        public static int text;
        public static int seek_bar;
    }
    public static final class layout {
        public static int autofill_text;
        public static int color_picker_dialog_title;
        public static int color_picker_dialog_content;
        public static int color_picker_advanced_component;
    }
    public static final class drawable {
        public static int color_picker_advanced_select_handle;
    }
    public static final class style {
        public static int AutofillPopupWindow;
    }
    public static final class color {
        public static int color_picker_border_color;
    }
}
