package com.bimalghara.filedownloader.data.repository

import android.content.Context
import android.util.Log
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.DownloadsDao
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.network.retrofit.service.ApiServiceDownload
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.bimalghara.filedownloader.utils.FunUtil.createFileDetailsFromHeaders
import com.bimalghara.filedownloader.utils.ResourceWrapper
import com.bimalghara.filedownloader.utils.getStringFromResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.IOException
import javax.inject.Inject


/**
 * Created by BimalGhara
 */

class DownloaderRepositoryImpl @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val serviceGenerator: ApiServiceGenerator,
    private val downloadsDao: DownloadsDao?,
) : DownloaderRepositorySource {
    private val logTag = javaClass.simpleName


    override suspend fun addQueue(
        appContext: Context,
        wifiOnly: Boolean,
        fileDetails: FileDetails,
        selectedDirectory: String
    ): ResourceWrapper<Boolean> {
        try {
            val data = DownloadEntity(
                url = "",
                wifiOnly = wifiOnly,
                selectedFolder = "",
                name = "",//name + ext
                ext = "",
                sizeTotal = 0,//in bytes [total]
                downloadStatus = DownloadStatus.WAITING.name,
                updatedAt = System.currentTimeMillis()
            )

            val id = downloadsDao?.addDownload(data) ?: -1

            //eeeee

            return ResourceWrapper.Success(data = true)
        } catch (e: Exception){
            e.printStackTrace()
            return ResourceWrapper.Error(e.localizedMessage ?: appContext.getStringFromResource(R.string.error_failed_to_queue))
        }
    }


    override suspend fun requestDownloadsFromLocal(): Flow<List<DownloadEntity>> {
        return downloadsDao?.getDownloads() ?: flow { emit(emptyList()) }
    }

    override suspend fun requestFileDetailsFromNetwork(
        appContext: Context,
        url: String
    ): Flow<ResourceWrapper<FileDetails>>  = flow {
        emit(ResourceWrapper.Loading())
        try {
            val fileInfoService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

            val response = fileInfoService.getFileDetails(url).execute()
            if (response.isSuccessful) {

                val fileDetails = createFileDetailsFromHeaders(url, response.headers())

                emit(ResourceWrapper.Success(data = fileDetails))
            } else {
                emit(ResourceWrapper.Error(appContext.getStringFromResource(R.string.network_not_supported)))
            }

        } catch (e: Exception) {
            emit(ResourceWrapper.Error(e.localizedMessage ?: appContext.getStringFromResource(R.string.error_default)))
        }
    }.flowOn(dispatcherProviderSource.io)

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

        download(url, object : DownloadCallback {
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

