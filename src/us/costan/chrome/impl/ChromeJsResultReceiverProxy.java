package us.costan.chrome.impl;

import org.chromium.android_webview.JsResultReceiver;

import us.costan.chrome.ChromeJsResult;

/**
 * Proxies from android_webkit's JsResultReceiver to JsResult.
 *
 * @hide
 */
public class ChromeJsResultReceiverProxy implements
    ChromeJsResult.ResultReceiver {

  /** The proxy target. */
  private JsResultReceiver target_;

  public ChromeJsResultReceiverProxy(JsResultReceiver target) {
    target_ = target;
  }
  @Override
  public void onJsResultComplete(ChromeJsResult result) {
    if (result.getResult()) {
      target_.confirm();
    } else {
      target_.cancel();
    }
  }
}
