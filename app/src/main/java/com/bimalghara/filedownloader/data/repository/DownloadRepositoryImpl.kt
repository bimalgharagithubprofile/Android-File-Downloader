package com.bimalghara.filedownloader.data.repository

import android.content.Context
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.DownloadsDao
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.network.retrofit.service.ApiServiceDownload
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.bimalghara.filedownloader.utils.FunUtil.fetchProgress
import com.bimalghara.filedownloader.utils.FunUtil.toMegabytes
import com.bimalghara.filedownloader.utils.InterruptedBy
import com.bimalghara.filedownloader.utils.Logger.logs
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
    private val logTag = "DownloadRepositoryImpl"

    private val coroutineScope = CoroutineScope(dispatcherProviderSource.io)

    private val downloadJobs = mutableMapOf<Int, Job>()
    private val downloadCalls = mutableMapOf<Int, Call<ResponseBody>>()
    private val pauseFlags = mutableMapOf<Int, AtomicBoolean>()
    private val cancellationFlags = mutableMapOf<Int, AtomicBoolean>()


    suspend fun getOpenQueuedList(): List<DownloadEntity>{
        return downloadsDao.getOpenQueuedList()
    }


    fun downloadFile(appContext: Context, downloadEntity: DownloadEntity, tmpPath: String, callback: DownloadCallback) {
        if (downloadJobs.containsKey(downloadEntity.id))
            return

        val downloadService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

        pauseFlags[downloadEntity.id] = AtomicBoolean(false)
        cancellationFlags[downloadEntity.id] = AtomicBoolean(false)

        downloadJobs[downloadEntity.id] = coroutineScope.launch {

            var destinationFileSize = 0L
            val outputFile = File(tmpPath)

            if (downloadEntity.supportRange && outputFile.exists())
                destinationFileSize = outputFile.length()

            val downloadCall = if (destinationFileSize > 0) {
                downloadService.downloadFileRange(
                    fileUrl = downloadEntity.url,
                    range = "bytes=$destinationFileSize-"
                )
            } else {
                downloadService.downloadFile(downloadEntity.url)
            }

            downloadCalls[downloadEntity.id] = downloadCall

            var initialProgress = 0
            if (destinationFileSize > 0 && downloadEntity.sizeTotal > 0) {
                initialProgress = fetchProgress(destinationFileSize, downloadEntity.sizeTotal)
            }

            updateDownloadStarted(downloadEntity.id, initialProgress)
            callback.onDownloadStarted(initialProgress, downloadEntity.id)

            try {
                val response = downloadCall.awaitResponse()
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        writeResponseBodyToDisk(appContext, responseBody, downloadEntity, tmpPath,callback)
                    } else {
                        removeIdFromMap(downloadEntity.id)
                        updateDownloadFailed(downloadEntity.id)
                        callback.onDownloadFailed("Empty response body.", downloadEntity.id)
                        logs(logTag, "Empty response body.")
                    }
                } else {
                    removeIdFromMap(downloadEntity.id)
                    updateDownloadFailed(downloadEntity.id)
                    callback.onDownloadFailed("Download failed with response code: ${response.code()}", downloadEntity.id)
                    logs(logTag, "Download failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                removeIdFromMap(downloadEntity.id)
                updateDownloadFailed(downloadEntity.id)
                callback.onDownloadFailed("Download failed: ${e.message}", downloadEntity.id)
                logs(logTag,"Download failed: ${e.message}")
            }
        }
    }

    fun pauseDownload(downloadId: Int) {
        logs(logTag, "pausing Download: $downloadId" )

        val pauseFlags = pauseFlags[downloadId]
        val downloadCall = downloadCalls[downloadId]
        val downloadJob = downloadJobs[downloadId]

        pauseFlags?.set(true)
        downloadCall?.cancel()
        downloadJob?.cancel()
    }
    fun cancelDownload(downloadId: Int) {
        logs(logTag, "canceling Download: $downloadId" )

        val cancellationFlag = cancellationFlags[downloadId]
        val downloadCall = downloadCalls[downloadId]
        val downloadJob = downloadJobs[downloadId]

        cancellationFlag?.set(true)
        downloadCall?.cancel()
        downloadJob?.cancel()
    }

    private suspend fun writeResponseBodyToDisk(
        appContext: Context,
        body: ResponseBody,
        downloadEntity: DownloadEntity,
        tmpPath: String,
        callback: DownloadCallback
    ) = withContext(dispatcherProviderSource.io) {
        try {
            val tempFile = File(tmpPath)
            val raf = RandomAccessFile(tempFile, "rw")

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

            var previousProgress = 0
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (totalBytes > 0) {
                    val currentProgress = fetchProgress(totalBytesRead, totalBytes)
                    if (currentProgress != previousProgress && currentProgress<100) {
                        updateDownloadProgress(downloadEntity.id, currentProgress)
                        callback.onProgressUpdate(currentProgress, downloadEntity.id)
                        previousProgress = currentProgress
                    }
                } else {
                    //updateDownloadProgress(downloadEntity.id, -100)//eeeeeeeee
                    callback.onInfiniteProgressUpdate(totalBytesRead.toMegabytes(), downloadEntity.id)
                }

                // paused
                if (pauseFlags.containsKey(downloadEntity.id) && pauseFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag,"Need to pause")
                    raf.close()
                    inputStream.close()
                    body.close()
                    removeIdFromMap(downloadEntity.id)
                    updateDownloadPaused(downloadEntity.id, InterruptedBy.USER)
                    callback.onDownloadPaused(downloadEntity.id)
                    return@withContext
                }
                // canceled
                if (cancellationFlags.containsKey(downloadEntity.id) && cancellationFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag,"Need to cancel")
                    raf.close()
                    inputStream.close()
                    body.close()
                    removeIdFromMap(downloadEntity.id)
                    if (tempFile.exists()) tempFile.delete()
                    updateDownloadCanceled(downloadEntity.id)
                    callback.onDownloadCancelled(downloadEntity.id)
                    return@withContext
                }
            }

            raf.close()
            inputStream.close()
            body.close()
            removeIdFromMap(downloadEntity.id)
            updateDownloadCompleted(downloadEntity.id)
            callback.onDownloadComplete(tmpPath, downloadEntity.id)
        } catch (e: IOException) {
            //eeeeeeeeeeee need mark either pasued by network or failed
            logs(logTag, "Failed to write the file to disk: ${e.message}")
            callback.onDownloadFailed("Failed to write the file to disk: ${e.message}", downloadEntity.id)
        }
    }

    private fun removeIdFromMap(downloadId: Int) {
        pauseFlags.remove(downloadId)
        cancellationFlags.remove(downloadId)
        downloadCalls.remove(downloadId)
        downloadJobs.remove(downloadId)
    }


    private suspend fun updateDownloadStarted(id: Int, initialProgress: Int) {
        logs(logTag, "updateDownloadStarted: id=> $id ($initialProgress)")
        downloadsDao.updateDownloadProgress(id, DownloadStatus.DOWNLOADING.name, initialProgress, System.currentTimeMillis())
    }
    private suspend fun updateDownloadProgress(id: Int, currentProgress: Int) {
        logs(logTag, "updateDownloadProgress: id=> $id ($currentProgress)")
        downloadsDao.updateDownloadProgress(id, DownloadStatus.DOWNLOADING.name, currentProgress, System.currentTimeMillis())
    }
    private suspend fun updateDownloadCompleted(id: Int) {
        logs(logTag, "updateDownloadCompleted: id=> $id")
        downloadsDao.updateDownloadProgress(id, DownloadStatus.COMPLETED.name, 100, System.currentTimeMillis())
    }
    private suspend fun updateDownloadPaused(id: Int, interruptedBy: InterruptedBy) {
        logs(logTag, "updateDownloadPaused: id=> $id | by=> ${interruptedBy.name}")
        downloadsDao.updateDownloadEnd(id, DownloadStatus.PAUSED.name, interruptedBy.name, System.currentTimeMillis())
    }
    private suspend fun updateDownloadCanceled(id: Int) {
        logs(logTag, "updateDownloadCanceled: id=> $id")
        downloadsDao.deleteDownload(id)
    }
    private suspend fun updateDownloadFailed(id: Int) {
        logs(logTag, "updateDownloadFailed: id=> $id")
        downloadsDao.updateDownloadEnd(id, DownloadStatus.PAUSED.name, null, System.currentTimeMillis())
    }
}
