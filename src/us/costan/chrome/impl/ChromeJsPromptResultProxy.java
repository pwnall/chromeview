package us.costan.chrome.impl;

import org.chromium.android_webview.JsPromptResultReceiver;

import us.costan.chrome.ChromeJsPromptResult;
import us.costan.chrome.ChromeJsResult;

/**
 * Proxies from android_webkit's JsResultReceiver to JsPromptResult.
 *
 * @hide
 */
public class ChromeJsPromptResultProxy
    implements ChromeJsResult.ResultReceiver {

  /** The proxy target. */
  private JsPromptResultReceiver target_;

  public ChromeJsPromptResultProxy(JsPromptResultReceiver target) {
    target_ = target;
  }
  @Override
  public void onJsResultComplete(ChromeJsResult result) {
    ChromeJsPromptResult promptResult = (ChromeJsPromptResult)result;
    if (result.getResult()) {
      target_.confirm(promptResult.getStringResult());
    } else {
      target_.cancel();
    }
  }
}
