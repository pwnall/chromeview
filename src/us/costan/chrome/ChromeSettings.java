package us.costan.chrome;

import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebSettings.ZoomDensity;

/**
 * ChromeView equivalent of WebSettings.
 *
 * @see android.webkit.WebSettings
 */
public abstract class ChromeSettings {
  /**
   * Sets whether the WebView should support zooming using its on-screen zoom
   * controls and gestures. The particular zoom mechanisms that should be used
   * can be set with {@link #setBuiltInZoomControls}. This setting does not
   * affect zooming performed using the {@link WebView#zoomIn()} and
   * {@link WebView#zoomOut()} methods. The default is true.
   *
   * @param support whether the WebView should support zoom
   */
  public abstract void setSupportZoom(boolean support);

  /**
   * Gets whether the WebView supports zoom.
   *
   * @return true if the WebView supports zoom
   * @see #setSupportZoom
   */
  public abstract boolean supportZoom();

  /**
   * Sets whether the WebView requires a user gesture to play media.
   * The default is true.
   *
   * @param require whether the WebView requires a user gesture to play media
   */
  public abstract void setMediaPlaybackRequiresUserGesture(boolean require);

  /**
   * Gets whether the WebView requires a user gesture to play media.
   *
   * @return true if the WebView requires a user gesture to play media
   * @see #setMediaPlaybackRequiresUserGesture
   */
  public abstract boolean getMediaPlaybackRequiresUserGesture();

  /**
   * Sets whether the WebView should use its built-in zoom mechanisms. The
   * built-in zoom mechanisms comprise on-screen zoom controls, which are
   * displayed over the WebView's content, and the use of a pinch gesture to
   * control zooming. Whether or not these on-screen controls are displayed
   * can be set with {@link #setDisplayZoomControls}. The default is false.
   * <p>
   * The built-in mechanisms are the only currently supported zoom
   * mechanisms, so it is recommended that this setting is always enabled.
   *
   * @param enabled whether the WebView should use its built-in zoom mechanisms
   */
  // This method was intended to select between the built-in zoom mechanisms
  // and the separate zoom controls. The latter were obtained using
  // {@link WebView#getZoomControls}, which is now hidden.
  public abstract void setBuiltInZoomControls(boolean enabled);

  /**
   * Gets whether the zoom mechanisms built into WebView are being used.
   *
   * @return true if the zoom mechanisms built into WebView are being used
   * @see #setBuiltInZoomControls
   */
  public abstract boolean getBuiltInZoomControls();

  /**
   * Sets whether the WebView should display on-screen zoom controls when
   * using the built-in zoom mechanisms. See {@link #setBuiltInZoomControls}.
   * The default is true.
   *
   * @param enabled whether the WebView should display on-screen zoom controls
   */
  public abstract void setDisplayZoomControls(boolean enabled);

  /**
   * Gets whether the WebView displays on-screen zoom controls when using
   * the built-in zoom mechanisms.
   *
   * @return true if the WebView displays on-screen zoom controls when using
   *         the built-in zoom mechanisms
   * @see #setDisplayZoomControls
   */
  public abstract boolean getDisplayZoomControls();

  /**
   * Enables or disables file access within WebView. File access is enabled by
   * default.  Note that this enables or disables file system access only.
   * Assets and resources are still accessible using file:///android_asset and
   * file:///android_res.
   */
  public abstract void setAllowFileAccess(boolean allow);

  /**
   * Gets whether this WebView supports file access.
   *
   * @see #setAllowFileAccess
   */
  public abstract boolean getAllowFileAccess();

  /**
   * Enables or disables content URL access within WebView.  Content URL
   * access allows WebView to load content from a content provider installed
   * in the system. The default is enabled.
   */
  public abstract void setAllowContentAccess(boolean allow);

  /**
   * Gets whether this WebView supports content URL access.
   *
   * @see #setAllowContentAccess
   */
  public abstract boolean getAllowContentAccess();

