package com.bimalghara.filedownloader.domain.model

import androidx.appcompat.widget.AppCompatTextView
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Created by BimalGhara
 */
data class DownloadItemState(
    var id: Int = 0,
    var url: String,
    var name: String,
    var mimeType: String,
    var size: Long=0,
    var downloadStatus: String = DownloadStatus.WAITING.name,
    var lastProgress: Int = 0,
    var interruptedBy: String?=null,
    var downloadedFileUri: String?=null,

    var tvProgress: AppCompatTextView?=null,//view sate
    var progressIndicator: LinearProgressIndicator?=null,//view sate
    var tvAction: AppCompatTextView?=null,//view sate
) {
    override fun toString(): String = "id=$id name=$name size=$size sts=$downloadStatus lastProgress=$lastProgress interrupted=$interruptedBy downloadedFileUri=$downloadedFileUri"
}






