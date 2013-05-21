package us.costan.chrome.impl;

import us.costan.chrome.ChromeSslErrorHandler;
import android.webkit.ValueCallback;

/**
 * Proxies from ChromeSslErrorHandler to ValueCallback<Boolean>.
 *
 * @hide
 */
public class ChromeSslErrorHandlerProxy implements ChromeSslErrorHandler {
  /** The proxy target. */
  private ValueCallback<Boolean> target_;

  public ChromeSslErrorHandlerProxy(ValueCallback<Boolean> target) {
    target_ = target;
  }
  @Override
  public void cancel() {
    target_.onReceiveValue(false);
  }
  @Override
  public void proceed() {
    target_.onReceiveValue(true);
  }
}
