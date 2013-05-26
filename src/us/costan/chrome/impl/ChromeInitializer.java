package us.costan.chrome.impl;

import org.chromium.android_webview.AwBrowserProcess;
import org.chromium.android_webview.AwResource;
import org.chromium.content.browser.ResourceExtractor;
import org.chromium.content.common.CommandLine;

import us.costan.chrome.ChromeView;
import android.content.Context;

/**
 * Chromium setup chores.
 */
public class ChromeInitializer {
  private static final String[] MANDATORY_PAKS = { "webviewchromium.pak" };

  /**
   * The entry point to the initialization process.
   *
   * This is called by {@link ChromeView#initialize(Context)}.
   *
   * @param context Android context for the application using ChromeView
   */
  public static void initialize(Context context) {
    if (initializeCalled_) {
      return;
    }
    initializeCalled_ = true;

    // Initialization lifted from
    //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/shell/AwShellResourceProvider

    AwResource.setResources(context.getResources());

    AwResource.RAW_LOAD_ERROR = us.costan.chrome.R.raw.blank_html;
    AwResource.RAW_NO_DOMAIN = us.costan.chrome.R.raw.blank_html;

    AwResource.STRING_DEFAULT_TEXT_ENCODING =
        us.costan.chrome.R.string.default_encoding;

    // Initialization lifted from
    //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/shell/AwShellApplication

    CommandLine.initFromFile("/data/local/chrome-command-line");

    ResourceExtractor.setMandatoryPaksToExtract(MANDATORY_PAKS);
    ResourceExtractor.setExtractImplicitLocaleForTesting(false);
    AwBrowserProcess.loadLibrary();
    AwBrowserProcess.start(context);
  }

  /** Ensures that initialize() is only called once. */
  private static boolean initializeCalled_ = false;
}