  /**
   * Sets whether the WebView loads pages in overview mode. The default is
   * false.
   */
  public abstract void setLoadWithOverviewMode(boolean overview);

  /**
   * Gets whether this WebView loads pages in overview mode.
   *
   * @return whether this WebView loads pages in overview mode
   * @see #setLoadWithOverviewMode
   */
  public abstract boolean getLoadWithOverviewMode();

  /**
   * Sets whether the WebView should save form data. The default is true,
   * unless in private browsing mode, when the value is always false.
   */
  public abstract void setSaveFormData(boolean save);

  /**
   * Gets whether the WebView saves form data. Always false in private
   * browsing mode.
   *
   * @return whether the WebView saves form data
   * @see #setSaveFormData
   */
  public abstract boolean getSaveFormData();

  /**
   * Sets whether the WebView should save passwords. The default is true.
   */
  public abstract void setSavePassword(boolean save);

  /**
   * Gets whether the WebView saves passwords.
   *
   * @return whether the WebView saves passwords
   * @see #setSavePassword
   */
  public abstract boolean getSavePassword();

  /**
   * Sets the text zoom of the page in percent. The default is 100.
   *
   * @param textZoom the text zoom in percent
   */
  public abstract void setTextZoom(int textZoom);

  /**
   * Gets the text zoom of the page in percent.
   *
   * @return the text zoom of the page in percent
   * @see #setTextZoom
   */
  public abstract int getTextZoom();

  /**
   * Sets the default zoom density of the page. This must be called from the UI
   * thread. The default is {@link ZoomDensity#MEDIUM}.
   *
   * @param zoom the zoom density
   */
  public abstract void setDefaultZoom(ZoomDensity zoom);

  /**
   * Gets the default zoom density of the page. This should be called from
   * the UI thread.
   *
   * @return the zoom density
   * @see #setDefaultZoom
   */
  public abstract ZoomDensity getDefaultZoom();

  /**
   * Enables using light touches to make a selection and activate mouseovers.
   * The default is false.
   */
  public abstract void setLightTouchEnabled(boolean enabled);

  /**
   * Gets whether light touches are enabled.
   *
   * @return whether light touches are enabled
   * @see #setLightTouchEnabled
   */
  public abstract boolean getLightTouchEnabled();

  /**
   * Tells the WebView to use a wide viewport. The default is false.
   *
   * @param use whether to use a wide viewport
   */
  public abstract void setUseWideViewPort(boolean use);

  /**
   * Gets whether the WebView is using a wide viewport.
   *
   * @return true if the WebView is using a wide viewport
   * @see #setUseWideViewPort
   */
  public abstract boolean getUseWideViewPort();

  /**
   * Sets whether the WebView whether supports multiple windows. If set to
   * true, {@link WebChromeClient#onCreateWindow} must be implemented by the
   * host application. The default is false.
   *
   * @param support whether to suport multiple windows
   */
  public abstract void setSupportMultipleWindows(boolean support);

  /**
   * Gets whether the WebView supports multiple windows.
   *
   * @return true if the WebView supports multiple windows
   * @see #setSupportMultipleWindows
   */
  public abstract boolean supportMultipleWindows();

  /**
   * Sets the underlying layout algorithm. This will cause a relayout of the
   * WebView. The default is {@link LayoutAlgorithm#NARROW_COLUMNS}.
   *
   * @param l the layout algorithm to use, as a {@link LayoutAlgorithm} value
   */
  public abstract void setLayoutAlgorithm(LayoutAlgorithm l);

  /**
   * Gets the current layout algorithm.
   *
   * @return the layout algorithm in use, as a {@link LayoutAlgorithm} value
   * @see #setLayoutAlgorithm
   */
  public abstract LayoutAlgorithm getLayoutAlgorithm();

  /**
   * Sets the standard font family name. The default is "sans-serif".
   *
   * @param font a font family name
   */
  public abstract void setStandardFontFamily(String font);

