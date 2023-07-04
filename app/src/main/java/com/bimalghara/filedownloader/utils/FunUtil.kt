package com.bimalghara.filedownloader.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bimalghara.filedownloader.broadcast.LocalMessageSender
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.service.DownloadService
import com.bimalghara.filedownloader.utils.Logger.logs
import kotlinx.coroutines.delay
import okhttp3.Headers
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object FunUtil {

    fun convertTimestampToLocalDate(timestamp: Long): Date {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.time
    }

    fun getDay(date: Date): String {
        val today = Date()
        val yesterday = Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000))
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        return when {
            isSameDay(date, today) -> ("Today")
            isSameDay(date, yesterday) -> ("Yesterday")
            else -> (dateFormatter.format(date))
        }
    }
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormatter.format(date1) == dateFormatter.format(date2)
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

    fun Long.toSpeed(): String {
        if (this <= 0) return "0 Bytes"

        val units = arrayOf("Bytes", "Kbps", "Mbps", "Gbps", "Tbps")
        val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()

        return String.format("%.1f %s", this / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun calculateETA(speed: Long, totalSize: Long, downloadedSize: Long): String {
        val remainingBytes = totalSize - downloadedSize
        val eta =  if (speed > 0) (remainingBytes / speed) else (-1)
        return formatETA(eta)
    }
    private fun formatETA(eta: Long): String {
        return when {
            eta < 0 -> "N/A"
            eta < 60 -> "00:${eta}"
            eta < 3600 -> "${eta / 60}:${eta % 60}"
            else -> {
                val hours = eta / 3600
                val minutes = (eta % 3600) / 60
                val seconds = eta % 60

                "${hours}:${minutes}:${seconds}"
            }
        }
    }

    fun fetchProgress(currentFileSize: Long, totalFileSize: Long): Int {
        return (currentFileSize * 100 / totalFileSize).toInt()
    }

    suspend fun wakeUpDownloadService(appContext: Context, action: String) {
        appContext.connectDownloadService()
        delay(2000)
        refreshDownloadService(appContext, action)
    }
    suspend fun refreshDownloadService(appContext: Context, action: String) {
        LocalMessageSender.sendMessageToBackground(context = appContext, action = action)
    }

    private fun Context.connectDownloadService(){
        if(!isServiceRunning(this, DownloadService::class.java)) {
            val serviceIntent = Intent(this, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = manager.runningAppProcesses
        for (processInfo in runningProcesses) {
            val serviceName = processInfo.processName
            if (serviceName == serviceClass.name) {
                return true
            }
        }
        return false
    }


}