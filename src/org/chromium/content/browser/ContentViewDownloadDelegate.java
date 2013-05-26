// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Interface to be implemented by the embedder to handle file downloads.
 */
public interface ContentViewDownloadDelegate {
    /**
     * Notify the host application that a file should be downloaded. Replaces
     * onDownloadStart from DownloadListener.
     * @param url The full url to the content that should be downloaded
     * @param userAgent the user agent to be used for the download.
     * @param contentDisposition Content-disposition http header, if
     *                           present.
     * @param mimetype The mimetype of the content reported by the server.
     * @param cookie The cookie
     * @param referer Referer http header.
     * @param contentLength The file size reported by the server.
     */
    void requestHttpGetDownload(String url, String userAgent, String contentDisposition,
            String mimetype, String cookie, String referer, long contentLength);

    /**
     * Notify the host application that a download is started.
     */
    void onDownloadStarted();

    /**
     * Notify the host application that a download is finished.
     * @param url The full url to the content that was downloaded.
     * @param mimetype The mimetype of downloaded file.
     * @param path Path of the downloaded file.
     * @param contentLength The file size of the downloaded file (in bytes).
     * @param successful Whether the download succeeded
     */
    void onDownloadCompleted(String url, String mimetype, String path,
            long contentLength, boolean successful);

    /**
     * Notify the host application that a download has an extension indicating
     * a dangerous file type.
     * @param filename File name of the downloaded file.
     * @param downloadId The download id.
     */
    void onDangerousDownload(String filename, int downloadId);
}
