package us.costan.chrome;

import java.util.Map;

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwContents;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.browser.ContentViewCore.JavaScriptCallback;

import us.costan.chrome.impl.ChromeAwContentsClientProxy;
import us.costan.chrome.impl.ChromeInitializer;
import us.costan.chrome.impl.ChromeSettingsProxy;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView.FindListener;
import android.widget.FrameLayout;

/** WebView-like layer. */
public class ChromeView extends FrameLayout {
  /** The closest thing to a WebView that Chromium has to offer. */
  private AwContents awContents_;

  /** Implements some of AwContents. */
  private ContentViewCore contentViewCore_;

  /** Glue that passes calls from the Chromium view to a WebChromeClient. */
  private ChromeAwContentsClientProxy awContentsClient_;

  /** Everything pertaining to the user's browsing session. */
  private AwBrowserContext browserContext_;

  /** Glue that passes calls from the Chromium view to its parent (us).  */
  private ChromeInternalAcccessAdapter internalAccessAdapter_;

  public ChromeView(Context context) {
    this(context, null);
  }

  /** Constructor for inflating via XML. */
  public ChromeView(Context context, AttributeSet attrs) {
    super(context, attrs, android.R.attr.webViewStyle);

    if (isInEditMode()) {
      return;  // Chromium isn't loaded in edit mode.
    }
    
    try {
      Activity activity = (Activity)context;
      activity.getWindow().setFlags(
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            
    } catch(ClassCastException e) {
      // Hope that hardware acceleration is enabled.
    }

    SharedPreferences sharedPreferences = context.getSharedPreferences(
        "chromeview", Context.MODE_PRIVATE);
    // TODO(pwnall): is there a better way to get an AwBrowserContext?
    browserContext_ = new AwBrowserContext(sharedPreferences);

    internalAccessAdapter_ = new ChromeView.ChromeInternalAcccessAdapter();
    awContentsClient_ = new ChromeAwContentsClientProxy(this);
    awContents_ = new AwContents(browserContext_, this, internalAccessAdapter_,
        awContentsClient_, true);
    contentViewCore_ = awContents_.getContentViewCore();
  }

  //// Non-WebView extensions

  /**
   * Injects the passed Javascript code in the current page and evaluates it.
   * If a result is required, pass in a callback.
   * Used in automation tests.
   *
   * @param script The Javascript to execute.
   * @param message The callback to be fired off when a result is ready. The script's
   *                result will be json encoded and passed as the parameter, and the call
   *                will be made on the main thread.
   *                If no result is required, pass null.
   * @throws IllegalStateException If the ContentView has been destroyed.
   */
  public void evaluateJavaScript(String script, JavaScriptCallback callback) {
    if (awContents_ != null) {
      contentViewCore_.evaluateJavaScript(script, callback);
    }
  }

  //// WebView methods

  /**
    * Return the first substring consisting of the address of a physical
    * location. Currently, only addresses in the United States are detected,
    * and consist of:
    * - a house number
    * - a street name
    * - a street type (Road, Circle, etc), either spelled out or abbreviated
    * - a city name
    * - a state or territory, either spelled out or two-letter abbr.
    * - an optional 5 digit or 9 digit zip code.
    *
    * All names must be correctly capitalized, and the zip code, if present,
    * must be valid for the state. The street type must be a standard USPS
    * spelling or abbreviation. The state or territory must also be spelled
    * or abbreviated using USPS standards. The house number may not exceed
    * five digits.
    * @param addr The string to search for addresses.
    *
    * @return the address, or if no address is found, return null.
    */
  public static String findAddress(String addr) {
    return ContentViewStatics.findAddress(addr);
  }

  /**
   * Sets the ChromeViewClient associated with this view.
   *
   * @param chromeViewClient the new handler; replaces the old handler
   * @see android.webkit.WebView#setWebViewClient(android.webkit.WebViewClient)
   * @see android.webkit.WebViewClient
   */
  public void setChromeViewClient(ChromeViewClient chromeViewClient) {
    awContentsClient_.setChromeViewClient(chromeViewClient);
  }

  /**
   * Sets the ChromeWebClient associated with this view.
   *
   * @param chromeWebClient the new handler; replaces the old handler
   * @see android.webkit.WebView#setWebChromeClient(android.webkit.WebChromeClient)
   * @see android.webkit.WebChromeClient
   */
  public void setChromeWebClient(ChromeWebClient chromeWebClient) {
    awContentsClient_.setChromeWebClient(chromeWebClient);
  }

  /**
   * Sets the DownloadListener associated with this view.
   *
   * @param downloadListener the new listener; replaces the old listener
   * @see android.webkit.WebView#setDownloadListener(DownloadListener)
   */
  public void setDownloadListener(DownloadListener downloadListener) {
    awContentsClient_.setDownloadListener(downloadListener);
  }

  /**
   * Sets the FindListener associated with this view.
   *
   * @param findListener the new listener; replaces the old listener
   * @see android.webkit.WebView#setFindListener(FindListener)
   */
 public void setFindListener(FindListener findListener) {
   awContentsClient_.setFindListener(findListener);
  }

  /**
   * Gets the SSL certificate for the main top-level page or null if there is
   * no certificate (the site is not secure).
   *
   * @return the SSL certificate for the main top-level page
   */
  public SslCertificate getCertificate() {
    return awContents_.getCertificate();
  }

  /**
   * Stores HTTP authentication credentials for a given host and realm. This
   * method is intended to be used with
   * {@link ChromeViewClient#onReceivedHttpAuthRequest(ChromeView, ChromeHttpAuthHandler, String, String)}
   *
   * @param host the host to which the credentials apply
   * @param realm the realm to which the credentials apply
   * @param username the username
   * @param password the password
   * @see getHttpAuthUsernamePassword
   * @see android.webkit.WebViewDatabase#hasHttpAuthUsernamePassword()
   * @see android.webkit.WebViewDatabase#clearHttpAuthUsernamePassword()
   */
  public void setHttpAuthUsernamePassword(String host, String realm,
      String username, String password) {
    awContents_.setHttpAuthUsernamePassword(host, realm, username, password);
  }

  /**
   * Retrieves HTTP authentication credentials for a given host and realm.
   * This method is intended to be used with
   * {@link ChromeViewClient#onReceivedHttpAuthRequest(ChromeView, ChromeHttpAuthHandler, String, String)}.
   *
   * @param host the host to which the credentials apply
   * @param realm the realm to which the credentials apply
   * @return the credentials as a String array, if found. The first element
   *    is the username and the second element is the password. Null if no
   *    credentials are found.
   * @see setHttpAuthUsernamePassword
   * @see android.webkit.WebViewDatabase#hasHttpAuthUsernamePassword()
   * @see android.webkit.WebViewDatabase#clearHttpAuthUsernamePassword()
   */
  public String[] getHttpAuthUsernamePassword(String host, String realm) {
      return awContents_.getHttpAuthUsernamePassword(host, realm);
  }

  /**
   * Saves the state of this WebView used in
   * {@link android.app.Activity#onSaveInstanceState}. Please note that this
   * method no longer stores the display data for this WebView. The previous
   * behavior could potentially leak files if {@link #restoreState} was never
   * called.
   *
   * @param outState the Bundle to store this WebView's state
   * @return false if saveState fails
   */
  public boolean saveState(Bundle outState) {
    return awContents_.saveState(outState);
  }

  /**
   * Restores the state of this WebView from the given Bundle. This method is
   * intended for use in {@link android.app.Activity#onRestoreInstanceState}
   * and should be called to restore the state of this WebView. If
   * it is called after this WebView has had a chance to build state (load
   * pages, create a back/forward list, etc.) there may be undesirable
   * side-effects. Please note that this method no longer restores the
   * display data for this WebView.
   *
   * @param inState the incoming Bundle of state
   * @return false if restoreState failed
   */
  public boolean restoreState(Bundle inState) {
      return awContents_.restoreState(inState);
  }

  /**
   * Loads the given URL with the specified additional HTTP headers.
   *
   * @param url the URL of the resource to load
   * @param additionalHttpHeaders the additional headers to be used in the
   *            HTTP request for this URL, specified as a map from name to
   *            value. Note that if this map contains any of the headers
   *            that are set by default by this WebView, such as those
   *            controlling caching, accept types or the User-Agent, their
   *            values may be overriden by this WebView's defaults.
   */
  public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
    LoadUrlParams loadUrlParams = new LoadUrlParams(url);
    loadUrlParams.setExtraHeaders(additionalHttpHeaders);
    awContents_.loadUrl(loadUrlParams);
  }

