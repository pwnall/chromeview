// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

/**
 * The exception that is thrown when the intialization of a process was failed.
 */
public class ProcessInitException extends Exception {
    private int mErrorCode = 0;

    /**
     * @param errorCode The error code could be one from content/public/common/result_codes.h
     *                  or embedder.
     */
    public ProcessInitException(int errorCode) {
        mErrorCode = errorCode;
    }

    /**
     * @param errorCode The error code could be one from content/public/common/result_codes.h
     *                  or embedder.
     * @param throwable The wrapped throwable obj.
     */
    public ProcessInitException(int errorCode, Throwable throwable) {
        super(null, throwable);
        mErrorCode = errorCode;
    }

    /**
     * Return the error code.
     */
    public int getErrorCode() {
        return mErrorCode;
    }
}
