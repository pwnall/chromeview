// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

import org.chromium.content.common.IChildProcessCallback;

import android.view.Surface;
import android.os.Bundle;

interface IChildProcessService {
  // Sets up the initial IPC channel and returns the pid of the child process.
  int setupConnection(in Bundle args, IChildProcessCallback callback);
}
