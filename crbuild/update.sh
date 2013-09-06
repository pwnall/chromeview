#!/bin/bash

# Updates this project with the Chrome build files.
# This script assumes the Chrome build VM is up at crbuild.local

# set this to your build machine's scp user@host
export BLD=crbuild@ubuntu.local

# Clean up.
rm -r assets/*
rm -r libs/*
rm -rf src/com/googlecode/*
rm -rf src/org/chromium/*

# ContentShell core -- use this if android_webview doesn't work out.
#scp $BLD:chromium/src/out/Release/content_shell/assets/* assets/
#rm libs/**/gdbserver
#scp -r $BLD:chromium/src/out/Release/content_shell_apk/libs/* libs
#scp -r $BLD:chromium/src/content/shell/android/java/res/* res
#scp -r $BLD:chromium/src/content/shell/android/java/src/* src
#scp -r $BLD:chromium/src/content/shell_apk/android/java/res/* res

# android_webview
scp $BLD:chromium/src/out/Release/android_webview_apk/assets/*.pak assets
scp -r $BLD:chromium/src/out/Release/android_webview_apk/libs/* libs
rm libs/**/gdbserver
scp -r $BLD:chromium/src/android_webview/java/src/* src/

## Dependencies inferred from android_webview/Android.mk

# Resources.
scp -r $BLD:chromium/src/content/public/android/java/resource_map/* src/
scp -r $BLD:chromium/src/ui/android/java/resource_map/* src/

# ContentView dependencies.
scp -r $BLD:chromium/src/base/android/java/src/* src/
scp -r $BLD:chromium/src/content/public/android/java/src/* src/
scp -r $BLD:chromium/src/media/base/android/java/src/* src/
scp -r $BLD:chromium/src/net/android/java/src/* src/
scp -r $BLD:chromium/src/ui/android/java/src/* src/
scp -r $BLD:chromium/src/third_party/eyesfree/src/android/java/src/* src/

# Grab this resource file or org.chromium.chrome.browser.ResourceId.java won't compile
scp -r $BLD:chromium/src/out/Release/chromium_testshell/gen/* src/

# Strip a ContentView file that's not supposed to be here.
rm src/org/chromium/content/common/common.aidl

# Get rid of the .svn directory in eyesfree.
rm -r src/com/googlecode/eyesfree/braille/.svn

# Browser components.
scp -r $BLD:chromium/src/components/web_contents_delegate_android/android/java/src/* src/
scp -r $BLD:chromium/src/components/navigation_interception/android/java/src/* src/

# Generated files.
scp -r $BLD:chromium/src/out/Release/gen/templates/* src/

# JARs.
scp -r $BLD:chromium/src/out/Release/lib.java/guava_javalib.jar libs/
scp -r $BLD:chromium/src/out/Release/lib.java/jsr_305_javalib.jar libs/

# android_webview generated sources. Must come after all the other sources.
scp -r $BLD:chromium/src/android_webview/java/generated_src/* src/