  /**
   * Gets the standard font family name.
   *
   * @return the standard font family name as a string
   * @see #setStandardFontFamily
   */
  public abstract String getStandardFontFamily();

  /**
   * Sets the fixed font family name. The default is "monospace".
   *
   * @param font a font family name
   */
  public abstract void setFixedFontFamily(String font);

  /**
   * Gets the fixed font family name.
   *
   * @return the fixed font family name as a string
   * @see #setFixedFontFamily
   */
  public abstract String getFixedFontFamily();

  /**
   * Sets the sans-serif font family name. The default is "sans-serif".
   *
   * @param font a font family name
   */
  public abstract void setSansSerifFontFamily(String font);

  /**
   * Gets the sans-serif font family name.
   *
   * @return the sans-serif font family name as a string
   * @see #setSansSerifFontFamily
   */
  public abstract String getSansSerifFontFamily();

  /**
   * Sets the serif font family name. The default is "sans-serif".
   *
   * @param font a font family name
   */
  public abstract void setSerifFontFamily(String font);

  /**
   * Gets the serif font family name. The default is "serif".
   *
   * @return the serif font family name as a string
   * @see #setSerifFontFamily
   */
  public abstract String getSerifFontFamily();

  /**
   * Sets the cursive font family name. The default is "cursive".
   *
   * @param font a font family name
   */
  public abstract void setCursiveFontFamily(String font);

  /**
   * Gets the cursive font family name.
   *
   * @return the cursive font family name as a string
   * @see #setCursiveFontFamily
   */
  public abstract String getCursiveFontFamily();

  /**
   * Sets the fantasy font family name. The default is "fantasy".
   *
   * @param font a font family name
   */
  public abstract void setFantasyFontFamily(String font);

  /**
   * Gets the fantasy font family name.
   *
   * @return the fantasy font family name as a string
   * @see #setFantasyFontFamily
   */
  public abstract String getFantasyFontFamily();

  /**
   * Sets the minimum font size. The default is 8.
   *
   * @param size a non-negative integer between 1 and 72. Any number outside
   *             the specified range will be pinned.
   */
  public abstract void setMinimumFontSize(int size);

  /**
   * Gets the minimum font size.
   *
   * @return a non-negative integer between 1 and 72
   * @see #setMinimumFontSize
   */
  public abstract int getMinimumFontSize();

  /**
   * Sets the minimum logical font size. The default is 8.
   *
   * @param size a non-negative integer between 1 and 72. Any number outside
   *             the specified range will be pinned.
   */
  public abstract void setMinimumLogicalFontSize(int size);

  /**
   * Gets the minimum logical font size.
   *
   * @return a non-negative integer between 1 and 72
   * @see #setMinimumLogicalFontSize
   */
  public abstract int getMinimumLogicalFontSize();

  /**
   * Sets the default font size. The default is 16.
   *
   * @param size a non-negative integer between 1 and 72. Any number outside
   *             the specified range will be pinned.
   */
  public abstract void setDefaultFontSize(int size);

  /**
   * Gets the default font size.
   *
   * @return a non-negative integer between 1 and 72
   * @see #setDefaultFontSize
   */
  public abstract int getDefaultFontSize();

  /**
   * Sets the default fixed font size. The default is 16.
   *
   * @param size a non-negative integer between 1 and 72. Any number outside
   *             the specified range will be pinned.
   */
  public abstract void setDefaultFixedFontSize(int size);

  /**
   * Gets the default fixed font size.
   *
   * @return a non-negative integer between 1 and 72
   * @see #setDefaultFixedFontSize
   */
  public abstract int getDefaultFixedFontSize();

  /**
   * Sets whether the WebView should load image resources. Note that this method
   * controls loading of all images, including those embedded using the data
   * URI scheme. Use {@link #setBlockNetworkImage} to control loading only
   * of images specified using network URI schemes. Note that if the value of this
   * setting is changed from false to true, all images resources referenced
   * by content currently displayed by the WebView are loaded automatically.
   * The default is true.
   *
   * @param flag whether the WebView should load image resources
   */
  public abstract void setLoadsImagesAutomatically(boolean flag);

