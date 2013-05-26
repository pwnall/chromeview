package us.costan.chrome.impl;

import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwSettings;
import org.chromium.content.browser.ContentSettings;

import android.annotation.SuppressLint;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebSettings.ZoomDensity;
import us.costan.chrome.ChromeSettings;

/** Proxies between ChromeSettings and ContentsSettings / WebSettings. */
public class ChromeSettingsProxy extends ChromeSettings {
  /** The AwContents powering the ChromeView whose settings we're proxying. */
  private AwContents awContents_;

  /** ContentsSettings proxy target. */
  private ContentSettings contents_;

  /** WebSettings proxy target. */
  private AwSettings web_;

  public ChromeSettingsProxy(AwContents awContents) {
    awContents_ = awContents;
    contents_ = awContents_.getContentSettings();
    web_ = awContents_.getSettings();
  }

  @Override
  public void setSupportZoom(boolean support) {
    web_.setSupportZoom(support);
  }
  @Override
  public boolean supportZoom() {
    return web_.supportZoom();
  }
  @Override
  public void setMediaPlaybackRequiresUserGesture(boolean require) {
    web_.setMediaPlaybackRequiresUserGesture(require);
  }
  @Override
  public boolean getMediaPlaybackRequiresUserGesture() {
    return web_.getMediaPlaybackRequiresUserGesture();
  }
  @Override
  public void setBuiltInZoomControls(boolean enabled) {
    web_.setBuiltInZoomControls(enabled);
  }
  @Override
  public boolean getBuiltInZoomControls() {
    return web_.getBuiltInZoomControls();
  }
  @Override
  public void setDisplayZoomControls(boolean enabled) {
    web_.setDisplayZoomControls(enabled);
  }
  @Override
  public boolean getDisplayZoomControls() {
    return web_.getDisplayZoomControls();
  }
  @Override
  public void setAllowFileAccess(boolean allow) {
    web_.setAllowFileAccess(allow);
  }
  @Override
  public boolean getAllowFileAccess() {
    return web_.getAllowFileAccess();
  }
  @Override
  public void setAllowContentAccess(boolean allow) {
    web_.setAllowContentAccess(allow);
  }
  @Override
  public boolean getAllowContentAccess() {
    return web_.getAllowContentAccess();
  }
  @Override
  public void setLoadWithOverviewMode(boolean overview) {
    web_.setLoadWithOverviewMode(overview);
  }
  @Override
  public boolean getLoadWithOverviewMode() {
    return web_.getLoadWithOverviewMode();
  }
  @Override
  public void setSaveFormData(boolean save) {
    web_.setSaveFormData(save);
  }
  @Override
  public boolean getSaveFormData() {
    return web_.getSaveFormData();
  }
  @Override
  public void setSavePassword(boolean save) {
    // TODO Auto-generated method stub
  }
  @Override
  public boolean getSavePassword() {
    // TODO Auto-generated method stub
    return false;
  }
  @Override
  public void setTextZoom(int textZoom) {
    web_.setTextZoom(textZoom);
  }
  @Override
  public int getTextZoom() {
    return web_.getTextZoom();
  }
  @Override
  public void setDefaultZoom(ZoomDensity zoom) {
    // TODO Auto-generated method stub
  }
  @Override
  public ZoomDensity getDefaultZoom() {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public void setLightTouchEnabled(boolean enabled) {
    // TODO Auto-generated method stub
  }
  @Override
  public boolean getLightTouchEnabled() {
    // TODO Auto-generated method stub
    return false;
  }
  @Override
  public void setUseWideViewPort(boolean use) {
    web_.setUseWideViewPort(use);
  }
  @Override
  public boolean getUseWideViewPort() {
    return web_.getUseWideViewPort();
  }
  @Override
  public void setSupportMultipleWindows(boolean support) {
    web_.setSupportMultipleWindows(support);
  }
  @Override
  public boolean supportMultipleWindows() {
    return web_.supportMultipleWindows();
  }
  @Override
  public void setLayoutAlgorithm(LayoutAlgorithm l) {
    AwSettings.LayoutAlgorithm algorithm = AwSettings.LayoutAlgorithm.NORMAL;
    switch(l) {
    case NORMAL:
      algorithm = AwSettings.LayoutAlgorithm.NORMAL;
    case SINGLE_COLUMN:
      algorithm = AwSettings.LayoutAlgorithm.SINGLE_COLUMN;
    case NARROW_COLUMNS:
      algorithm = AwSettings.LayoutAlgorithm.NARROW_COLUMNS;
    }
    web_.setLayoutAlgorithm(algorithm);
  }
  @Override
  public LayoutAlgorithm getLayoutAlgorithm() {
    switch (web_.getLayoutAlgorithm()) {
    case NORMAL:
      return LayoutAlgorithm.NORMAL;
    case SINGLE_COLUMN:
      return LayoutAlgorithm.SINGLE_COLUMN;
    case NARROW_COLUMNS:
      return LayoutAlgorithm.NARROW_COLUMNS;
    case TEXT_AUTOSIZING:
      return LayoutAlgorithm.NORMAL;
    default:
      return LayoutAlgorithm.NORMAL;
    }
  }
  @Override
  public void setStandardFontFamily(String font) {
    web_.setStandardFontFamily(font);
  }
  @Override
  public String getStandardFontFamily() {
    return web_.getStandardFontFamily();
  }
  @Override
  public void setFixedFontFamily(String font) {
    web_.setFixedFontFamily(font);
  }
  @Override
  public String getFixedFontFamily() {
    return getFixedFontFamily();
  }
  @Override
  public void setSansSerifFontFamily(String font) {
    web_.setSansSerifFontFamily(font);
  }
  @Override
  public String getSansSerifFontFamily() {
    return web_.getSansSerifFontFamily();
  }
  @Override
  public void setSerifFontFamily(String font) {
    web_.setSerifFontFamily(font);
  }
  @Override
  public String getSerifFontFamily() {
    return web_.getSerifFontFamily();
  }
  @Override
  public void setCursiveFontFamily(String font) {
    web_.setCursiveFontFamily(font);
  }
  @Override
  public String getCursiveFontFamily() {
    return web_.getCursiveFontFamily();
  }
  @Override
  public void setFantasyFontFamily(String font) {
    web_.setFantasyFontFamily(font);
  }
  @Override
  public String getFantasyFontFamily() {
    return web_.getFantasyFontFamily();
  }
  @Override
  public void setMinimumFontSize(int size) {
    web_.setMinimumFontSize(size);
  }
  @Override
  public int getMinimumFontSize() {
    return web_.getMinimumFontSize();
  }
  @Override
  public void setMinimumLogicalFontSize(int size) {
    web_.setMinimumLogicalFontSize(size);
  }
  @Override
  public int getMinimumLogicalFontSize() {
    return web_.getMinimumLogicalFontSize();
  }
  @Override
  public void setDefaultFontSize(int size) {
    web_.setDefaultFontSize(size);
  }
  @Override
  public int getDefaultFontSize() {
    return web_.getDefaultFontSize();
  }
  @Override
  public void setDefaultFixedFontSize(int size) {
    web_.setDefaultFixedFontSize(size);
  }
  @Override
  public int getDefaultFixedFontSize() {
    return web_.getDefaultFixedFontSize();
  }
  @Override
  public void setLoadsImagesAutomatically(boolean flag) {
    web_.setLoadsImagesAutomatically(flag);
  }
  @Override
  public boolean getLoadsImagesAutomatically() {
    return web_.getLoadsImagesAutomatically();
  }
  @Override
  public void setBlockNetworkImage(boolean flag) {
    // TODO Auto-generated method stub
  }
  @Override
  public boolean getBlockNetworkImage() {
    // TODO Auto-generated method stub
    return false;
  }
  @Override
  public void setBlockNetworkLoads(boolean flag) {
    web_.setBlockNetworkLoads(flag);
  }
  @Override
  public boolean getBlockNetworkLoads() {
    return web_.getBlockNetworkLoads();
  }
  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public void setJavaScriptEnabled(boolean flag) {
    web_.setJavaScriptEnabled(flag);
  }
  @Override
  public void setAllowUniversalAccessFromFileURLs(boolean flag) {
    web_.setAllowUniversalAccessFromFileURLs(flag);
  }
  @Override
  public void setAllowFileAccessFromFileURLs(boolean flag) {
    web_.setAllowFileAccessFromFileURLs(flag);
  }
  @Override
  public void setPluginState(PluginState state) {
    web_.setPluginState(state);
  }
  @Override
  public void setDatabasePath(String databasePath) {
    // TODO Auto-generated method stub
  }
  @Override
  public void setGeolocationDatabasePath(String databasePath) {
    // TODO Auto-generated method stub
  }
  @Override
  public void setAppCacheEnabled(boolean flag) {
    web_.setAppCacheEnabled(flag);
  }
  @Override
  public void setAppCachePath(String appCachePath) {
    web_.setAppCachePath(appCachePath);
  }
  @Override
  public void setAppCacheMaxSize(long appCacheMaxSize) {
    // TODO Auto-generated method stub
  }
  @Override
  public void setDatabaseEnabled(boolean flag) {
    web_.setDatabaseEnabled(flag);
  }
  @Override
  public void setDomStorageEnabled(boolean flag) {
    web_.setDomStorageEnabled(flag);
  }
  @Override
  public boolean getDomStorageEnabled() {
    return web_.getDomStorageEnabled();
  }
  @Override
  public String getDatabasePath() {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public boolean getDatabaseEnabled() {
    return web_.getDatabaseEnabled();
  }
  @Override
  public void setGeolocationEnabled(boolean flag) {
    web_.setGeolocationEnabled(flag);
  }
  @Override
  public boolean getJavaScriptEnabled() {
    return contents_.getJavaScriptEnabled();
  }
  @Override
  public boolean getAllowUniversalAccessFromFileURLs() {
    return web_.getAllowUniversalAccessFromFileURLs();
  }
  @Override
  public boolean getAllowFileAccessFromFileURLs() {
    return web_.getAllowFileAccessFromFileURLs();
  }
  @Override
  public PluginState getPluginState() {
    return web_.getPluginState();
  }
  @Override
  public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
    web_.setJavaScriptCanOpenWindowsAutomatically(flag);
  }
  @Override
  public boolean getJavaScriptCanOpenWindowsAutomatically() {
    return web_.getJavaScriptCanOpenWindowsAutomatically();
  }
  @Override
  public void setDefaultTextEncodingName(String encoding) {
    web_.setDefaultTextEncodingName(encoding);
  }
  @Override
  public String getDefaultTextEncodingName() {
    return web_.getDefaultTextEncodingName();
  }
  @Override
  public void setUserAgentString(String ua) {
    web_.setUserAgentString(ua);
  }
  @Override
  public String getUserAgentString() {
    return web_.getUserAgentString();
  }
  @Override
  public void setNeedInitialFocus(boolean flag) {
    // TODO Auto-generated method stub
  }
  @Override
  public void setRenderPriority(RenderPriority priority) {
    // TODO Auto-generated method stub
  }
  @Override
  public void setCacheMode(int mode) {
    web_.setCacheMode(mode);
  }
  @Override
  public int getCacheMode() {
    return web_.getCacheMode();
  }
}
