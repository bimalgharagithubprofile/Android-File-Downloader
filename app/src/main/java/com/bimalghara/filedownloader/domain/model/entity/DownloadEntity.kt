package com.bimalghara.filedownloader.domain.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bimalghara.filedownloader.utils.DownloadStatus

/**
 * Created by BimalGhara
 */
@Entity
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    var url: String,
    var wifiOnly: Boolean = true,
    var destinationUri: String,//string uri

    var name: String,//name + ext
    var mimeType: String,
    var size: Long=0,//in bytes [total]

    var supportRange: Boolean=false,

    var progress: Int=0,//1%-100%

    var downloadStatus: String = DownloadStatus.WAITING.name,
    var interruptedBy: String?=null,//user or no-wifi (when wifi only selected)

    var downloadedFileUri: String?=null,//download file in selected folder

    var updatedAt: Long = -1, //last updated timestamp
) {
    override fun toString(): String = "id=$id name=$name wifiOnly=$wifiOnly size=$size supportRange=$supportRange sts=$downloadStatus progress=$progress interrupted=$interruptedBy downloadedFileUri=$downloadedFileUri"
}