  /**
   * Loads the given URL.
   *
   * @param url the URL of the resource to load
   */
  public void loadUrl(String url) {
    awContents_.loadUrl(new LoadUrlParams(url));
  }

  /**
   * Loads the URL with postData using "POST" method into this WebView. If url
   * is not a network URL, it will be loaded with {link
   * {@link #loadUrl(String)} instead.
   *
   * @param url the URL of the resource to load
   * @param postData the data will be passed to "POST" request
   */
  public void postUrl(String url, byte[] postData) {
    awContents_.loadUrl(LoadUrlParams.createLoadHttpPostParams(url, postData));
  }

  /**
   * Loads the given data into this WebView using a 'data' scheme URL.
   * <p>
   * Note that JavaScript's same origin policy means that script running in a
   * page loaded using this method will be unable to access content loaded
   * using any scheme other than 'data', including 'http(s)'. To avoid this
   * restriction, use {@link
   * #loadDataWithBaseURL(String,String,String,String,String)
   * loadDataWithBaseURL()} with an appropriate base URL.
   * <p>
   * The encoding parameter specifies whether the data is base64 or URL
   * encoded. If the data is base64 encoded, the value of the encoding
   * parameter must be 'base64'. For all other values of the parameter,
   * including null, it is assumed that the data uses ASCII encoding for
   * octets inside the range of safe URL characters and use the standard %xx
   * hex encoding of URLs for octets outside that range. For example, '#',
   * '%', '\', '?' should be replaced by %23, %25, %27, %3f respectively.
   * <p>
   * The 'data' scheme URL formed by this method uses the default US-ASCII
   * charset. If you need need to set a different charset, you should form a
   * 'data' scheme URL which explicitly specifies a charset parameter in the
   * mediatype portion of the URL and call {@link #loadUrl(String)} instead.
   * Note that the charset obtained from the mediatype portion of a data URL
   * always overrides that specified in the HTML or XML document itself.
   *
   * @param data a String of data in the given encoding
   * @param mimeType the MIME type of the data, e.g. 'text/html'
   * @param encoding the encoding of the data
   */
  public void loadData(String data, String mimeType, String encoding) {
    LoadUrlParams loadUrlParams = LoadUrlParams.createLoadDataParams(data,
        mimeType, encoding.equals("base64"));
    awContents_.loadUrl(loadUrlParams);
  }

