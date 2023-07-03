package com.bimalghara.filedownloader.notification.model

import android.os.Parcelable
import com.bimalghara.filedownloader.utils.NotificationStatus
import kotlinx.android.parcel.Parcelize

@Parcelize
data class NotificationData(
    var id:Int,
    var status: NotificationStatus,
    var name:String,
    var progress:Int=0,
    var actionData:String?=null,
    var speed:String?=null,
    var eta:String?=null,
    var isIndeterminate: Boolean = false
) : Parcelable
