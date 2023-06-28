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

    var url: String?,
    var wifiOnly: Boolean = true,
    var selectedFolder: String?,

    var name: String?,//name + ext
    var ext: String?,
    var sizeTotal: Int=0,//in bytes [total]
    var sizeCurrent: Int=0,//in bytes [downloaded]

    var progress: Int=0,//1%-100%
    var downloadStatus: String = DownloadStatus.WAITING.name,
    var interruptedBy: String?,//user or no-wifi (when wifi only selected)

    var updatedAt: Long = -1, //last updated timestamp
)





