package com.bimalghara.filedownloader.utils.permissions

import android.Manifest.permission.*
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Created by BimalGhara
 */

sealed class Permissions(vararg val permissions: String) {

    object Storage : Permissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    object Notification : Permissions(POST_NOTIFICATIONS)

}