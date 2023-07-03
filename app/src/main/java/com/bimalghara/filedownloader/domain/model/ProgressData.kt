package com.bimalghara.filedownloader.domain.model

import android.os.Parcelable
import com.bimalghara.filedownloader.utils.NotificationStatus
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ProgressData(
    var id:Int,
    var progress:Int=0,
    var actionData:String?=null,
    var isIndeterminate: Boolean = false
) : Parcelable
