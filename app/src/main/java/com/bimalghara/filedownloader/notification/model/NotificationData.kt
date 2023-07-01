package com.bimalghara.filedownloader.notification.model

import com.bimalghara.filedownloader.utils.NotificationStatus

data class NotificationData(
    var id:Int,
    var status: NotificationStatus,
    var name:String,
    var progress:Int=0,
    var actionData:String?=null,
    var speed:String?=null,
    var eta:String?=null,
    var isIndeterminate: Boolean = false
)
