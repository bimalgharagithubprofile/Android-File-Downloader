package com.bimalghara.filedownloader.utils

import android.util.Log
import com.bimalghara.filedownloader.BuildConfig

object Logger {
    fun logs(tag:String, value:String){
        if(BuildConfig.DEBUG) {
            Log.e(tag, value)
        }
    }
}