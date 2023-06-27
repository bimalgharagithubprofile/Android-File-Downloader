package com.bimalghara.filedownloader.data.repository

import android.content.Context
import android.util.Log
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.network.RemoteDataSource
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource
import okhttp3.ResponseBody
import java.io.IOException
import javax.inject.Inject


/**
 * Created by BimalGhara
 */

class DownloaderRepositoryImpl @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val remoteDataSource: RemoteDataSource
) : DownloaderRepositorySource {
    private val logTag = javaClass.simpleName

    override suspend fun requestFileDetailsFromNetwork(
        appContext: Context,
        url: String
    ): FileDetails {

        /*val callback: Function3<Float, Long, String, Unit> =
            { progress: Float, o2: Long?, line: String? ->
                Log.e(logTag, "callback: $progress, $o2, $line")
            }

        return try {
            *//*val result = YTDLP.execute(appContext, url, callback)

            //convert raw response to DTO
            val videoInfoDTO = ObjectMapper().readValue(result.out, VideoInfoDTO::class.java)

            //convert DTO to Model
            val converted = videoInfoDTO.toDomain()

            converted*//*
            FileDetails()

        } catch (e: CustomException) {
            throw e
        } catch (ex: Exception) {
            throw CustomException(cause = "Unable to parse video information: ${ex.localizedMessage}")
        }*/
        return FileDetails()
    }

    override suspend fun requestDownloadFileFromNetwork(
        appContext: Context,
        url: String,
        ext: String,
        callback: (Triple<Int, String?, String>) -> Unit
    ) {
        Log.e(logTag, "start downloading: $url")

        /*val videoRawName = "${System.currentTimeMillis()}.${ext}"

        val baseDir = File(appContext.noBackupFilesDir, DIRECTORY_BASE)
        if (!baseDir.exists()) baseDir.mkdir()
        val videoDir = File(baseDir, VIDEO_DIRECTORY)
        if (!videoDir.exists()) videoDir.mkdir()
        val videoFile = File(videoDir, videoRawName)
        if (!videoFile.exists()) videoFile.createNewFile()

        remoteDataSource.requestDownload(url, object : DownloadCallback {
            override fun onDataReceive(responseBody: ResponseBody, callback: DownloadCallback) {
                writeResponseBodyToDisk(responseBody, videoFile.absolutePath, callback)
            }

            override fun onProgressUpdateProgress(progress: Int, combineSize: String) {
                callback(Triple(progress, null, combineSize))
            }

            override fun onDownloadComplete(filePath: String, combineSize: String) {
                callback(Triple(100, filePath, combineSize))
            }

            override fun onDownloadFailed(errorMessage: String) {
                Log.e(logTag, "callback onDownloadFailed : $errorMessage")
                throw CustomException(cause = errorMessage)
            }
        })*/

    }

    private fun writeResponseBodyToDisk(
        body: ResponseBody,
        destinationPath: String,
        callback: DownloadCallback
    ) {
        try {
            /*val file = File(destinationPath)
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            val totalBytes = body.contentLength()

            var previousProgress = 0
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (totalBytes > 0) {
                    val progress = (totalBytesRead * 100 / totalBytes).toInt()
                    if (progress != previousProgress) {
                        if (progress < 100)//100 will be sent at last(check end of this function block - callback.onDownloadComplete)
                            callback.onProgressUpdateProgress(progress, "$totalBytesRead|$totalBytes")
                        previousProgress = progress
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            callback.onDownloadComplete(destinationPath, "$totalBytesRead|$totalBytes")*/
        } catch (e: IOException) {
            callback.onDownloadFailed("Failed to write the video file to disk: ${e.message}")
        }
    }


}

