// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.gfx;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * This class facilitates access to android information typically only
 * available using the Java SDK, including {@link Display} properties.
 *
 * Currently the information consists of very raw display information (height, width, DPI scale)
 * regarding the main display.
 */
@JNINamespace("gfx")
public class DeviceDisplayInfo {


  private final Context mAppContext;
  private final WindowManager mWinManager;

  private DeviceDisplayInfo(Context context) {
      mAppContext = context.getApplicationContext();
      mWinManager = (WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE);
  }

  /**
   * @return Display height in physical pixels.
   */
  @CalledByNative
  public int getDisplayHeight() {
      return getMetrics().heightPixels;
  }

  /**
   * @return Display width in physical pixels.
   */
  @CalledByNative
  public int getDisplayWidth() {
      return getMetrics().widthPixels;
  }

  @SuppressWarnings("deprecation")
  private int getPixelFormat() {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
          return getDisplay().getPixelFormat();
      }
      // JellyBean MR1 and later always uses RGBA_8888.
      return PixelFormat.RGBA_8888;
  }

  /**
   * @return Bits per pixel.
   */
  @CalledByNative
  public int getBitsPerPixel() {
      int format = getPixelFormat();
      PixelFormat info = new PixelFormat();
      PixelFormat.getPixelFormatInfo(format, info);
      return info.bitsPerPixel;
  }

  /**
   * @return Bits per component.
   */
  @SuppressWarnings("deprecation")
  @CalledByNative
  public int getBitsPerComponent() {
      int format = getPixelFormat();
      switch (format) {
      case PixelFormat.RGBA_4444:
          return 4;

      case PixelFormat.RGBA_5551:
          return 5;

      case PixelFormat.RGBA_8888:
      case PixelFormat.RGBX_8888:
      case PixelFormat.RGB_888:
          return 8;

      case PixelFormat.RGB_332:
          return 2;

      case PixelFormat.RGB_565:
          return 5;

      // Non-RGB formats.
      case PixelFormat.A_8:
      case PixelFormat.LA_88:
      case PixelFormat.L_8:
          return 0;

      // Unknown format. Use 8 as a sensible default.
      default:
          return 8;
      }
  }

  /**
   * @return A scaling factor for the Density Independent Pixel unit.
   *         1.0 is 160dpi, 0.75 is 120dpi, 2.0 is 320dpi.
   */
  @CalledByNative
  public double getDIPScale() {
      return getMetrics().density;
  }

  private Display getDisplay() {
      return mWinManager.getDefaultDisplay();
  }

  private DisplayMetrics getMetrics() {
      return mAppContext.getResources().getDisplayMetrics();
  }

  /**
   * Creates DeviceDisplayInfo for a given Context.
   * @param context A context to use.
   * @return DeviceDisplayInfo associated with a given Context.
   */
  @CalledByNative
  public static DeviceDisplayInfo create(Context context) {
      return new DeviceDisplayInfo(context);
  }
}
