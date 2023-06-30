package com.bimalghara.filedownloader.data.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.DownloadsDao
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.network.retrofit.service.ApiServiceDownload
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.utils.FunUtil.fetchProgress
import com.bimalghara.filedownloader.utils.FunUtil.toMegabytes
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.awaitResponse
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val serviceGenerator: ApiServiceGenerator,
    private val downloadsDao: DownloadsDao,
) {
    private val logTag = "DownloadManager"

    private val coroutineScope = CoroutineScope(dispatcherProviderSource.io)

    private val downloadJobs = mutableMapOf<Int, Job>()
    private val downloadCalls = mutableMapOf<Int, Call<ResponseBody>>()
    private val cancellationFlags = mutableMapOf<Int, AtomicBoolean>()


    suspend fun getOpenQueuedList(): List<DownloadEntity>{
        return downloadsDao.getOpenQueuedList()
    }


    fun downloadFile(appContext: Context, downloadEntity: DownloadEntity, tmpPath: String, callback: DownloadCallback) {
        val downloadService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

        if (downloadJobs.containsKey(downloadEntity.id)) {
            return
        }

        cancellationFlags[downloadEntity.id] = AtomicBoolean(false)

        Log.w(logTag, "downloadFile: ${downloadEntity.id}" )
        Log.w(logTag, "downloadFile: ${cancellationFlags[downloadEntity.id]}" )

        val downloadJob = coroutineScope.launch {

            var destinationFileSize = 0L
            val outputFile = File(tmpPath)

            if (downloadEntity.supportRange && outputFile.exists())
                destinationFileSize = outputFile.length()

            val downloadCall = if (destinationFileSize > 0) {
                downloadService.downloadFileRange(
                    downloadEntity.url, "bytes=$destinationFileSize-"
                )
            } else {
                downloadService.downloadFile(downloadEntity.url)
            }

            downloadCalls[downloadEntity.id] = downloadCall

            var initialProgress = 0
            if (destinationFileSize > 0 && downloadEntity.sizeTotal > 0) {
                initialProgress = fetchProgress(destinationFileSize, downloadEntity.sizeTotal)
            }

            callback.onDownloadStarted(initialProgress, downloadEntity.id)

            try {
                val response = downloadCall.awaitResponse()
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        writeResponseBodyToDisk(appContext, responseBody, downloadEntity, tmpPath,callback)
                    } else {
                        removeIdFromMap(downloadEntity.id)
                        callback.onDownloadFailed("Empty response body.", downloadEntity.id)
                    }
                }
                else {
                    removeIdFromMap(downloadEntity.id)
                    callback.onDownloadFailed("Download failed with response code: ${response.code()}", downloadEntity.id)
                }
            }
            catch (e: Exception) {
                removeIdFromMap(downloadEntity.id)
                callback.onDownloadFailed("Download failed: ${e.message}", downloadEntity.id)
            }
        }

        downloadJobs[downloadEntity.id] = downloadJob
    }

    fun cancelDownload(downloadId: Int?) {

        Log.w(logTag, "cancelDownload: $downloadId" )

        if (downloadId == null){
            //cancel all
        } else {
            val cancellationFlag = cancellationFlags[downloadId]
            val downloadCall = downloadCalls[downloadId]
            val downloadJob = downloadJobs[downloadId]

            Log.w(logTag, "cancellationFlag: $cancellationFlag" )
            Log.w(logTag, "downloadCall: $downloadCall" )
            Log.w(logTag, "downloadJob: $downloadJob" )



            cancellationFlag?.set(true)

            Handler(Looper.myLooper()!!).postDelayed({
                downloadCall?.cancel()
                downloadJob?.cancel()
            }, 500)
        }
    }

    private suspend fun writeResponseBodyToDisk(
        appContext: Context,
        body: ResponseBody,
        downloadEntity: DownloadEntity,
        tmpPath: String,
        callback: DownloadCallback
    ) {
        withContext(Dispatchers.IO) {
            try {

                val raf = RandomAccessFile(File(tmpPath), "rw")

                var fileLength = 0L
                var fileSeek = 0L

                if (downloadEntity.supportRange) {
                    fileLength = raf.length() + 1
                    fileSeek = raf.length()
                }

                raf.setLength(fileLength)
                raf.seek(fileSeek)

                val inputStream = body.byteStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead: Long = fileSeek
                val totalBytes:Long = downloadEntity.sizeTotal

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (totalBytes > 0) {
                        callback.onProgressUpdate(fetchProgress(totalBytesRead, totalBytes), downloadEntity.id)
                    } else {
                        callback.onInfiniteProgressUpdate(totalBytesRead.toMegabytes(), downloadEntity.id)
                    }

                    // Check if the download has been canceled
                    if (cancellationFlags.containsKey(downloadEntity.id) && cancellationFlags[downloadEntity.id]?.get() == true) {

                        raf.close()
                        inputStream.close()
                        body.close()

                        removeIdFromMap(downloadEntity.id)

                        withContext(Dispatchers.Main) {
                            callback.onDownloadCancelled(downloadEntity.id)
                        }
                        return@withContext
                    }
                }

                raf.close()
                inputStream.close()
                body.close()

                removeIdFromMap(downloadEntity.id)

                callback.onDownloadComplete(tmpPath, downloadEntity.id)
            }
            catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    callback.onDownloadFailed("Failed to write the file to disk: ${e.message}", downloadEntity.id)
                }
            }
        }
    }

    private fun removeIdFromMap(downloadId: Int) {
        cancellationFlags.remove(downloadId)
        downloadCalls.remove(downloadId)
        downloadJobs.remove(downloadId)
    }

}