  /**
   * Gets whether the WebView loads image resources. This includes
   * images embedded using the data URI scheme.
   *
   * @return true if the WebView loads image resources
   * @see #setLoadsImagesAutomatically
   */
  public abstract boolean getLoadsImagesAutomatically();

  /**
   * Sets whether the WebView should not load image resources from the
   * network (resources accessed via http and https URI schemes).  Note
   * that this method has no effect unless
   * {@link #getLoadsImagesAutomatically} returns true. Also note that
   * disabling all network loads using {@link #setBlockNetworkLoads}
   * will also prevent network images from loading, even if this flag is set
   * to false. When the value of this setting is changed from true to false,
   * network images resources referenced by content currently displayed by
   * the WebView are fetched automatically. The default is false.
   *
   * @param flag whether the WebView should not load image resources from the
   *             network
   * @see #setBlockNetworkLoads
   */
  public abstract void setBlockNetworkImage(boolean flag);

  /**
   * Gets whether the WebView does not load image resources from the network.
   *
   * @return true if the WebView does not load image resources from the network
   * @see #setBlockNetworkImage
   */
  public abstract boolean getBlockNetworkImage();

  /**
   * Sets whether the WebView should not load resources from the network.
   * Use {@link #setBlockNetworkImage} to only avoid loading
   * image resources. Note that if the value of this setting is
   * changed from true to false, network resources referenced by content
   * currently displayed by the WebView are not fetched until
   * {@link android.webkit.WebView#reload} is called.
   * If the application does not have the
   * {@link android.Manifest.permission#INTERNET} permission, attempts to set
   * a value of false will cause a {@link java.lang.SecurityException}
   * to be thrown. The default value is false if the application has the
   * {@link android.Manifest.permission#INTERNET} permission, otherwise it is
   * true.
   *
   * @param flag whether the WebView should not load any resources from the
   *             network
   * @see android.webkit.WebView#reload
   */
  public abstract void setBlockNetworkLoads(boolean flag);

  /**
   * Gets whether the WebView does not load any resources from the network.
   *
   * @return true if the WebView does not load any resources from the network
   * @see #setBlockNetworkLoads
   */
  public abstract boolean getBlockNetworkLoads();

  /**
   * Tells the WebView to enable JavaScript execution.
   * <b>The default is false.</b>
   *
   * @param flag true if the WebView should execute JavaScript
   */
  public abstract void setJavaScriptEnabled(boolean flag);

  /**
   * Sets whether JavaScript running in the context of a file scheme URL
   * should be allowed to access content from any origin. This includes
   * access to content from other file scheme URLs. See
   * {@link #setAllowFileAccessFromFileURLs}. To enable the most restrictive,
   * and therefore secure policy, this setting should be disabled.
   * <p>
   * The default value is true for API level
   * {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH_MR1} and below,
   * and false for API level {@link android.os.Build.VERSION_CODES#JELLY_BEAN}
   * and above.
   *
   * @param flag whether JavaScript running in the context of a file scheme
   *             URL should be allowed to access content from any origin
   */
  public abstract void setAllowUniversalAccessFromFileURLs(boolean flag);

  /**
   * Sets whether JavaScript running in the context of a file scheme URL
   * should be allowed to access content from other file scheme URLs. To
   * enable the most restrictive, and therefore secure policy, this setting
   * should be disabled. Note that the value of this setting is ignored if
   * the value of {@link #getAllowUniversalAccessFromFileURLs} is true.
   * <p>
   * The default value is true for API level
   * {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH_MR1} and below,
   * and false for API level {@link android.os.Build.VERSION_CODES#JELLY_BEAN}
   * and above.
   *
   * @param flag whether JavaScript running in the context of a file scheme
   *             URL should be allowed to access content from other file
   *             scheme URLs
   */
  public abstract void setAllowFileAccessFromFileURLs(boolean flag);

