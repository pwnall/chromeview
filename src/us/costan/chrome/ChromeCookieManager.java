package us.costan.chrome;

import org.chromium.android_webview.AwCookieManager;

/**
 * ChromeView equivalent of WebView's CookieManager.
 *
 * @see android.webkit.CookieManager
 */
public class ChromeCookieManager {
  /** The class that's doing all the work. */
  private AwCookieManager awCookieManager_;

  private ChromeCookieManager() {
    awCookieManager_ = new AwCookieManager();
  }

  /**
   * Sets whether ChromeView instances should send and accept cookies.
   * @param accept whether ChromeView instances should send and accept cookies
   */
  public void setAcceptCookie(boolean accept) {
    awCookieManager_.setAcceptCookie(accept);
  }

  /**
   * Whether the application's ChromeView instances send and accept cookies.
   * @return true if ChromeView instances send and accept cookies for file
   *     scheme URLs
   */
  public boolean acceptCookie() {
    return awCookieManager_.acceptCookie();
  }

  /**
   * Sets a cookie for the given URL. Any existing cookie with the same host,
   * path and name will be replaced with the new cookie. The cookie being set
   * must not have expired and must not be a session cookie, otherwise it will
   * be ignored.
   *
   * @param url the URL for which cookie is set
   * @param value the cookie as a string, using the format of the 'Set-Cookie'
   *     HTTP header
   */
  public void setCookie(final String url, final String value) {
    awCookieManager_.setCookie(url, value);
  }

  /**
   * Gets the cookies for the given URL.
   *
   * @param url the URL for which the cookies are requested
   * @return the cookies as a string, using the format of the 'Cookie' HTTP
   *     header
   */
  public String getCookie(final String url) {
    return awCookieManager_.getCookie(url);
  }

  /**
   * Removes all session cookies, which are cookies without an expiration date.
   */
  public void removeSessionCookie() {
    awCookieManager_.removeSessionCookie();
  }

  /**
   * Removes all cookies.
   */
  public void removeAllCookie() {
    awCookieManager_.removeAllCookie();
  }

  /**
   * Gets whether there are stored cookies.
   *
   * @return true if there are stored cookies
   */
  public boolean hasCookies() {
     return awCookieManager_.hasCookies();
  }

  /**
   * Removes all expired cookies.
   */
  public void removeExpiredCookie() {
    awCookieManager_.removeExpiredCookie();
  }

  /**
   * Gets whether the ChromeView instances send and accept cookies for file
   * scheme URLs.
   *
   * @return true if WebView instances send and accept cookies for file scheme
   *     URLs
   */
  public boolean allowFileSchemeCookies() {
    return awCookieManager_.allowFileSchemeCookies();
  }

  /**
   * Sets whether the application's WebView instances should send and accept
   * cookies for file scheme URLs. Use of cookies with file scheme URLs is
   * potentially insecure. Do not use this feature unless you can be sure that
   * no unintentional sharing of cookie data can take place.
   *
   * Note that calls to this method will have no effect if made after a WebView
   * or CookieManager instance has been created.
   */
  public void setAcceptFileSchemeCookies(boolean accept) {
    awCookieManager_.setAcceptFileSchemeCookies(accept);
  }

  /** Gets the singleton ChromeCookieManager instance. */
  public synchronized static ChromeCookieManager getInstance() {
     if (instance_ == null) {
       instance_ = new ChromeCookieManager();
     }
     return instance_;
  }
  private static ChromeCookieManager instance_ = null;
}
