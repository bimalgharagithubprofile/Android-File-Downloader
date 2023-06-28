package com.bimalghara.filedownloader.utils

import java.util.*

object FunUtil {

    fun convertTimestampToLocalDate(timestamp: Long): Date {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.time
    }


}