  /**
   * Tells the WebView to enable, disable, or have plugins on demand. On
   * demand mode means that if a plugin exists that can handle the embedded
   * content, a placeholder icon will be shown instead of the plugin. When
   * the placeholder is clicked, the plugin will be enabled. The default is
   * {@link PluginState#OFF}.
   *
   * @param state a PluginState value
   */
  public abstract void setPluginState(PluginState state);

  /**
   * Sets the path to where database storage API databases should be saved.
   * In order for the database storage API to function correctly, this method
   * must be called with a path to which the application can write. This
   * method should only be called once: repeated calls are ignored.
   *
   * @param databasePath a path to the directory where databases should be
   *                     saved.
   */
  // This will update WebCore when the Sync runs in the C++ side.
  // Note that the WebCore Database Tracker only allows the path to be set
  // once.
  public abstract void setDatabasePath(String databasePath);

  /**
   * Sets the path where the Geolocation databases should be saved. In order
   * for Geolocation permissions and cached positions to be persisted, this
   * method must be called with a path to which the application can write.
   *
   * @param databasePath a path to the directory where databases should be
   *                     saved.
   */
  // This will update WebCore when the Sync runs in the C++ side.
  public abstract void setGeolocationDatabasePath(String databasePath);

  /**
   * Sets whether the Application Caches API should be enabled. The default
   * is false. Note that in order for the Application Caches API to be
   * enabled, a valid database path must also be supplied to
   * {@link #setAppCachePath}.
   *
   * @param flag true if the WebView should enable Application Caches
   */
  public abstract void setAppCacheEnabled(boolean flag);

  /**
   * Sets the path to the Application Caches files. In order for the
   * Application Caches API to be enabled, this method must be called with a
   * path to which the application can write. This method should only be
   * called once: repeated calls are ignored.
   *
   * @param appCachePath a String path to the directory containing
   *                     Application Caches files.
   * @see setAppCacheEnabled
   */
  public abstract void setAppCachePath(String appCachePath);

  /**
   * Sets the maximum size for the Application Cache content. The passed size
   * will be rounded to the nearest value that the database can support, so
   * this should be viewed as a guide, not a hard limit. Setting the
   * size to a value less than current database size does not cause the
   * database to be trimmed. The default size is {@link Long#MAX_VALUE}.
   *
   * @param appCacheMaxSize the maximum size in bytes
   */
  public abstract void setAppCacheMaxSize(long appCacheMaxSize);

  /**
   * Sets whether the database storage API is enabled. The default value is
   * false. See also {@link #setDatabasePath} for how to correctly set up the
   * database storage API.
   *
   * @param flag true if the WebView should use the database storage API
   */
  public abstract void setDatabaseEnabled(boolean flag);

  /**
   * Sets whether the DOM storage API is enabled. The default value is false.
   *
   * @param flag true if the WebView should use the DOM storage API
   */
  public abstract void setDomStorageEnabled(boolean flag);

  /**
   * Gets whether the DOM Storage APIs are enabled.
   *
   * @return true if the DOM Storage APIs are enabled
   * @see #setDomStorageEnabled
   */
  public abstract boolean getDomStorageEnabled();
  /**
   * Gets the path to where database storage API databases are saved.
   *
   * @return the String path to the database storage API databases
   * @see #setDatabasePath
   */
  public abstract String getDatabasePath();

  /**
   * Gets whether the database storage API is enabled.
   *
   * @return true if the database storage API is enabled
   * @see #setDatabaseEnabled
   */
  public abstract boolean getDatabaseEnabled();

  /**
   * Sets whether Geolocation is enabled. The default is true. See also
   * {@link #setGeolocationDatabasePath} for how to correctly set up
   * Geolocation.
   *
   * @param flag whether Geolocation should be enabled
   */
  public abstract void setGeolocationEnabled(boolean flag);

