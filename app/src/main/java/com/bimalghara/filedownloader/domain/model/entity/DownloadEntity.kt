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
    var sizeTotal: Long=0,//in bytes [total]
    var sizeCurrent: Long=0,//in bytes [downloaded]

    var supportRange: Boolean=false,

    var progress: Int=0,//1%-100%
    var downloadStatus: String = DownloadStatus.WAITING.name,
    var interruptedBy: String?=null,//user or no-wifi (when wifi only selected)

    var updatedAt: Long = -1, //last updated timestamp
) {
    override fun toString(): String = "id=$id name=$name wifiOnly=$wifiOnly sizeTotal=$sizeTotal sizeCurrent=$sizeCurrent supportRange=$supportRange sts=$downloadStatus progress=$progress interrupted=$interruptedBy"
}





