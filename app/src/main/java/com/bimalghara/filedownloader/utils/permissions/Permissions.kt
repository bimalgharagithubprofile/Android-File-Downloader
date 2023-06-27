package com.bimalghara.filedownloader.utils.permissions

import android.Manifest.permission.*

/**
 * Created by BimalGhara
 */

sealed class Permissions(vararg val permissions: String) {

    object Storage : Permissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)

}