  /**
   * Gets whether JavaScript is enabled.
   *
   * @return true if JavaScript is enabled
   * @see #setJavaScriptEnabled
   */
  public abstract boolean getJavaScriptEnabled();

  /**
   * Gets whether JavaScript running in the context of a file scheme URL can
   * access content from any origin. This includes access to content from
   * other file scheme URLs.
   *
   * @return whether JavaScript running in the context of a file scheme URL
   *         can access content from any origin
   * @see #setAllowUniversalAccessFromFileURLs
   */
  public abstract boolean getAllowUniversalAccessFromFileURLs();

  /**
   * Gets whether JavaScript running in the context of a file scheme URL can
   * access content from other file scheme URLs.
   *
   * @return whether JavaScript running in the context of a file scheme URL
   *         can access content from other file scheme URLs
   * @see #setAllowFileAccessFromFileURLs
   */
  public abstract boolean getAllowFileAccessFromFileURLs();

  /**
   * Gets the current state regarding whether plugins are enabled.
   *
   * @return the plugin state as a {@link PluginState} value
   * @see #setPluginState
   */
  public abstract PluginState getPluginState();

  /**
   * Tells JavaScript to open windows automatically. This applies to the
   * JavaScript function window.open(). The default is false.
   *
   * @param flag true if JavaScript can open windows automatically
   */
  public abstract void setJavaScriptCanOpenWindowsAutomatically(boolean flag);

  /**
   * Gets whether JavaScript can open windows automatically.
   *
   * @return true if JavaScript can open windows automatically during
   *         window.open()
   * @see #setJavaScriptCanOpenWindowsAutomatically
   */
  public abstract boolean getJavaScriptCanOpenWindowsAutomatically();
  /**
   * Sets the default text encoding name to use when decoding html pages.
   * The default is "Latin-1".
   *
   * @param encoding the text encoding name
   */
  public abstract void setDefaultTextEncodingName(String encoding);

  /**
   * Gets the default text encoding name.
   *
   * @return the default text encoding name as a string
   * @see #setDefaultTextEncodingName
   */
  public abstract String getDefaultTextEncodingName();

  /**
   * Sets the WebView's user-agent string. If the string is null or empty,
   * the system default value will be used.
   */
  public abstract void setUserAgentString(String ua);

  /**
   * Gets the WebView's user-agent string.
   *
   * @return the WebView's user-agent string
   * @see #setUserAgentString
   */
  public abstract String getUserAgentString();

  /**
   * Tells the WebView whether it needs to set a node to have focus when
   * {@link WebView#requestFocus(int, android.graphics.Rect)} is called. The
   * default value is true.
   *
   * @param flag whether the WebView needs to set a node
   */
  public abstract void setNeedInitialFocus(boolean flag);

  /**
   * Sets the priority of the Render thread. Unlike the other settings, this
   * one only needs to be called once per process. The default value is
   * {@link RenderPriority#NORMAL}.
   *
   * @param priority the priority
   */
  public abstract void setRenderPriority(RenderPriority priority);

  /**
   * Overrides the way the cache is used. The way the cache is used is based
   * on the navigation type. For a normal page load, the cache is checked
   * and content is re-validated as needed. When navigating back, content is
   * not revalidated, instead the content is just retrieved from the cache.
   * This method allows the client to override this behavior by specifying
   * one of {@link #LOAD_DEFAULT}, {@link #LOAD_NORMAL},
   * {@link #LOAD_CACHE_ELSE_NETWORK}, {@link #LOAD_NO_CACHE} or
   * {@link #LOAD_CACHE_ONLY}. The default value is {@link #LOAD_DEFAULT}.
   *
   * @param mode the mode to use
   */
  public abstract void setCacheMode(int mode);

  /**
   * Gets the current setting for overriding the cache mode.
   *
   * @return the current setting for overriding the cache mode
   * @see #setCacheMode
   */
  public abstract int getCacheMode();
}