  /**
   * Loads the given data into this WebView, using baseUrl as the base URL for
   * the content. The base URL is used both to resolve relative URLs and when
   * applying JavaScript's same origin policy. The historyUrl is used for the
   * history entry.
   * <p>
   * Note that content specified in this way can access local device files
   * (via 'file' scheme URLs) only if baseUrl specifies a scheme other than
   * 'http', 'https', 'ftp', 'ftps', 'about' or 'javascript'.
   * <p>
   * If the base URL uses the data scheme, this method is equivalent to
   * calling {@link #loadData(String,String,String) loadData()} and the
   * historyUrl is ignored.
   *
   * @param baseUrl the URL to use as the page's base URL. If null defaults to
   *                'about:blank'.
   * @param data a String of data in the given encoding
   * @param mimeType the MIMEType of the data, e.g. 'text/html'. If null,
   *                 defaults to 'text/html'.
   * @param encoding the encoding of the data
   * @param historyUrl the URL to use as the history entry. If null defaults
   *                   to 'about:blank'.
   */
  public void loadDataWithBaseURL(String baseUrl, String data,
          String mimeType, String encoding, String historyUrl) {
      LoadUrlParams loadUrlParams =
          LoadUrlParams.createLoadDataParamsWithBaseUrl(data, mimeType,
          encoding.equals("base64"), baseUrl, historyUrl);
      awContents_.loadUrl(loadUrlParams);
  }

  /**
   * Saves the current view as a web archive.
   *
   * @param filename the filename where the archive should be placed
   */
  public void saveWebArchive(String filename) {
    ValueCallback<String> callback = new ValueCallback<String>() {
      @Override
      public void onReceiveValue(String value) { }
    };
    awContents_.saveWebArchive(filename, false, callback);
  }

