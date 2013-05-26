# ChromeView

ChormeView works like Android's WebView, but is backed by the latest Chromium
code.


## Why ChromeView

ChromeView lets you ship your own Chromium code, instead of using whatever
version comes with your user's Android image. This gives your application
early access to the newest features in Chromium, and removes the variability
due to different WebView implementations in different versions of Android.


## Setting Up

This section explains how to set up your Android project to use ChromeView.

### Get the Code

Check out the repository in your Eclipse workspace, and make your project use
ChromeView as a library. In Eclipse, right-click your project directory, select
`Properties`, choose the `Android` category, and click on the `Add` button in
the `Library section`.

### Copy Data

Copy `assets/webviewchromium.pak` to your project's `assets` directory.
[Star this bug](https://code.google.com/p/android/issues/detail?id=35748) if
you agree that this is annoying.

In your `Application` subclass, call `ChromeView.initialize` and pass it the
application's context. For example,

### Initialize Chromium

```java
import us.costan.chrome.ChromeView;
import android.app.Application;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ChromeView.initialize(this);
    }
}
```

Now you can use ChromeView in the same contexts as you would use WebView.

### Star some bugs

If you use this project and want to help move it along, please star the
following bugs.

* [crbug.com/113088](http://crbug.com/113088)
* [crbug.com/234907](http://crbug.com/234907) and
* [this Android bug](https://code.google.com/p/android/issues/detail?id=35748)


## Usage

To access ChromeView in the graphical layout editor, go to the `Palette`,
expand the `Custom and Library Views` section, and click the `Refresh` button.

ChromeView supports most of the WebView methods. For example,

```java
ChromeView chromeView = (ChromeView)findViewById(R.id.gameUiView);
chromeView.getSettings().setJavaScriptEnabled(true);
chromeView.loadUrl("http://www.google.com");
```

### JavaScript

ChromeView's `addJavaScriptInterface` exposes public methods that are annotated
with `@ChromeJavascriptInterface`. This is because WebView's
`@JavascriptInterface` is only available on Android 4.2 and above, but
ChromeView targets 4.0 and 4.1 as well.

```java
import us.costan.chrome.ChromeJavascriptInterface;

public class JsBindings {
    @ChromeJavascriptInterface
    public String getHello() {
        return "Hello world";
    }
}

chromeView.addJavascriptInterface(new JsBindings(), "AndroidBindings");
```

### Cookies

ChromeCookieManager is ChromeView's equivalent of CookieManager.

```java
ChromeCookieManager.getInstance().getCookie("https://www.google.com");
```

### Faster Development

To speed up the application launch on real devices, remove the `libs/x86`
directory. When developing on Atom devices, remove the ARM directory instead.

Remember to `git checkout -- .` and get the library back before building a
release APK.

### Internet Access

If your application manifest doesn't specify the
[INTERNET permission](http://developer.android.com/reference/android/Manifest.permission.html#INTERNET),
the Chromium code behind ChromeView silentely blocks all network requests. This
is mentioned here because it can be hard to debug.


## Building

The bulk of this project is Chromium source code and build products. With the
appropriate infrastructure, the Chromium bits can be easily updated.

[crbuild/vm-build.md](crbuild/vm-build.md) contains step-by-step instructions
for setting up a VM and building the Chromium for Android components used by
ChromeView.

Once Chromium has been successfully built, running
[crbuild/update.sh](crbuild/update.sh) will copy the relevant bits from the
build VM into the ChromeView source tree.


## Issues

Attempting to scroll the view (by swiping a finger across the screen) does not
update the displayed image. However, internally, the view is scrolled. This can
be seen by displaying a stack of buttons and trying to click on the topmost
one. This issue makes ChromeView mostly unusable in production.

The core issue is that the integration is done via `AwContent` in the
`android_webview` directory of the Chromium source tree, which is experimental
and not intended for embedding use. The "right" way of doing this is to embed
a `ContentView` from the `content` directory, or a `Shell` in `content/shell`.
Unfortunately, these components' APIs don't match WebView nearly as well as
AwContent, and they're much harder to integrate. Pull requests or a fork would
be welcome.

This repository is rebased often, because the large files in `lib/` would
result in a huge repository if new commits were created for each build. The
large files are Chromium build products.


## Contributing

Please don't hesitate to send your Pull Requests!

Please don't send pull requests including the binary assets or code extracted
from Android (`assets/`, `libs/`, `src/com/googlecode/` and `src/org/android`).
If your Pull Request requires updated Android bits, mention that in the PR
description, and I will rebuild the Android bits.


## Copyright and License

The directories below contain code from the
[The Chromium Project](http://www.chromium.org/), which is subject to the
copyright and license on the project site.

* `assets/`
* `libs/`
* `src/com/googlecode`
* `src/org/chromium`

Some of the source code in `src/us/costan/chrome` has been derived from the
Android source code, and is therefore covered by the
[Android project licenses](http://source.android.com/source/licenses.html).

The rest of the code is Copyright 2013, Victor Costan, and available under the
MIT license.
