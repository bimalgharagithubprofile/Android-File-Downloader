package com.bimalghara.filedownloader.data.network

interface DownloadCallback {
    fun onDownloadStarted(initialProgress: Int, downloadId: Int)
    fun onDownloadPaused(downloadId: Int, lastProgress:Int)
    fun onDownloadCancelled(downloadId: Int)
    fun onInfiniteProgressUpdate(downloadedData: String, downloadId: Int)
    fun onProgressUpdate(progress: Int, downloadId: Int)
    fun onDownloadComplete(tmpPath: String, downloadId: Int)
    fun onDownloadFailed(errorMessage: String, downloadId: Int)
}