  /**
   * Saves the current view as a web archive.
   *
   * @param basename the filename where the archive should be placed
   * @param autoname if false, takes basename to be a file. If true, basename
   *                 is assumed to be a directory in which a filename will be
   *                 chosen according to the URL of the current page.
   * @param callback called after the web archive has been saved. The
   *                 parameter for onReceiveValue will either be the filename
   *                 under which the file was saved, or null if saving the
   *                 file failed.
   */
  public void saveWebArchive(String basename, boolean autoname,
      ValueCallback<String> callback) {
    awContents_.saveWebArchive(basename, autoname, callback);
  }

  /**
   * Stops the current load.
   */
  public void stopLoading() {
    awContents_.stopLoading();
  }

  /**
   * Reloads the current URL.
   */
  public void reload() {
    awContents_.reload();
  }

  /**
   * Gets whether this WebView has a back history item.
   *
   * @return true iff this WebView has a back history item
   */
  public boolean canGoBack() {
    return awContents_.canGoBack();
  }

  /**
   * Goes back in the history of this WebView.
   */
  public void goBack() {
    awContents_.goBack();
  }

  /**
   * Gets whether this WebView has a forward history item.
   *
   * @return true iff this Webview has a forward history item
   */
  public boolean canGoForward() {
    return awContents_.canGoForward();
  }

  /**
   * Goes forward in the history of this WebView.
   */
  public void goForward() {
    awContents_.goForward();
  }

  /**
   * Gets whether the page can go back or forward the given
   * number of steps.
   *
   * @param steps the negative or positive number of steps to move the
   *              history
   */
  public boolean canGoBackOrForward(int steps) {
    return awContents_.canGoBackOrForward(steps);
  }

  /**
   * Goes to the history item that is the number of steps away from
   * the current item. Steps is negative if backward and positive
   * if forward.
   *
   * @param steps the number of steps to take back or forward in the back
   *              forward list
   */
  public void goBackOrForward(int steps) {
      awContents_.goBackOrForward(steps);
  }

  /**
   * Gets whether private browsing is enabled in this WebView.
   */
  public boolean isPrivateBrowsingEnabled() {
    return false;
  }

  /**
   * Scrolls the contents of this WebView up by half the view size.
   *
   * @param top true to jump to the top of the page
   * @return true if the page was scrolled
   */
  public boolean pageUp(boolean top) {
    return awContents_.pageUp(top);
  }

  /**
   * Scrolls the contents of this WebView down by half the page size.
   *
   * @param bottom true to jump to bottom of page
   * @return true if the page was scrolled
   */
  public boolean pageDown(boolean bottom) {
    return awContents_.pageDown(bottom);
  }

  /**
   * Gets a new picture that captures the current contents of this WebView.
   * The picture is of the entire document being displayed, and is not
   * limited to the area currently displayed by this WebView. Also, the
   * picture is a static copy and is unaffected by later changes to the
   * content being displayed.
   * <p>
   * Note that due to internal changes, for API levels between
   * {@link android.os.Build.VERSION_CODES#HONEYCOMB} and
   * {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH} inclusive, the
   * picture does not include fixed position elements or scrollable divs.
   *
   * @return a picture that captures the current contents of this WebView
   */
  public Picture capturePicture() {
    return awContents_.capturePicture();
  }

  /**
   * Sets the initial scale for this WebView. 0 means default. If
   * {@link WebSettings#getUseWideViewPort()} is true, it zooms out all the
   * way. Otherwise it starts with 100%. If initial scale is greater than 0,
   * WebView starts with this value as initial scale.
   * Please note that unlike the scale properties in the viewport meta tag,
   * this method doesn't take the screen density into account.
   *
   * @param scaleInPercent the initial scale in percent
   */
  public void setInitialScale(int scaleInPercent) {
    // TODO(pwnall): seems useful
  }

  /**
   * Invokes the graphical zoom picker widget for this WebView. This will
   * result in the zoom widget appearing on the screen to control the zoom
   * level of this WebView.
   */
  public void invokeZoomPicker() {
    awContents_.invokeZoomPicker();
  }

  /**
   * Requests the anchor or image element URL at the last tapped point.
   * If hrefMsg is null, this method returns immediately and does not
   * dispatch hrefMsg to its target. If the tapped point hits an image,
   * an anchor, or an image in an anchor, the message associates
   * strings in named keys in its data. The value paired with the key
   * may be an empty string.
   *
   * @param hrefMsg the message to be dispatched with the result of the
   *                request. The message data contains three keys. "url"
   *                returns the anchor's href attribute. "title" returns the
   *                anchor's text. "src" returns the image's src attribute.
   */
  public void requestFocusNodeHref(Message hrefMsg) {
    awContents_.requestFocusNodeHref(hrefMsg);
  }

