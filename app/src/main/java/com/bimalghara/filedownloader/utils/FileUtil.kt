package com.bimalghara.filedownloader.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import com.bimalghara.filedownloader.utils.Logger.logs
import java.io.*


object FileUtil {

    val protectedDirectories:MutableList<String> = arrayListOf()

    val imageTypes = arrayOf("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp")
    val videoTypes = arrayOf("video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo")
    val audioTypes = arrayOf("audio/mp3", "audio/mpeg", "audio/wav", "audio/x-ms-wma", "audio/vnd.rn-realaudio")

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
        val mimeTypeMap = MimeTypeMap.getSingleton()
        return mimeTypeMap.getMimeTypeFromExtension(ext)
    }

    fun getFileType(mimeType: String): FileType {
        return if (imageTypes.contains(mimeType)) {
            FileType.IMAGE
        } else if (videoTypes.contains(mimeType)) {
            FileType.VIDEO
        } else if (audioTypes.contains(mimeType)) {
            FileType.AUDIO
        } else {
            FileType.OTHER
        }
    }

    private fun getFileSize(outputStream: CountingOutputStream): Long {
        return outputStream.getByteCount()
    }

    fun copyFileToUri(context: Context, sourceFilePath: String, destinationUri: Uri): Pair<Boolean, Long> {
        val inputStream: InputStream
        val outputStream: OutputStream

        try {
            inputStream = FileInputStream(File(sourceFilePath))
            outputStream = context.contentResolver.openOutputStream(destinationUri) ?: return Pair(false, 0)

            val countingOutputStream = CountingOutputStream(outputStream)

            // Copy the data
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                countingOutputStream.write(buffer, 0, bytesRead)
            }

            // Close the streams
            inputStream.close()
            countingOutputStream.close()
            outputStream.close()

            val fileSize = getFileSize(countingOutputStream)
            logs("Fileutil", "Output File size: $fileSize bytes")

            return Pair(true, fileSize)
        } catch (e: IOException) {
            e.printStackTrace()
            return Pair(false, 0)
        }
    }


}



class CountingOutputStream(private val wrappedOutputStream: OutputStream) : OutputStream() {
    private var byteCount: Long = 0

    override fun write(b: Int) {
        wrappedOutputStream.write(b)
        byteCount++
    }

    override fun write(b: ByteArray) {
        wrappedOutputStream.write(b)
        byteCount += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        wrappedOutputStream.write(b, off, len)
        byteCount += len
    }

    override fun flush() {
        wrappedOutputStream.flush()
    }

    override fun close() {
        wrappedOutputStream.close()
    }

    fun getByteCount(): Long {
        return byteCount
    }
}
