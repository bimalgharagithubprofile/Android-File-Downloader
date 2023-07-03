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
import com.bimalghara.filedownloader.utils.FileUtil.deleteTmpFile
import com.bimalghara.filedownloader.utils.FunUtil.fetchProgress
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
) : BaseRepositoryImpl(dispatcherProviderSource, downloadsDao) {
    private val logTag = javaClass.simpleName

    private val coroutineScope = CoroutineScope(dispatcherProviderSource.io)

    private val downloadJobs = mutableMapOf<Int, Job>()
    private val downloadCalls = mutableMapOf<Int, Call<ResponseBody>>()
    private val downloadCallbacks = mutableMapOf<Int, DownloadCallback>()
    private val pauseFlags = mutableMapOf<Int, AtomicBoolean>()
    private val pauseAllFlags = AtomicBoolean(false)
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
       if (downloadJobs.containsKey(downloadEntity.id)) {
           logs(logTag, "downloading... [ALREADY RUNNING] id => ${downloadEntity.id}")
           return
        }
        logs(logTag, "downloading... [NEWLY RUNNING] id => ${downloadEntity.id}")

        val downloadService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

        downloadCallbacks[downloadEntity.id] = callback
        pauseFlags[downloadEntity.id] = AtomicBoolean(false)
        pauseAllFlags.set(false)
        cancellationFlags[downloadEntity.id] = AtomicBoolean(false)

        downloadJobs[downloadEntity.id] = coroutineScope.launch(dispatcherProviderSource.io) {

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
            callback.onDownloadStarted(initialProgress, downloadEntity.id, downloadEntity.name)

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
                if (networkStatusLive.value?.first == NetworkConnectivity.Status.Lost) {
                    logs(logTag, "Download broke WiFi lost: ${e.message} [${networkStatusLive.value}]")
                    val interruptedBy = if(downloadEntity.wifiOnly) InterruptedBy.NO_WIFI else InterruptedBy.NO_NETWORK
                    updateDownloadPaused(downloadEntity.id, 0, interruptedBy)
                    callback.onDownloadPaused(downloadEntity.id, 0, downloadEntity.name)
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

        val pauseFlag = pauseFlags[downloadId]
        pauseFlag?.set(true)
    }

    fun pauseAllDownload() {
        logs(logTag, "pausing All Download")

        pauseAllFlags.set(true)
    }

    fun cancelDownload(context: Context, downloadId: Int) {
        logs(logTag, "canceling Download: $downloadId")

        //check if already paused | if yes -> cancel right away
        val pauseFlags = pauseFlags[downloadId]
        if(pauseFlags == null){
            logs(logTag, "canceling Download case: already paused")
            val callback = downloadCallbacks[downloadId]

            coroutineScope.launch(dispatcherProviderSource.io) {
                deleteTmpFile(context, downloadsDao, downloadId)
            }


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
        context: Context,
        body: ResponseBody,
        downloadEntity: DownloadEntity,
        tmpPath: String,
        callback: DownloadCallback
    ) = withContext(dispatcherProviderSource.io) {
        var lastProgress = 0
        val tempFile = File(tmpPath)
        val raf = RandomAccessFile(tempFile, "rw")
        val inputStream = body.byteStream()

        try {
            var fileLength = 0L
            var fileSeek = 0L

            if (downloadEntity.supportRange) {
                fileLength = raf.length() + 1
                fileSeek = raf.length()
            }

            raf.setLength(fileLength)
            raf.seek(fileSeek)

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
                        callback.onProgressUpdate(currentProgress, downloadEntity.id, downloadEntity.name, totalBytes, totalBytesRead)
                        previousProgress = currentProgress
                        lastProgress = currentProgress
                    }
                } else {
                    callback.onInfiniteProgressUpdate(totalBytesRead, downloadEntity.id, downloadEntity.name)
                }

                // paused all
                if(pauseAllFlags.get()){
                    logs(logTag, "Force pause all")

                    updateDownloadPaused(downloadEntity.id, lastProgress, InterruptedBy.USER)
                    callback.onDownloadPaused(downloadEntity.id, lastProgress, downloadEntity.name)

                    cancelJob(downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)

                    return@withContext
                }
                // paused
                if (pauseFlags.containsKey(downloadEntity.id) && pauseFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag, "Force pause")

                    updateDownloadPaused(downloadEntity.id, lastProgress, InterruptedBy.USER)
                    callback.onDownloadPaused(downloadEntity.id, lastProgress, downloadEntity.name)

                    cancelJob(downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)

                    return@withContext
                }
                // canceled
                if (cancellationFlags.containsKey(downloadEntity.id) && cancellationFlags[downloadEntity.id]?.get() == true) {
                    logs(logTag, "Force cancel")

                    updateDownloadCanceled(downloadEntity.id)
                    callback.onDownloadCancelled(downloadEntity.id)

                    cancelJob(downloadEntity.id)
                    removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)

                    deleteTmpFile(context, downloadsDao, downloadEntity.id)

                    return@withContext
                }
            }

            removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
            updateDownloadCompleted(downloadEntity.id)
            callback.onDownloadComplete(tmpPath, downloadEntity.id)
        } catch (e: IOException) {
            delay(100)
            if (networkStatusLive.value?.first == NetworkConnectivity.Status.Lost) {
                logs(logTag, "WiFi lost: ${e.message} [${networkStatusLive.value}]")
                val interruptedBy = if(downloadEntity.wifiOnly) InterruptedBy.NO_WIFI else InterruptedBy.NO_NETWORK
                updateDownloadPaused(downloadEntity.id, lastProgress, interruptedBy)
                callback.onDownloadPaused(downloadEntity.id, lastProgress, downloadEntity.name)
                removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_PAUSE)
            } else {
                logs(logTag, "Failed to write the file to disk: ${e.message} [${networkStatusLive.value}]")
                updateDownloadFailed(downloadEntity.id)
                callback.onDownloadFailed("Failed to write the file to disk: ${e.message} [${networkStatusLive.value}]", downloadEntity.id)
                removeIdFromMap(downloadEntity.id, NotificationAction.DOWNLOAD_CANCEL)
            }
        } finally {
            logs(logTag, "Finally -> Closing -> write the file to disk: ${downloadEntity.id}")
            try {
                raf.close()
                inputStream.close()
                body.close()
            }catch (ce: Exception){
                logs(logTag, "Finally -> Closing -> failed: [${downloadEntity.id}] :: ${ce.message}")
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


    private fun updateDownloadStarted(id: Int, initialProgress: Int) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            logs(logTag, "updateDownloadStarted: id=> $id ($initialProgress)")
            downloadsDao.updateDownloadProgress(
                id,
                DownloadStatus.DOWNLOADING.name,
                null,
                System.currentTimeMillis()
            )
        }

    private fun updateDownloadCompleted(id: Int) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            logs(logTag, "updateDownloadCompleted: id=> $id")
            downloadsDao.updateDownloadProgress(
                id,
                DownloadStatus.COMPLETED.name,
                null,
                System.currentTimeMillis()
            )
        }

    private fun updateDownloadFailed(id: Int) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            logs(logTag, "updateDownloadFailed: id=> $id")
            downloadsDao.updateDownloadEnd(
                id,
                DownloadStatus.FAILED.name,
                0,
                null,
                System.currentTimeMillis()
            )
        }

    fun updateDownloadPaused(id: Int, lastProgress: Int, interruptedBy: InterruptedBy) =
        coroutineScope.launch(dispatcherProviderSource.io) {
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