  /**
   * Requests the URL of the image last touched by the user. msg will be sent
   * to its target with a String representing the URL as its object.
   *
   * @param msg the message to be dispatched with the result of the request
   *            as the data member with "url" as key. The result can be null.
   */
  public void requestImageRef(Message msg) {
    awContents_.requestImageRef(msg);
  }

  /**
   * Gets the URL for the current page. This is not always the same as the URL
   * passed to WebViewClient.onPageStarted because although the load for
   * that URL has begun, the current page may not have changed.
   *
   * @return the URL for the current page
   */
  public String getUrl() {
    return awContents_.getUrl();
  }

  /**
   * Gets the original URL for the current page. This is not always the same
   * as the URL passed to WebViewClient.onPageStarted because although the
   * load for that URL has begun, the current page may not have changed.
   * Also, there may have been redirects resulting in a different URL to that
   * originally requested.
   *
   * @return the URL that was originally requested for the current page
   */
  public String getOriginalUrl() {
    return awContents_.getOriginalUrl();
  }

  /**
   * Gets the title for the current page. This is the title of the current page
   * until WebViewClient.onReceivedTitle is called.
   *
   * @return the title for the current page
   */
  public String getTitle() {
    return awContents_.getTitle();
  }

  /**
   * Gets the favicon for the current page. This is the favicon of the current
   * page until WebViewClient.onReceivedIcon is called.
   *
   * @return the favicon for the current page
   */
  public Bitmap getFavicon() {
    return awContents_.getFavicon();
  }

  /**
   * Gets the height of the HTML content.
   *
   * @return the height of the HTML content
   */
  public int getContentHeight() {
    return awContents_.getContentHeightCss();
  }

  /**
   * Gets the width of the HTML content.
   *
   * @return the width of the HTML content
   * @hide
   */
  public int getContentWidth() {
    return awContents_.getContentWidthCss();
  }

  /**
   * Pauses all layout, parsing, and JavaScript timers for all WebViews. This
   * is a global requests, not restricted to just this WebView. This can be
   * useful if the application has been paused.
   */
  public void pauseTimers() {
    awContents_.pauseTimers();
  }

  /**
   * Resumes all layout, parsing, and JavaScript timers for all WebViews.
   * This will resume dispatching all timers.
   */
  public void resumeTimers() {
    awContents_.resumeTimers();
  }

  /**
   * Pauses any extra processing associated with this WebView and its
   * associated DOM, plugins, JavaScript etc. For example, if this WebView is
   * taken offscreen, this could be called to reduce unnecessary CPU or
   * network traffic. When this WebView is again "active", call onResume().
   * Note that this differs from pauseTimers(), which affects all WebViews.
   */
  public void onPause() {
    awContents_.onPause();
  }

  /**
   * Resumes a WebView after a previous call to onPause().
   */
  public void onResume() {
    awContents_.onResume();
  }

  /**
   * Gets whether this WebView is paused, meaning onPause() was called.
   * Calling onResume() sets the paused state back to false.
   *
   * @hide
   */
  public boolean isPaused() {
    return awContents_.isPaused();
  }

  /**
   * Clears the resource cache. Note that the cache is per-application, so
   * this will clear the cache for all WebViews used.
   *
   * @param includeDiskFiles if false, only the RAM cache is cleared
   */
  public void clearCache(boolean includeDiskFiles) {
    awContents_.clearCache(includeDiskFiles);
  }

  /**
   * Tells this WebView to clear its internal back/forward list.
   */
  public void clearHistory() {
    awContents_.clearHistory();
  }

  /**
   * Clears the SSL preferences table stored in response to proceeding with
   * SSL certificate errors.
   */
  public void clearSslPreferences() {
    awContents_.clearSslPreferences();
  }

  /**
   * Highlights and scrolls to the next match found by
   * {@link #findAllAsync}, wrapping around page boundaries as necessary.
   * Notifies any registered {@link FindListener}. If {@link #findAllAsync(String)}
   * has not been called yet, or if {@link #clearMatches} has been called since the
   * last find operation, this function does nothing.
   *
   * @param forward the direction to search
   * @see #setFindListener
   */
  public void findNext(boolean forward) {
    awContents_.findNext(forward);
  }

