package com.bimalghara.filedownloader.domain.mapper

import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity

fun DownloadEntity.toState() : DownloadItemState {
    return DownloadItemState(
        id = id,
        url = url,
        name = name,
        mimeType = mimeType,
        size = size,
        downloadStatus = downloadStatus,
        lastProgress = lastProgress,
        interruptedBy = interruptedBy,
        downloadedFileUri = downloadedFileUri,
    )
}