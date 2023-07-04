package com.bimalghara.filedownloader.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.broadcast.LocalMessageSender
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.preferences.DataStoreSource
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.repository.DownloadRepositoryImpl
import com.bimalghara.filedownloader.domain.model.ProgressData
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.notification.AppNotificationManager
import com.bimalghara.filedownloader.notification.model.NotificationData
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.copyFileToUri
import com.bimalghara.filedownloader.utils.FileUtil.toSize
import com.bimalghara.filedownloader.utils.FunUtil.calculateETA
import com.bimalghara.filedownloader.utils.FunUtil.refreshDownloadService
import com.bimalghara.filedownloader.utils.FunUtil.toSpeed
import com.bimalghara.filedownloader.utils.Logger.logs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.*
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {
    private val logTag = "DownloadService"

    @Inject
    lateinit var dispatcherProviderSource: DispatcherProviderSource
    @Inject
    lateinit var downloadRepository: DownloadRepositoryImpl
    @Inject
    lateinit var dataStoreSource: DataStoreSource
    @Inject
    lateinit var networkConnectivity: NetworkConnectivity

    private var notificationManager = AppNotificationManager

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("action")
            if (context!= null && data != null) {
                when (data) {
                    NotificationAction.DOWNLOAD_START.name -> {
                        logs(logTag, "DOWNLOAD_START")
                        actionDownload(PopType.START.name)
                    }
                    NotificationAction.DOWNLOAD_RESUME.name -> {
                        logs(logTag, "DOWNLOAD_RESUME")
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionResume(downloadId)
                        }
                    }
                    NotificationAction.DOWNLOAD_RESUME_ALL.name -> {
                        logs(logTag, "DOWNLOAD_RESUME_ALL")
                        actionDownload(PopType.RESUME_ALL.name)
                    }
                    NotificationAction.DOWNLOAD_PAUSE.name -> {
                        logs(logTag, "DOWNLOAD_PAUSE")
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionPause(downloadId)
                        } else logs(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
                    }
                    NotificationAction.DOWNLOAD_PAUSE_ALL.name -> {
                        logs(logTag, "DOWNLOAD_PAUSE_ALL")
                        actionPauseAll()
                    }
                    NotificationAction.DOWNLOAD_CANCEL.name -> {
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionCancel(context, downloadId)
                        } else logs(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
                    }
                }
            }
        }
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = AppNotificationManager.from(this)

        val filter = IntentFilter("${baseContext?.packageName}.NOTIFICATION_BROAD_CAST")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val chan = NotificationChannel(
                packageName, "foreground_notification", NotificationManager.IMPORTANCE_LOW
            )

            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)

            val notificationBuilder = NotificationCompat.Builder(this, packageName)
                .setOngoing(true)
                .setContentTitle("Checking for pending downloads")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1111111111, notificationBuilder.build(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1111111111, notificationBuilder.build())
            }
        }

        coroutineScope.launch {
            networkConnectivity.observe().collectLatest {
                Log.i(logTag, "observe network status: $it")
                downloadRepository.networkStatusLive.postValue(it)
                when(it.first){
                    NetworkConnectivity.Status.WIFI -> actionDownload(PopType.RESUME_WIFI.name)
                    NetworkConnectivity.Status.CELLULAR -> actionDownload(PopType.RESUME_CELLULAR.name)
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logs(logTag, "Download Service startId => $startId")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logs(logTag, "onDestroy: CALLED" )

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

        job.cancel()
    }

    private fun actionDownload(action: String) = coroutineScope.launch {
        logs(logTag, "action Download [$action]")
        try {
            val settingParallelDownload =
                dataStoreSource.getString(DS_KEY_SETTING_PARALLEL_DOWNLOAD)?.toInt()
                    ?: DEFAULT_PARALLEL_DOWNLOAD_LIMIT.toInt()
            logs(logTag, "setting Parallel Download => $settingParallelDownload")

            var availableParallelDownload = settingParallelDownload

            val openQueuedList = downloadRepository.getOpenQueuedList()

            val groupedQueuedItems = openQueuedList.groupBy { it.downloadStatus }
            val downloadingQueuedItems = groupedQueuedItems[DownloadStatus.DOWNLOADING.name] ?: emptyList()
            val pausedQueuedItems = groupedQueuedItems[DownloadStatus.PAUSED.name] ?: emptyList()
            var waitingQueuedItems = groupedQueuedItems[DownloadStatus.WAITING.name] ?: emptyList()

            val groupedPausedQueuedItems = pausedQueuedItems.groupBy { it.interruptedBy }
            var userInterruptedItems = groupedPausedQueuedItems[InterruptedBy.USER.name] ?: emptyList()
            val wifiInterruptedItems = groupedPausedQueuedItems[InterruptedBy.NO_WIFI.name] ?: emptyList()
            var cellularInterruptedItems = groupedPausedQueuedItems[InterruptedBy.NO_NETWORK.name] ?: emptyList()

            //slot -> all in-progress [downloading & -in-queue]
            var totInProgress = downloadingQueuedItems.size.plus(waitingQueuedItems.size)
            if (totInProgress >= settingParallelDownload) {
                logs(logTag, "slot-available: 0 Downloading: ${downloadingQueuedItems.size} waiting: ${waitingQueuedItems.size}")
                availableParallelDownload = 0
            } else {
                availableParallelDownload = availableParallelDownload.minus(totInProgress)
                if(availableParallelDownload<0) availableParallelDownload=0
                logs(logTag, "slot-available: $availableParallelDownload already downloading: ${downloadingQueuedItems.size} already waiting ${waitingQueuedItems.size}")
            }

            when(action) {
                PopType.RESUME_WIFI.name -> {
                    var networkInterruptedItems = wifiInterruptedItems + cellularInterruptedItems

                    //slot available
                    var allowResumeWifiAndAnyNetwork = 0
                    if(networkInterruptedItems.size >= availableParallelDownload){
                        allowResumeWifiAndAnyNetwork = availableParallelDownload
                        logs(logTag, "slot-available: 0 resume wifi+any: $allowResumeWifiAndAnyNetwork")
                    } else {
                        allowResumeWifiAndAnyNetwork = networkInterruptedItems.size
                        logs(logTag, "slot-available: $availableParallelDownload resuming wifi+any-network :)")
                    }
                    logs(logTag, "resume wifi+any-network: $allowResumeWifiAndAnyNetwork")

                    // start all waiting for wifi form queue as per slot
                    networkInterruptedItems = networkInterruptedItems.sortedByDescending { it.updatedAt }
                    for (rwan in networkInterruptedItems.indices){
                        val item = networkInterruptedItems[rwan]
                        if(rwan < allowResumeWifiAndAnyNetwork) {
                            downloadFileFromNetwork(this@DownloadService, item)
                        } else {
                            downloadRepository.putInWaiting(item.id) //put in waiting
                            notificationManager.cancelNotification(item.id)
                        }
                    }
                }
                PopType.RESUME_CELLULAR.name -> {
                    //slot available
                    var allowResumeAnyNetwork = 0
                    if(cellularInterruptedItems.size >= availableParallelDownload){
                        allowResumeAnyNetwork = availableParallelDownload
                        logs(logTag, "slot-available: 0 resume any-network: $allowResumeAnyNetwork")
                    } else {
                        allowResumeAnyNetwork = cellularInterruptedItems.size
                        logs(logTag, "slot-available: $availableParallelDownload resuming any-network :)")
                    }
                    logs(logTag, "resume any-network: $allowResumeAnyNetwork")

                    // start all waiting for network form queue as per slot
                    cellularInterruptedItems = cellularInterruptedItems.sortedByDescending { it.updatedAt }
                    for (rn in cellularInterruptedItems.indices){
                        val item = cellularInterruptedItems[rn]
                        if(rn < allowResumeAnyNetwork) {
                            downloadFileFromNetwork(this@DownloadService, item)
                        } else {
                            downloadRepository.putInWaiting(item.id) //put in waiting
                            notificationManager.cancelNotification(item.id)
                        }
                    }
                }
                PopType.RESUME_ALL.name -> {

                    //slot available
                    var allowResumeALl = 0
                    if(userInterruptedItems.size >= availableParallelDownload){
                        allowResumeALl = availableParallelDownload
                        logs(logTag, "slot-available: 0 resume all: $availableParallelDownload")
                    } else {
                        allowResumeALl = userInterruptedItems.size
                        logs(logTag, "slot-available: $allowResumeALl resume all :)")
                    }
                    logs(logTag, "resume: all: $allowResumeALl")

                    // resume all form queue as per slot
                    userInterruptedItems = userInterruptedItems.sortedByDescending { it.updatedAt }
                    for (ra in userInterruptedItems.indices){
                        val item = userInterruptedItems[ra]
                        if(ra < allowResumeALl) {
                            if (item.wifiOnly) {
                                if (downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI)
                                    downloadFileFromNetwork(this@DownloadService, item)
                                else {
                                    logs(
                                        logTag,
                                        "resume: all: Failed [selected only over WiFi and WiFi not available at this moment]"
                                    )
                                    downloadRepository.putInWaiting(item.id) //put in waiting
                                    notificationManager.cancelNotification(item.id)
                                }
                            } else {
                                if (downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI || downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.CELLULAR)
                                    downloadFileFromNetwork(this@DownloadService, item)
                                else {
                                    logs(
                                        logTag,
                                        "resume: all: Failed [both WiFi and Cellular not available at this moment]"
                                    )
                                    downloadRepository.putInWaiting(item.id) //put in waiting
                                    notificationManager.cancelNotification(item.id)
                                }
                            }
                        } else {
                            downloadRepository.putInWaiting(item.id) //put in waiting
                            notificationManager.cancelNotification(item.id)
                        }
                    }
                }
                PopType.START.name -> {
                    //slot -> all in-progress [downloading & waiting-in-queue & waiting-for-wifi]
                    totInProgress = totInProgress.plus(wifiInterruptedItems.size)
                    if (totInProgress >= settingParallelDownload) {
                        logs(logTag, "slot-available: 0 Downloading: ${downloadingQueuedItems.size} waiting-in-queue: ${waitingQueuedItems.size} waiting-for-wifi: ${wifiInterruptedItems.size}")
                        availableParallelDownload = 0
                    } else {
                        availableParallelDownload = availableParallelDownload.minus(wifiInterruptedItems.size)
                        if(availableParallelDownload<0) availableParallelDownload=0
                        logs(logTag, "slot-available: $availableParallelDownload already downloading: ${downloadingQueuedItems.size} already waiting-in-queue: ${waitingQueuedItems.size} already waiting-for-wifi: ${wifiInterruptedItems.size}")
                    }

                    //slot full -> total in-progress
                    if(availableParallelDownload == 0) {
                        return@launch
                    }

                    //slot available
                    var allowNew = 0
                    if(waitingQueuedItems.size >= availableParallelDownload){
                        allowNew = availableParallelDownload
                        logs(logTag, "slot-available: 0 starting: $waitingQueuedItems")
                    } else {
                        allowNew = waitingQueuedItems.size
                        logs(logTag, "slot-available: $allowNew starting :)")
                    }
                    logs(logTag, "start: new: $allowNew")

                    // start all new waiting form queue as per slot
                    waitingQueuedItems = waitingQueuedItems.sortedByDescending { it.updatedAt }
                    for (n in 0 until allowNew){
                        val item = waitingQueuedItems[n]
                        if(item.wifiOnly){
                            if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI)
                                downloadFileFromNetwork(this@DownloadService, item)
                            else {
                                logs(
                                    logTag,
                                    "start: new: Failed [selected only over WiFi and WiFi not available at this moment]"
                                )
                                //pause no-wifi
                                downloadRepository.updateDownloadPaused(item.id, 0, InterruptedBy.NO_WIFI)
                            }
                        } else {
                            if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI || downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.CELLULAR)
                                downloadFileFromNetwork(this@DownloadService, item)
                            else {
                                logs(
                                    logTag,
                                    "start: new: Failed [both WiFi and Cellular not available at this moment]"
                                )
                                //pause no-network
                                downloadRepository.updateDownloadPaused(item.id, 0, InterruptedBy.NO_NETWORK)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun actionResume(downloadId: Int) = coroutineScope.launch {
        val settingParallelDownload =
            dataStoreSource.getString(DS_KEY_SETTING_PARALLEL_DOWNLOAD)?.toInt()
                ?: DEFAULT_PARALLEL_DOWNLOAD_LIMIT.toInt()
        logs(logTag, "setting Parallel Download => $settingParallelDownload")

        var availableParallelDownload = settingParallelDownload

        val openQueuedList = downloadRepository.getOpenQueuedList()
        val pausedQueuedItem = openQueuedList.filter { it.id == downloadId }.singleOrNull()
        if(pausedQueuedItem != null) {
            val groupedQueuedItems = openQueuedList.groupBy { it.downloadStatus }
            val downloadingQueuedItems = groupedQueuedItems[DownloadStatus.DOWNLOADING.name] ?: emptyList()
            val pausedQueuedItems = groupedQueuedItems[DownloadStatus.PAUSED.name] ?: emptyList()
            val waitingQueuedItems = groupedQueuedItems[DownloadStatus.WAITING.name] ?: emptyList()
            val groupedPausedQueuedItems = pausedQueuedItems.groupBy { it.interruptedBy }
            val wifiInterruptedItems = groupedPausedQueuedItems[InterruptedBy.NO_WIFI.name] ?: emptyList()
            val networkInterruptedItems = groupedPausedQueuedItems[InterruptedBy.NO_NETWORK.name] ?: emptyList()

            //slot -> all in-progress [downloading & waiting-in-queue & waiting-for-wifi & waiting-for-any-network]
            val totInProgress = (downloadingQueuedItems.size + waitingQueuedItems.size + wifiInterruptedItems.size + networkInterruptedItems.size)
            if (totInProgress >= settingParallelDownload) {
                logs(
                    logTag,
                    "slot-available: 0 Downloading: ${downloadingQueuedItems.size} waiting: ${waitingQueuedItems.size} waiting-for-wifi: ${wifiInterruptedItems.size} waiting-for-any-network: ${networkInterruptedItems.size}"
                )
                availableParallelDownload = 0
            } else {
                availableParallelDownload = availableParallelDownload.minus(totInProgress)
                if(availableParallelDownload<0) availableParallelDownload=0
                logs(
                    logTag,
                    "slot-available: $availableParallelDownload already downloading: ${downloadingQueuedItems.size} already waiting-in-queue: ${waitingQueuedItems.size} already waiting-for-wifi: ${wifiInterruptedItems.size} already waiting-for-any-network: ${networkInterruptedItems.size}"
                )

            }

            //slot full -> total in-progress
            if(availableParallelDownload == 0) {
                downloadRepository.putInWaiting(downloadId) //put in waiting
                notificationManager.cancelNotification(downloadId)
            } else {
                if(pausedQueuedItem.wifiOnly){
                    if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI){
                        downloadFileFromNetwork(this@DownloadService, pausedQueuedItem)
                    } else {
                        logs(
                            logTag,
                            "resume: User: Failed - no wifi [${downloadRepository.networkStatusLive.value?.first}]"
                        )
                        //pause no-wifi
                        downloadRepository.updateDownloadPaused(downloadId, 0, InterruptedBy.NO_WIFI)
                    }
                } else {
                    if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI || downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.CELLULAR) {
                        downloadFileFromNetwork(this@DownloadService, pausedQueuedItem)
                    } else {
                        logs(
                            logTag,
                            "resume: User: Failed - no network [${downloadRepository.networkStatusLive.value?.first}]"
                        )
                        //pause no-network
                        downloadRepository.updateDownloadPaused(downloadId, 0, InterruptedBy.NO_NETWORK)
                    }
                }
            }
        } else {
            notificationManager.cancelNotification(downloadId)
        }
    }
    private fun actionPause(downloadId: Int) = coroutineScope.launch {
        downloadRepository.pauseDownload(downloadId)
    }
    private fun actionPauseAll() = coroutineScope.launch {
        downloadRepository.pauseAllDownload()
    }
    private fun actionCancel(context: Context, downloadId: Int) = coroutineScope.launch {
        downloadRepository.cancelDownload(context, downloadId)
    }

    private fun downloadFileFromNetwork(appContext:Context, downloadEntity: DownloadEntity) = coroutineScope.launch(dispatcherProviderSource.io) {
        logs(logTag, "process download... id => ${downloadEntity.id}")

        val fileCacheDir = File(appContext.noBackupFilesDir?.path + "/downloads")

        if (!fileCacheDir.exists()){
            fileCacheDir.mkdir()
        }

        val tempFilePath = fileCacheDir!!.path + "/" + downloadEntity.id + "_" + downloadEntity.name
        val file = File(tempFilePath)
        var fileCreated = file.exists()
        if (!fileCreated){
            fileCreated =  file.createNewFile()
        }

        if (fileCreated){
            logs(logTag, "tmpFile opened")
            downloadRepository.downloadFile(baseContext, downloadEntity, tempFilePath,  object : DownloadCallback {

                override fun onDownloadStarted(initialProgress: Int, downloadId: Int, name: String) {
                    logs(logTag, "onDownloadStarted() => progress => $initialProgress, downloadId => $downloadId")
                    notificationManager.cancelNotification(downloadId)
                    val downloadSpeed = (downloadRepository.networkStatusLive.value?.second ?: 0L).toSpeed()
                    val notificationData = NotificationData(
                        id = downloadId,
                        status = NotificationStatus.STARTED,
                        name = name,
                        progress = initialProgress,
                        speed = downloadSpeed
                    )
                    notificationManager.showFileDownloadNotification(notificationData)
                }

                override fun onDownloadPaused(downloadId: Int, lastProgress: Int, name: String) {
                    logs(logTag, "onDownloadPaused() => downloadId => $downloadId")
                    notificationManager.cancelNotification(downloadId)
                    //val isIndeterminate = lastProgress == -1
                    val notificationData = NotificationData(
                        id = downloadId,
                        status = NotificationStatus.PAUSED,
                        name = name,
                        progress = lastProgress,
                        //isIndeterminate = isIndeterminate
                    )
                    notificationManager.showFileDownloadNotification(notificationData)
                }

                override fun onDownloadCancelled(downloadId: Int) {
                    coroutineScope.launch(dispatcherProviderSource.io) {
                        logs(logTag, "onDownloadCancelled() => downloadId => $downloadId")
                        refreshDownloadService(
                            appContext = appContext,
                            action = NotificationAction.DOWNLOAD_START.name
                        )
                        delay(500)
                        notificationManager.cancelNotification(downloadId)
                    }
                }

                override fun onInfiniteProgressUpdate(downloadedSize: Long, downloadId: Int, name: String) {
                    coroutineScope.launch(dispatcherProviderSource.io) {
                        logs(
                            logTag,
                            "onInfiniteDownloadProgress() => $downloadedSize, downloadId => $downloadId"
                        )
                        val downloadSpeed =
                            (downloadRepository.networkStatusLive.value?.second ?: 0L).toSpeed()
                        val notificationData = NotificationData(
                            id = downloadId,
                            status = NotificationStatus.IN_PROGRESS,
                            name = name,
                            actionData = downloadedSize.toSize(" "),
                            speed = downloadSpeed,
                            isIndeterminate = true
                        )
                        val progressData = ProgressData(
                            id = downloadId,
                            actionData = downloadedSize.toSize(""),
                            isIndeterminate = true
                        )
                        LocalMessageSender.sendMessageToForeground(
                            context = appContext,
                            progressData = progressData
                        )
                        notificationManager.showFileDownloadNotification(notificationData)
                    }
                }

                override fun onProgressUpdate(progress: Int, downloadId: Int, name: String, totalSize: Long, downloadedSize: Long) {
                    coroutineScope.launch(dispatcherProviderSource.io) {
                        logs(
                            logTag,
                            "onProgressUpdate: $progress, downloadId => $downloadId, totalSize => $totalSize, downloadedSize => $downloadedSize"
                        )
                        val downloadSpeed =
                            (downloadRepository.networkStatusLive.value?.second ?: 0L).toSpeed()
                        val eta = calculateETA(
                            (downloadRepository.networkStatusLive.value?.second ?: 0L),
                            totalSize,
                            downloadedSize
                        )
                        val notificationData = NotificationData(
                            id = downloadId,
                            status = NotificationStatus.IN_PROGRESS,
                            name = name,
                            actionData = "${downloadedSize.toSize(" ")} of ${totalSize.toSize(" ")}",
                            progress = progress,
                            speed = downloadSpeed,
                            eta = "$eta Left"
                        )
                        val progressData = ProgressData(
                            id = downloadId,
                            progress = progress,
                            actionData = "${downloadedSize.toSize("")} / ${totalSize.toSize("")}"
                        )
                        LocalMessageSender.sendMessageToForeground(
                            context = appContext,
                            progressData = progressData
                        )
                        notificationManager.showFileDownloadNotification(notificationData)
                    }
                }

                override fun onDownloadComplete(tmpPath: String, downloadId: Int) {
                    coroutineScope.launch(dispatcherProviderSource.io) {
                        logs(
                            logTag,
                            "onDownloadComplete: downloadId => $downloadId | tmpPath=>$tmpPath"
                        )

                        val targetDocumentFileUri: Uri? = Uri.parse(downloadEntity.destinationUri)
                        val targetDocumentFile: DocumentFile? =
                            targetDocumentFileUri?.let { DocumentFile.fromTreeUri(appContext, it) }

                        if (targetDocumentFile == null) {
                            logs(
                                logTag,
                                "onDownloadComplete: fail to open targetDocumentFile => ${downloadEntity.destinationUri}"
                            )
                            return@launch
                        }

                        val contentFile = targetDocumentFile.createFile(
                            downloadEntity.mimeType,
                            downloadEntity.name
                        )
                        if (contentFile != null && contentFile.exists()) {

                            val fileCopied = copyFileToUri(baseContext, tmpPath, contentFile.uri)
                            logs(logTag, "onDownloadComplete: copied result => $fileCopied")

                            if (fileCopied.first) {

                                downloadRepository.updateDownloadedFileUri(
                                    downloadId,
                                    fileCopied.second,
                                    contentFile.uri.toString()
                                )

                                val tmpFile = File(tmpPath)
                                if (tmpFile.exists()) tmpFile.delete()
                            }
                        } else logs(logTag, "onDownloadComplete: output file not created")

                        refreshDownloadService(
                            appContext = appContext,
                            action = NotificationAction.DOWNLOAD_START.name
                        )
                        delay(500)
                        notificationManager.cancelNotification(downloadId)
                    }
                }

                override fun onDownloadFailed(errorMessage: String, downloadId: Int) {
                    coroutineScope.launch(dispatcherProviderSource.io) {
                        logs(logTag, "onDownloadFailed: $errorMessage, downloadId => $downloadId")
                        refreshDownloadService(
                            appContext = appContext,
                            action = NotificationAction.DOWNLOAD_START.name
                        )
                        delay(500)
                        notificationManager.cancelNotification(downloadId)
                    }
                }
            })
        } else{
            logs(logTag, "tmpFile not opened")
        }
    }

}