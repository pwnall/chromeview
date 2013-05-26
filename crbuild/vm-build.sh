#!/bin/sh
# Builds the Chromium bits needed by ChromeView.

set -o errexit  # Stop the script on the first error.
set -o nounset  # Catch un-initialized variables.

cd ~/chromium/
# https://code.google.com/p/chromium/wiki/UsingGit
gclient sync --jobs 16
cd ~/chromium/src

if [ -f ~/.build_android ] ; then
  . build/android/envsetup.sh --target-arch=arm
  android_gyp
  ninja -C out/Release -k0 -j$CPUS libwebviewchromium android_webview_apk \
      content_shell_apk chromium_testshell
fi

if [ -f ~/.build_x86 ] ; then
  . build/android/envsetup.sh --target-arch=x86
  android_gyp
  ninja -C out/Release -k0 -j$CPUS libwebviewchromium android_webview_apk \
      content_shell_apk chromium_testshell
fi
