package us.costan.chrome.impl;

import org.chromium.android_webview.AwHttpAuthHandler;

import us.costan.chrome.ChromeHttpAuthHandler;

/**
 * Proxies from ChromeHttpAuthHandler to AwHttpAuthHandler.
 *
 * @hide
 */
public class ChromeHttpAuthHandlerProxy implements ChromeHttpAuthHandler {
  /** The proxy target. */
  private AwHttpAuthHandler target_;

  public ChromeHttpAuthHandlerProxy(AwHttpAuthHandler target) {
    target_ = target;
  }
  @Override
  public boolean useHttpAuthUsernamePassword() {
    return false;
  }
  @Override
  public void cancel() {
    target_.cancel();
  }
  @Override
  public void proceed(String username, String password) {
    target_.proceed(username, password);
  }
  @Override
  public boolean suppressDialog() {
    return false;
  }
}
