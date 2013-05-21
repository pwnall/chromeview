// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

import android.content.Context;
import android.telephony.TelephonyManager;

import org.chromium.base.CalledByNative;

/**
 * This class facilitates access to the current telephony region,
 * typically only available using the Java SDK.
 */
public class DeviceTelephonyInfo {

  private TelephonyManager mTelManager;

  private DeviceTelephonyInfo(Context context) {
      Context appContext = context.getApplicationContext();
      mTelManager = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
  }

  /**
   * @return The ISO country code equivalent of the current MCC.
   */
  @CalledByNative
  public String getNetworkCountryIso() {
      return mTelManager.getNetworkCountryIso();
  }

  /**
   * Creates DeviceTelephonyInfo for a given Context.
   * @param context A context to use.
   * @return DeviceTelephonyInfo associated with a given Context.
   */
  @CalledByNative
  public static DeviceTelephonyInfo create(Context context) {
      return new DeviceTelephonyInfo(context);
  }
}
