package com.bimalghara.filedownloader.data.network

interface DownloadCallback {
    fun onDownloadStarted(initialProgress: Int, downloadId: Int, name: String)
    fun onDownloadPaused(downloadId: Int, lastProgress:Int, name: String)
    fun onDownloadCancelled(downloadId: Int)
    fun onInfiniteProgressUpdate(downloadedData: String, downloadId: Int, name: String)
    fun onProgressUpdate(progress: Int, downloadId: Int, name: String, actionData: String)
    fun onDownloadComplete(tmpPath: String, downloadId: Int)
    fun onDownloadFailed(errorMessage: String, downloadId: Int)
}