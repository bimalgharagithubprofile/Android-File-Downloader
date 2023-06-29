package com.bimalghara.filedownloader.utils

import com.bimalghara.filedownloader.domain.model.FileDetails
import okhttp3.Headers
import java.net.URL
import java.text.DecimalFormat
import java.util.*

object FunUtil {

    fun convertTimestampToLocalDate(timestamp: Long): Date {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.time
    }

    fun createFileDetailsFromHeaders(requestUrl: String, headers: Headers): FileDetails {
        val contentLength = headers["Content-Length"]?.toLong()
        val contentType = headers["Content-Type"]
        val lastModified = headers["Last-Modified"]
        val eTag = headers["ETag"]
        val cacheControl = headers["Cache-Control"]
        val expires = headers["Expires"]
        val acceptRanges = headers["Accept-Ranges"]
        val server = headers["Server"]

        val fileNameDetails = getFileNameAndExtensionFromRequest(requestUrl)
        val fileName = fileNameDetails.first
        val fileExtension = fileNameDetails.second


        return FileDetails(
            requestUrl = requestUrl,
            contentLength = contentLength,
            contentType = contentType,
            fileName = fileName,
            fileExtension = fileExtension,
            lastModified = lastModified,
            eTag = eTag,
            cacheControl = cacheControl,
            expires = expires,
            acceptRanges = acceptRanges,
            server = server
        )
    }

    private fun getFileNameAndExtensionFromRequest(requestUrl: String): Pair<String?, String?> {
        return try {
            val uri = URL(requestUrl)
            val path = uri.path
            val fileName = path.substringAfterLast('/')
            val dotIndex = fileName.lastIndexOf(".")
            val name = if (dotIndex != -1) fileName.substring(0, dotIndex) else null
            val extension = if (dotIndex != -1) fileName.substring(dotIndex + 1) else null
            Pair(name, extension)
        }
        catch (e: Exception) {
            e.printStackTrace()
            Pair(null, null)
        }
    }

    fun Long.toMegabytes(): String {
        val megabyte = 1024.0 * 1024.0
        val result = this.toDouble() / megabyte

        val decimalFormat = DecimalFormat("#.#")
        return decimalFormat.format(result) + " MB"
    }
}