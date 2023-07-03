package com.bimalghara.filedownloader.domain.mapper

import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity

fun DownloadEntity.toState() : DownloadItemState {
    return DownloadItemState(
        id = id,
        url = url,
        wifiOnly = wifiOnly,
        destinationUri = destinationUri,
        name = name,
        mimeType = mimeType,
        size = size,
        supportRange = supportRange,
        downloadStatus = downloadStatus,
        lastProgress = lastProgress,
        interruptedBy = interruptedBy,
        downloadedFileUri = downloadedFileUri,
    )
}