  /**
   * Finds all instances of find on the page and highlights them,
   * asynchronously. Notifies any registered {@link FindListener}.
   * Successive calls to this will cancel any pending searches.
   *
   * @param find the string to find.
   * @see #setFindListener
   */
  public void findAllAsync(String find) {
    awContents_.findAllAsync(find);
  }

  /**
   * Starts an ActionMode for finding text in this WebView.  Only works if this
   * WebView is attached to the view system.
   *
   * @param text if non-null, will be the initial text to search for.
   *             Otherwise, the last String searched for in this WebView will
   *             be used to start.
   * @param showIme if true, show the IME, assuming the user will begin typing.
   *                If false and text is non-null, perform a find all.
   * @return true if the find dialog is shown, false otherwise
   */
  public boolean showFindDialog(String text, boolean showIme) {
    // TODO(pwnall): seems useful
    return false;
  }

  /**
   * Clears the highlighting surrounding text matches created by
   * {@link #findAllAsync}.
   */
  public void clearMatches() {
    awContents_.clearMatches();
  }

  /**
   * Queries the document to see if it contains any image references. The
   * message object will be dispatched with arg1 being set to 1 if images
   * were found and 0 if the document does not reference any images.
   *
   * @param response the message that will be dispatched with the result
   */
  public void documentHasImages(Message response) {
    awContents_.documentHasImages(response);
  }

  /**
   * Injects the supplied Java object into this WebView. The object is
   * injected into the JavaScript context of the main frame, using the
   * supplied name. This allows the Java object's methods to be
   * accessed from JavaScript. Only public methods that are annotated with
   * {@link us.costan.chrome.ChromeJavascriptInterface} can be accessed from
   * JavaScript.
   * <p> Note that injected objects will not
   * appear in JavaScript until the page is next (re)loaded. For example:
   * <pre>
   * class JsObject {
   *    {@literal @}ChromeJavascriptInterface
   *    public String toString() { return "injectedObject"; }
   * }
   * webView.addJavascriptInterface(new JsObject(), "injectedObject");
   * webView.loadData("<!DOCTYPE html><title></title>", "text/html", null);
   * webView.loadUrl("javascript:alert(injectedObject.toString())");</pre>
   * <p>
   * <strong>IMPORTANT:</strong>
   * <ul>
   * <li> This method can be used to allow JavaScript to control the host
   * application. Use of this method in a WebView
   * containing untrusted content could allow an attacker to manipulate the
   * host application in unintended ways, executing Java code with the
   * permissions of the host application. Use extreme care when using this
   * method in a WebView which could contain untrusted content.</li>
   * <li> JavaScript interacts with Java object on a private, background
   * thread of this WebView. Care is therefore required to maintain thread
   * safety.</li>
   * <li> The Java object's fields are not accessible.</li>
   * </ul>
   *
   * @param object the Java object to inject into this WebView's JavaScript
   *               context. Null values are ignored.
   * @param name the name used to expose the object in JavaScript
   */
  public void addJavascriptInterface(Object object, String name) {
    awContents_.addPossiblyUnsafeJavascriptInterface(object, name,
        ChromeJavascriptInterface.class);
  }

  /**
   * Removes a previously injected Java object from this WebView. Note that
   * the removal will not be reflected in JavaScript until the page is next
   * (re)loaded. See {@link #addJavascriptInterface}.
   *
   * @param name the name used to expose the object in JavaScript
   */
  public void removeJavascriptInterface(String name) {
    awContents_.removeJavascriptInterface(name);
  }

  /**
   * Gets the ChromeSettings object used to control the settings for this
   * ChromeView.
   *
   * @return a ChromeSettings object that can be used to control this
   *      ChromeView's settings
   */
  public ChromeSettings getSettings() {
    return new ChromeSettingsProxy(awContents_);
  }

  public void flingScroll(int vx, int vy) {
    awContents_.flingScroll(vx, vy);
  }

  /**
   * Performs zoom in in this WebView.
   *
   * @return true if zoom in succeeds, false if no zoom changes
   */
  public boolean zoomIn() {
    return awContents_.zoomIn();
  }

