package com.bimalghara.filedownloader.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
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
import com.bimalghara.filedownloader.utils.NetworkConnectivity
import com.bimalghara.filedownloader.utils.NotificationAction
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
    private val downloadCallbacks = mutableMapOf<Int, DownloadCallback>()
    private val pauseFlags = mutableMapOf<Int, AtomicBoolean>()
    private val cancellationFlags = mutableMapOf<Int, AtomicBoolean>()

    val networkStatusLive = MutableLiveData(Pair(NetworkConnectivity.Status.Unavailable, 0L))


    suspend fun getOpenQueuedList(): List<DownloadEntity> {
        return downloadsDao.getOpenQueuedList()
    }


    fun downloadFile(
        appContext: Context,
        downloadEntity: DownloadEntity,
        tmpPath: String,
        callback: DownloadCallback
    ) {
        logs(logTag, "downloading... id => ${downloadEntity.id}")

        if (downloadJobs.containsKey(downloadEntity.id))
            return

        val downloadService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

        downloadCallbacks[downloadEntity.id] = callback
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
            if (destinationFileSize > 0 && downloadEntity.size > 0) {
                initialProgress = fetchProgress(destinationFileSize, downloadEntity.size)
            }

            updateDownloadStarted(downloadEntity.id, initialProgress)
            callback.onDownloadStarted(initialProgress, downloadEntity.id)

            try {
                val response = downloadCall.awaitResponse()
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        writeResponseBodyToDisk(
                            appContext,
                            responseBody,
                            downloadEntity,
                            tmpPath,
                            callback
                        )
                    } else {
                        removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
                        updateDownloadFailed(downloadEntity.id)
                        callback.onDownloadFailed("Empty response body.", downloadEntity.id)
                        logs(logTag, "Empty response body.")
                    }
                } else {
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
                    updateDownloadFailed(downloadEntity.id)
                    callback.onDownloadFailed(
                        "Download failed with response code: ${response.code()}",
                        downloadEntity.id
                    )
                    logs(logTag, "Download failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                delay(100)
                if (networkStatusLive.value?.first != NetworkConnectivity.Status.WIFI && downloadEntity.wifiOnly) {
                    logs(logTag, "Download broke WiFi lost: ${e.message} [${networkStatusLive.value}]")
                    updateDownloadPaused(downloadEntity.id, 1, InterruptedBy.NO_WIFI)
                    callback.onDownloadPaused(downloadEntity.id, 1)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)
                } else {
                    logs(logTag, "Download failed: ${e.message} [${networkStatusLive.value}]")
                    updateDownloadFailed(downloadEntity.id)
                    callback.onDownloadFailed("Download failed: ${e.message} [${networkStatusLive.value}]", downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
                }
            }
        }
    }

    fun pauseDownload(downloadId: Int) {
        logs(logTag, "pausing Download: $downloadId")

        val pauseFlags = pauseFlags[downloadId]
        pauseFlags?.set(true)
    }

    fun cancelDownload(downloadId: Int) {
        logs(logTag, "canceling Download: $downloadId")

        //check if already paused | if yes -> cancel right away
        val pauseFlags = pauseFlags[downloadId]
        if(pauseFlags == null){
            logs(logTag, "canceling Download case: paused")
            val callback = downloadCallbacks[downloadId]

            //eeeeee
            //if (tempFile.exists()) tempFile.delete()

            updateDownloadCanceled(downloadId)
            callback?.onDownloadCancelled(downloadId)

            removeIdFromMap(downloadId, NotificationAction.DOWNLOAD_CANCEL)
        } else {
            logs(logTag, "canceling Download case: running")
            val cancellationFlag = cancellationFlags[downloadId]
            cancellationFlag?.set(true)
        }
    }

    private suspend fun writeResponseBodyToDisk(
        appContext: Context,
        body: ResponseBody,
        downloadEntity: DownloadEntity,
        tmpPath: String,
        callback: DownloadCallback
    ) = withContext(dispatcherProviderSource.io) {
        var lastProgress = 0
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
            val totalBytes: Long = downloadEntity.size

            var previousProgress = 0
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (totalBytes > 0) {
                    val currentProgress = fetchProgress(totalBytesRead, totalBytes)
                    if (currentProgress != previousProgress && currentProgress < 100) {
                        callback.onProgressUpdate(currentProgress, downloadEntity.id)
                        previousProgress = currentProgress
                        lastProgress = currentProgress
                    }
                } else {
                    callback.onInfiniteProgressUpdate(
                        totalBytesRead.toMegabytes(),
                        downloadEntity.id
                    )
                }

                // paused
                if (pauseFlags.containsKey(downloadEntity.id) && pauseFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag, "Force pause")

                    raf.close()
                    inputStream.close()
                    body.close()

                    updateDownloadPaused(downloadEntity.id, lastProgress, InterruptedBy.USER)
                    callback.onDownloadPaused(downloadEntity.id, lastProgress)

                    cancelJob(downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)

                    return@withContext
                }
                // canceled
                if (cancellationFlags.containsKey(downloadEntity.id) && cancellationFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag, "Force cancel")

                    raf.close()
                    inputStream.close()
                    body.close()

                    if (tempFile.exists()) tempFile.delete()
                    updateDownloadCanceled(downloadEntity.id)
                    callback.onDownloadCancelled(downloadEntity.id)

                    cancelJob(downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)

                    return@withContext
                }
            }

            raf.close()
            inputStream.close()
            body.close()
            removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)
            updateDownloadCompleted(downloadEntity.id)
            callback.onDownloadComplete(tmpPath, downloadEntity.id)
        } catch (e: IOException) {
            delay(100)
            if (networkStatusLive.value?.first != NetworkConnectivity.Status.WIFI && downloadEntity.wifiOnly) {
                logs(logTag, "WiFi lost: ${e.message} [${networkStatusLive.value}]")
                updateDownloadPaused(downloadEntity.id, lastProgress, InterruptedBy.NO_WIFI)
                callback.onDownloadPaused(downloadEntity.id, lastProgress)
                removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)
            } else {
                logs(logTag, "Failed to write the file to disk: ${e.message} [${networkStatusLive.value}]")
                updateDownloadFailed(downloadEntity.id)
                callback.onDownloadFailed("Failed to write the file to disk: ${e.message} [${networkStatusLive.value}]", downloadEntity.id)
                removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
            }
        }
    }

    private fun cancelJob(downloadId: Int){
        logs(logTag, "Clearing stack: $downloadId")

        val downloadCall = downloadCalls[downloadId]
        val downloadJob = downloadJobs[downloadId]

        downloadCall?.cancel()
        downloadJob?.cancel()
    }

    private fun removeIdFromMap(downloadId: Int, action: NotificationAction) {
        if(action == NotificationAction.DOWNLOAD_CANCEL)
            downloadCallbacks.remove(downloadId)

        pauseFlags.remove(downloadId)
        cancellationFlags.remove(downloadId)
        downloadCalls.remove(downloadId)
        downloadJobs.remove(downloadId)
    }


    private suspend fun updateDownloadStarted(id: Int, initialProgress: Int) {
        logs(logTag, "updateDownloadStarted: id=> $id ($initialProgress)")
        downloadsDao.updateDownloadProgress(
            id,
            DownloadStatus.DOWNLOADING.name,
            null,
            System.currentTimeMillis()
        )
    }

    private suspend fun updateDownloadCompleted(id: Int) {
        logs(logTag, "updateDownloadCompleted: id=> $id")
        downloadsDao.updateDownloadProgress(
            id,
            DownloadStatus.COMPLETED.name,
            null,
            System.currentTimeMillis()
        )
    }

    private suspend fun updateDownloadFailed(id: Int) {
        logs(logTag, "updateDownloadFailed: id=> $id")
        downloadsDao.updateDownloadEnd(
            id,
            DownloadStatus.FAILED.name,
            0,
            null,
            System.currentTimeMillis()
        )
    }

    private suspend fun updateDownloadPaused(id: Int, lastProgress: Int, interruptedBy: InterruptedBy) {
        logs(logTag, "updateDownloadPaused: id=> $id | lastProgress=> ${lastProgress}| by=> ${interruptedBy.name}")
        downloadsDao.updateDownloadEnd(
            id,
            DownloadStatus.PAUSED.name,
            lastProgress,
            interruptedBy.name,
            System.currentTimeMillis()
        )
    }


    fun updateDownloadedFileUri(id: Int, size: Long, downloadedUri:String) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            logs(logTag, "updateDownloadedFilePath: id=> $id")
            downloadsDao.updateDownloadedFileUri(
                id,
                size,
                downloadedUri,
                System.currentTimeMillis()
            )
        }

    private fun updateDownloadCanceled(id: Int) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            logs(logTag, "updateDownloadCanceled: id=> $id")
            downloadsDao.deleteDownload(id)
        }
}
