// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

class FileDescriptorInfo {
    public int mId;
    public int mFd;
    public boolean mAutoClose;

    FileDescriptorInfo(int id, int fd, boolean autoClose) {
        mId = id;
        mFd = fd;
        mAutoClose = autoClose;
    }
}