  /**
   * Performs zoom out in this WebView.
   *
   * @return true if zoom out succeeds, false if no zoom changes
   */
  public boolean zoomOut() {
    return awContents_.zoomOut();
  }


  //// Methods outside WebView.

  /**
   * Sets up the Chromium libraries backing ChromeView.
   *
   * This should be called from {@link android.app.Application#onCreate()}.
   *
   * @param context Android context for the application using ChromeView
   */
  public static void initialize(Context context) {
    ChromeInitializer.initialize(context);
  }

  /**
   * The object that implements the WebView API.
   */
  public ContentViewCore getContentViewCore() {
    return contentViewCore_;
  }

  //// Forward a bunch of calls to the Chromium view.
  //// Lifted from chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/AwTestContainerView

  public void destroy() {
    awContents_.destroy();
  }

  @SuppressLint("WrongCall")
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (awContents_ != null) {
      awContents_.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
    }
  }
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (awContents_ != null) {
      awContents_.onSizeChanged(w, h, oldw, oldh);
    }
  }
  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (awContents_ != null) {
      awContents_.onAttachedToWindow();
    }
  }
  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    if (awContents_ != null) {
      awContents_.onConfigurationChanged(newConfig);
    } else {
      super.onConfigurationChanged(newConfig);
    }
  }
  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (awContents_ != null) {
      awContents_.onDetachedFromWindow();
    }
  }
  @SuppressLint("WrongCall")
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (awContents_ != null) {
      awContents_.onDraw(canvas);
    }
  }
  @Override
  protected void onFocusChanged(boolean gainFocus, int direction,
      Rect previouslyFocusedRect) {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    if (awContents_ != null) {
      awContents_.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }
  }
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (awContents_ != null) {
      return awContents_.onTouchEvent(event);
    } else {
      return super.onTouchEvent(event);
    }
  }
  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (awContents_ != null) {
      awContents_.onVisibilityChanged(changedView, visibility);
    }
  }
  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (awContents_ != null) {
      awContents_.onWindowVisibilityChanged(visibility);
    }
  }

  //// Forward a bunch more calls to the Chromium view.
  //// Lifted from
  ////     platform/frameworks/base ./core/java/android/webkit/WebKit.java

  @Override
  protected int computeHorizontalScrollRange() {
    if (awContents_ != null) {
      return awContents_.computeHorizontalScrollRange();
    } else {
      return super.computeHorizontalScrollRange();
    }
  }
  @Override
  protected int computeHorizontalScrollOffset() {
    if (awContents_ != null) {
      return awContents_.computeHorizontalScrollOffset();
    } else {
      return super.computeHorizontalScrollOffset();
    }
  }
  @Override
  protected int computeVerticalScrollRange() {
    if (awContents_ != null) {
      return awContents_.computeVerticalScrollRange();
    } else {
      return super.computeVerticalScrollRange();
    }
  }
  @Override
  protected int computeVerticalScrollOffset() {
    if (awContents_ != null) {
      return awContents_.computeVerticalScrollOffset();
    } else {
      return super.computeVerticalScrollOffset();
    }
  }
  @Override
  protected int computeVerticalScrollExtent() {
    if (awContents_ != null) {
      return awContents_.computeVerticalScrollExtent();
    } else {
      return super.computeVerticalScrollExtent();
    }
  }
  @Override
  public boolean onHoverEvent(MotionEvent event) {
    if (awContents_ != null) {
      return awContents_.onHoverEvent(event);
    } else {
      return super.onHoverEvent(event);
    }
  }
  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (awContents_ != null) {
      return awContents_.onGenericMotionEvent(event);
    } else {
      return super.onGenericMotionEvent(event);
    }
  }
  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (awContents_ != null) {
      return awContents_.onKeyUp(keyCode, event);
    } else {
      return super.onKeyUp(keyCode, event);
    }
  }
  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    if (awContents_ != null) {
      info.setClassName(ChromeView.class.getName());
      awContents_.onInitializeAccessibilityNodeInfo(info);
    }
  }
  @Override
  public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
    super.onInitializeAccessibilityEvent(event);
    if (awContents_ != null) {
      event.setClassName(ChromeView.class.getName());
      awContents_.onInitializeAccessibilityEvent(event);
    }
  }
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  @Override
  public boolean performAccessibilityAction(int action, Bundle arguments) {
    if (awContents_ != null) {
      // Lifted from content.browser.JellyBeanContentView
      if (contentViewCore_.supportsAccessibilityAction(action)) {
        return awContents_.performAccessibilityAction(action, arguments);
      }
    }
    return super.performAccessibilityAction(action, arguments);
  }
  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (awContents_ != null) {
      return awContents_.onCreateInputConnection(outAttrs);
    } else {
      return super.onCreateInputConnection(outAttrs);
    }
  }
  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    if (awContents_ != null) {
      awContents_.onWindowFocusChanged(hasWindowFocus);
    }
    super.onWindowFocusChanged(hasWindowFocus);
  }
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (awContents_ != null) {
      return awContents_.dispatchKeyEvent(event);
    } else {
      return super.dispatchKeyEvent(event);
    }
  }
  @Override
  public boolean dispatchKeyEventPreIme(KeyEvent event) {
    if (awContents_ != null) {
      return contentViewCore_.dispatchKeyEventPreIme(event);
    } else {
      return super.dispatchKeyEventPreIme(event);
    }
  }

  //// Forward calls that ContentViewCore responds to.

  @Override
  public boolean onCheckIsTextEditor() {
    if (awContents_ != null) {
      return contentViewCore_.onCheckIsTextEditor();
    } else {
      return super.onCheckIsTextEditor();
    }
  }
  @Override
  public void scrollTo(int x, int y) {
    super.scrollTo(x, y);
    if (awContents_ != null) {
      contentViewCore_.scrollTo(x, y);
    }
  }
  @Override
  public void scrollBy(int x, int y) {
    super.scrollBy(x, y);
    if (awContents_ != null) {
      contentViewCore_.scrollBy(x, y);
    }
  }
  @Override
  protected boolean awakenScrollBars(int startDelay, boolean invalidate) {
    if (awContents_ != null) {
      return contentViewCore_.awakenScrollBars(startDelay, invalidate);
    } else {
      return super.awakenScrollBars(startDelay, invalidate);
    }
  }

  /** Glue that passes calls from the Chromium view to its container (us). */
  private class ChromeInternalAcccessAdapter implements AwContents.InternalAccessDelegate {
    //// Lifted from chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/AwTestContainerView
    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
      return ChromeView.this.drawChild(canvas, child, drawingTime);
    }
    @Override
    public boolean super_onKeyUp(int keyCode, KeyEvent event) {
      return ChromeView.super.onKeyUp(keyCode, event);
    }
    @Override
    public boolean super_dispatchKeyEventPreIme(KeyEvent event) {
      return ChromeView.super.dispatchKeyEventPreIme(event);
    }
    @Override
    public boolean super_dispatchKeyEvent(KeyEvent event) {
      return ChromeView.super.dispatchKeyEvent(event);
    }
    @Override
    public boolean super_onGenericMotionEvent(MotionEvent event) {
      return ChromeView.super.onGenericMotionEvent(event);
    }
    @Override
    public void super_onConfigurationChanged(Configuration newConfig) {
      ChromeView.super.onConfigurationChanged(newConfig);
    }
    @Override
    public void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix) {
      ChromeView.this.onScrollChanged(lPix, tPix, oldlPix, oldtPix);
    }
    @Override
    public boolean awakenScrollBars() {
      return ChromeView.this.awakenScrollBars();
    }
    @Override
    public boolean super_awakenScrollBars(int startDelay, boolean invalidate) {
      return ChromeView.super.awakenScrollBars(startDelay, invalidate);
    }
    @Override
    public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
      ChromeView.this.setMeasuredDimension(measuredWidth, measuredHeight);
    }
    @Override
    public boolean requestDrawGL(Canvas canvas) {
      if (canvas != null) {
        if (canvas.isHardwareAccelerated()) {
          // TODO(pwnall): figure out what AwContents wants from us, and do it;
          //               most likely something to do with
          //               AwContents.getAwDrawGLFunction()
          return false;
        } else {
          return false;
        }
      } else {
        if (ChromeView.this.isHardwareAccelerated()) {
          // TODO(pwnall): figure out what AwContents wants from us, and do it;
          //               most likely something to do with
          //               AwContents.getAwDrawGLFunction()
          return false;
        } else {
          return false;
        }
      }
    }
  }
}
