package us.costan.chrome.impl;

import org.chromium.android_webview.AwContentsClient;
import org.chromium.android_webview.AwHttpAuthHandler;
import org.chromium.android_webview.InterceptedRequestData;
import org.chromium.android_webview.JsPromptResultReceiver;
import org.chromium.android_webview.JsResultReceiver;

import us.costan.chrome.ChromeJsResult;
import us.costan.chrome.ChromeView;
import us.costan.chrome.ChromeViewClient;
import us.costan.chrome.ChromeWebClient;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.GeolocationPermissions.Callback;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebView.FindListener;

/** Glue that passes calls from the Chromium view to a WebChromeClient. */
public class ChromeAwContentsClientProxy extends AwContentsClient {
  // Inspired from
  //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/NullContentsClient:w
  
  //     chromium/src/android_webview/javatests/src/org/chromium/android_webview/tests/*ContentsClient
  //     http://developer.android.com/reference/android/webkit/WebChromeClient.html

  /** The view whose clients are proxied by this instance. */
  private final ChromeView view_;

  /** ChromeView equivalent of WebViewClient. */
  private ChromeViewClient viewClient_;

  /** ChromeView equivalent of WebChromeClient. */
  private ChromeWebClient webClient_;

  /** Receives download notifications. */
  private DownloadListener downloadListener_;

  /** Receives find results notifications. */
  private FindListener findListener_;


  /** Resets the ChromeViewClient proxy target. */
  public void setChromeViewClient(ChromeViewClient chromeViewClient) {
    viewClient_ = chromeViewClient;
  }

  /** Resets the ChromeWebClient proxy target. */
  public void setChromeWebClient(ChromeWebClient chromeWebClient) {
    webClient_ = chromeWebClient;
  }

  /** Resets the DownloadListener proxy target. */
  public void setDownloadListener(DownloadListener downloadListener) {
    downloadListener_ = downloadListener;
  }

  /** Resets the FindListener proxy target. */
  public void setFindListener(FindListener findListener) {
    findListener_ = findListener;
  }

  /**
   * Creates a new proxy.
   *
   * @param chromeView The view whose clients are proxied by this instance.
   */
  public ChromeAwContentsClientProxy(ChromeView chromeView) {
    view_ = chromeView;
    viewClient_ = null;
    webClient_ = null;
  }

  @Override
  public void onHideCustomView() {
    // TODO Auto-generated method stub
  }

  @Override
  public Bitmap getDefaultVideoPoster() {
    // TODO Auto-generated method stub
    return null;
  }

  //// WebChromeClient inexact proxies.
  @Override
  protected void handleJsAlert(String url, String message,
      JsResultReceiver receiver) {
    if (webClient_ != null) {
      ChromeJsResult result = new ChromeJsResult(
          new ChromeJsResultReceiverProxy(receiver));
      if (webClient_.onJsAlert(view_, url, message, result)) {
        return;  // Alert will be handled by the client.
      }
    }
    receiver.cancel();  // Default alert handling.
  }
  @Override
  protected void handleJsBeforeUnload(String url, String message,
      JsResultReceiver receiver) {
    if (webClient_ != null) {
      ChromeJsResult result = new ChromeJsResult(
          new ChromeJsResultReceiverProxy(receiver));
      if (webClient_.onJsBeforeUnload(view_, url, message,
          result)) {
        return;  // Alert will be handled by the client.
      }
    }
    receiver.cancel();  // Default alert handling.
  }
  @Override
  protected void handleJsConfirm(String url, String message,
      JsResultReceiver receiver) {
    if (webClient_ != null) {
      ChromeJsResult result = new ChromeJsResult(
          new ChromeJsResultReceiverProxy(receiver));
      if (webClient_.onJsAlert(view_, url, message, result)) {
        return;  // Alert will be handled by the client.
      }
    }
    receiver.cancel();  // Default alert handling.
  }
  @Override
  protected void handleJsPrompt(String url, String message,
      String defaultValue, JsPromptResultReceiver receiver) {
    if (webClient_ != null) {
      ChromeJsResult result = new ChromeJsResult(
          new ChromeJsPromptResultProxy(receiver));
      if (webClient_.onJsAlert(view_, url, message, result)) {
        return;  // Alert will be handled by the client.
      }
    }
    receiver.cancel();  // Default alert handling.
  }

