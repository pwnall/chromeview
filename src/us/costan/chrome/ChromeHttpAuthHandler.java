package us.costan.chrome;

/**
 * ChromeView equivalent of HttpAuthHandler.
 *
 * This is necessary because HttpAuthHandler's constructor is package-private,
 * so it is impossible to extend the class, which would have been a cleaner way
 * to proxy to AwHttpAuthHandler.
 *
 * @see android.webkit.HttpAuthHandler
 */
public interface ChromeHttpAuthHandler {
  // Mostly mirrors
  //    platform/frameworks/base/ ./core/java/android/webkit/HttpAuthHandler

  /**
   * @return True if we can use user credentials on record
   * (ie, if we did not fail trying to use them last time)
   */
  public boolean useHttpAuthUsernamePassword();

  /**
   * Cancel the authorization request.
   */
  public void cancel();

  /**
   * Proceed with the authorization with the given credentials.
   */
  public void proceed(String username, String password);

  /**
   * return true if the prompt dialog should be suppressed.
   * @hide
   */
  public boolean suppressDialog();
}
