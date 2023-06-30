package com.bimalghara.filedownloader.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.*


object FileUtil {

    val protectedDirectories:MutableList<String> = arrayListOf()

    init {
        protectedDirectories.addAll(
            listOf(
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.MEDIA_BAD_REMOVAL,
                Environment.MEDIA_CHECKING,
                Environment.MEDIA_EJECTING,
                Environment.MEDIA_MOUNTED,
                Environment.MEDIA_MOUNTED_READ_ONLY,
                Environment.MEDIA_NOFS,
                Environment.MEDIA_REMOVED,
                Environment.MEDIA_SHARED,
                Environment.MEDIA_UNKNOWN,
                Environment.MEDIA_UNMOUNTABLE,
                Environment.MEDIA_UNMOUNTED
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            protectedDirectories.addAll(
                listOf(
                    Environment.DIRECTORY_AUDIOBOOKS,
                    Environment.DIRECTORY_SCREENSHOTS,
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            protectedDirectories.addAll(
                listOf(
                    Environment.DIRECTORY_RECORDINGS
                )
            )
        }
    }

    fun getMimeType(ext: String): String? {
        // "audio/$ext"

        val mimeTypeMap = MimeTypeMap.getSingleton()
        return mimeTypeMap.getMimeTypeFromExtension(ext)
    }

    fun copyFileToUri(context: Context, sourceFilePath: String, destinationUri: Uri): Boolean {
        val inputStream: InputStream
        val outputStream: OutputStream

        try {
            inputStream = FileInputStream(File(sourceFilePath))
            outputStream = context.contentResolver.openOutputStream(destinationUri) ?: return false

            // Copy the data
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            // Close the streams
            inputStream.close()
            outputStream.close()

            return true
        }
        catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

}