  //// WebChromeClient proxy methods.
  @Override
  public void onProgressChanged(int progress) {
    if (webClient_ != null)
      webClient_.onProgressChanged(view_, progress);
  }
  @Override
  public void onReceivedIcon(Bitmap bitmap) {
    if (webClient_ != null)
      webClient_.onReceivedIcon(view_, bitmap);
  }
  @Override
  public void onReceivedTouchIconUrl(String url, boolean precomposed) {
    if (webClient_ != null)
      webClient_.onReceivedTouchIconUrl(view_, url, precomposed);
  }
  @Override
  public void onShowCustomView(View view, int requestedOrientation,
      CustomViewCallback callback) {
    if (webClient_ != null) {
      webClient_.onShowCustomView(view_, requestedOrientation,
          callback);
    }
  }
  @Override
  protected boolean onCreateWindow(boolean isDialog, boolean isUserGesture) {
    if (webClient_ != null) {
      // TODO(pwnall): figure out what to do here
      Message resultMsg = new Message();
      resultMsg.setTarget(null);
      resultMsg.obj = null; // WebView.WebViewTransport
      return webClient_.onCreateWindow(view_, isDialog,
          isUserGesture, resultMsg);
    } else {
      return false;
    }
  }
  @Override
  protected void onRequestFocus() {
    if (webClient_ != null)
      webClient_.onRequestFocus(view_);
  }
  @Override
  protected void onCloseWindow() {
    if (webClient_ != null)
      webClient_.onCloseWindow(view_);
  }
  @Override
  public void onGeolocationPermissionsShowPrompt(String origin,
      Callback callback) {
    if (webClient_ != null) {
      webClient_.onGeolocationPermissionsShowPrompt(origin, callback);
    } else {
      callback.invoke(origin, false, false);
    }
  }
  @Override
  public void onGeolocationPermissionsHidePrompt() {
    if (webClient_ != null)
      webClient_.onGeolocationPermissionsHidePrompt();
  }
  @Override
  public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    if (webClient_ != null) {
      return webClient_.onConsoleMessage(consoleMessage);
    } else {
      return false;
    }
  }
  @Override
  protected View getVideoLoadingProgressView() {
    if (webClient_ != null) {
      return webClient_.getVideoLoadingProgressView();
    } else {
      return null;
    }
  }
  @Override
  public void getVisitedHistory(ValueCallback<String[]> callback) {
    if (webClient_ != null) {
      webClient_.getVisitedHistory(callback);
    } else {
      callback.onReceiveValue(new String[] {});
    }
  }
  @Override
  public void onReceivedTitle(String title) {
    if (viewClient_ != null) {
      webClient_.onReceivedTitle(view_, title);
    }
  }

  //// WebViewClient proxy methods.
  @Override
  public void onPageStarted(String url) {
    if (viewClient_ != null)
      viewClient_.onPageStarted(view_, url, null);
  }
  @Override
  public void onPageFinished(String url) {
    if (viewClient_ != null)
      viewClient_.onPageFinished(view_, url);
  }
  @Override
  public void onLoadResource(String url) {
    if (viewClient_ != null)
      viewClient_.onLoadResource(view_, url);
  }
  @Override
  public InterceptedRequestData shouldInterceptRequest(String url) {
    if (viewClient_ != null) {
      WebResourceResponse response =
          viewClient_.shouldInterceptRequest(view_, url);
      if (response != null) {
        return new InterceptedRequestData(response.getMimeType(),
            response.getEncoding(), response.getData());
      }
    }
    return null;
  }
  @Override
  public void onReceivedError(int errorCode, String description,
      String failingUrl) {
    if (viewClient_ != null) {
      viewClient_.onReceivedError(view_, errorCode, description, failingUrl);
    }
  }
  @Override
  public void onFormResubmission(Message dontResend, Message resend) {
    if (viewClient_ != null) {
      viewClient_.onFormResubmission(view_, dontResend, resend);
    } else {
      dontResend.sendToTarget();
    }
  }
  @Override
  public void doUpdateVisitedHistory(String url, boolean isReload) {
     if (viewClient_ != null)
       viewClient_.doUpdateVisitedHistory(view_, url, isReload);
  }
  @Override
  public void onReceivedSslError(ValueCallback<Boolean> callback,
      SslError error) {
    if (viewClient_ != null) {
      ChromeSslErrorHandlerProxy handler =
          new ChromeSslErrorHandlerProxy(callback);
      viewClient_.onReceivedSslError(view_, handler, error);
    } else {
      callback.onReceiveValue(false);
    }
  }
  @Override
  public void onReceivedHttpAuthRequest(AwHttpAuthHandler handler,
      String host, String realm) {
    if (viewClient_ != null) {
      ChromeHttpAuthHandlerProxy httpAuthHandler =
          new ChromeHttpAuthHandlerProxy(handler);
      viewClient_.onReceivedHttpAuthRequest(view_, httpAuthHandler,
          host, realm);
    } else {
      handler.cancel();
    }
  }
  @Override
  public void onUnhandledKeyEvent(KeyEvent event) {
    if (viewClient_ != null)
      viewClient_.onUnhandledKeyEvent(view_, event);
  }
  @Override
  public void onScaleChangedScaled(float oldScale, float newScale) {
    if (viewClient_ != null)
      viewClient_.onScaleChanged(view_, oldScale, newScale);
  }
  @Override
  public void onReceivedLoginRequest(String realm, String account,
      String args) {
    if (viewClient_ != null) {
      viewClient_.onReceivedLoginRequest(view_, realm, account,
          args);
    }
  }
  @Override
  public boolean shouldOverrideKeyEvent(KeyEvent event) {
    if (viewClient_ != null) {
      return viewClient_.shouldOverrideKeyEvent(view_, event);
    } else {
      return false;
    }
  }
  @Override
  public boolean shouldOverrideUrlLoading(String url) {
    if (viewClient_ != null) {
      return viewClient_.shouldOverrideUrlLoading(view_, url);
    } else {
      return false;
    }
  }

  // DownloadListener proxy methods.
  @Override
  public void onDownloadStart(String url, String userAgent,
      String contentDisposition, String mimeType, long contentLength) {
    if (downloadListener_ != null) {
      downloadListener_.onDownloadStart(url, userAgent, contentDisposition,
          mimeType, contentLength);
    }
  }

  // FindListener proxy methods.
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  @Override
  public void onFindResultReceived(int activeMatchOrdinal,
      int numberOfMatches, boolean isDoneCounting) {
    if (findListener_ != null) {
      findListener_.onFindResultReceived(activeMatchOrdinal, numberOfMatches,
          isDoneCounting);
    }
  }

  // PictureListener is deprecated, so we don't proxy it.
  @Override
  public void onNewPicture(Picture picture) {
    return;
  }
}
