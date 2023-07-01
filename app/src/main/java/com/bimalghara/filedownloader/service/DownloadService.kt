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
import com.bimalghara.filedownloader.data.local.preferences.DataStoreSource
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.repository.DownloadRepositoryImpl
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.notification.AppNotificationManager
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.copyFileToUri
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
    lateinit var downloadRepository: DownloadRepositoryImpl
    @Inject
    lateinit var dataStore: DataStoreSource
    @Inject
    lateinit var networkConnectivity: NetworkConnectivity

    private var notificationManager: AppNotificationManager? = null

    private var fileCacheDir:File?=null

    private val openQueuedList:MutableList<DownloadEntity> = arrayListOf()

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("action")
            if (data != null) {
                when (data) {
                    "DOWNLOAD_START" -> {
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
                    NotificationAction.DOWNLOAD_PAUSE.name -> {
                        logs(logTag, "DOWNLOAD_PAUSE")
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionPause(downloadId)
                        } else logs(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
                    }
                    NotificationAction.DOWNLOAD_CANCEL.name -> {
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionCancel(downloadId)
                        } else logs(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
                    }
                    /*"STOP_ALL" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        }

                        //pause ongoing download
                        actionPauseDownload(null)

                        Handler(Looper.myLooper()!!).postDelayed({

                            //delete the temp file so that the process starts from beginning
                            if (tempFilePath != null) {
                                val tempFile = File(tempFilePath!!)
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                            }

                            //cancel notification
                            notificationManager?.cancelAllNotifications()

                            //stop all other processes
                            stopSelf()

                        }, 700)
                    }*/
                }
            }
        }
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        fileCacheDir = File(noBackupFilesDir?.path + "/downloads")

        notificationManager = AppNotificationManager(this)

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
                    NetworkConnectivity.Status.CELLULAR -> Unit
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        logs(logTag, "Download Service startId => $startId")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logs(logTag, "onDestroy: CALLED" )

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

        job.cancel()
    }

    private fun actionDownload(type: String) = coroutineScope.launch {
        logs(logTag, "action Download [$type]")
        try {
            val settingParallelDownload =
                dataStore.getString(DS_KEY_SETTING_PARALLEL_DOWNLOAD)?.toInt()
                    ?: DEFAULT_PARALLEL_DOWNLOAD_LIMIT.toInt()
            logs(logTag, "setting Parallel Download => $settingParallelDownload")

            var availableParallelDownload = settingParallelDownload

            openQueuedList.clear()
            openQueuedList.addAll(downloadRepository.getOpenQueuedList())

            val groupedQueuedItems = openQueuedList.groupBy { it.downloadStatus }
            val downloadingQueuedItems = groupedQueuedItems[DownloadStatus.DOWNLOADING.name] ?: emptyList()
            var pausedQueuedItems = groupedQueuedItems[DownloadStatus.PAUSED.name] ?: emptyList()
            var waitingQueuedItems = groupedQueuedItems[DownloadStatus.WAITING.name] ?: emptyList()

            val groupedPausedQueuedItems = pausedQueuedItems.groupBy { it.interruptedBy }
            var interruptedNoWiFiItems = groupedPausedQueuedItems[InterruptedBy.NO_WIFI.name] ?: emptyList()

            //slot full -> all downloading
            val totInProgress = downloadingQueuedItems.size.plus(pausedQueuedItems.size)
            if (totInProgress >= settingParallelDownload) {
                logs(logTag, "slot-available: 0 Downloading: ${downloadingQueuedItems.size} Paused: ${pausedQueuedItems.size}")
                return@launch
            } else {
                availableParallelDownload = availableParallelDownload.minus(totInProgress)
                if(availableParallelDownload<0) availableParallelDownload=0
                logs(logTag, "slot-available: $availableParallelDownload already downloading: ${downloadingQueuedItems.size} already paused: ${pausedQueuedItems.size}")
            }

            when(type) {
                PopType.RESUME_WIFI.name -> {
                    if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI){
                        interruptedNoWiFiItems = interruptedNoWiFiItems.sortedByDescending { it.updatedAt }
                        for(interruptedNoWiFiItem in interruptedNoWiFiItems){
                            if(interruptedNoWiFiItem.wifiOnly) {
                                downloadFileFromNetwork(this@DownloadService, interruptedNoWiFiItem)
                            } else
                                logs(logTag, "resume: wifi: Failed [not selected only over WiFi]")
                        }
                    } else logs(logTag, "resume: wifi: Failed - no wifi [${downloadRepository.networkStatusLive.value?.first}]")
                    /*var allowWifi = 0
                    if (interruptedNoWiFiItems.size >= availableParallelDownload) {
                        allowWifi = availableParallelDownload
                        availableParallelDownload = 0
                        logs(logTag, "slot-available: 0 waiting for Wifi: $availableParallelDownload")
                    } else {
                        allowWifi = interruptedNoWiFiItems.size
                        availableParallelDownload = availableParallelDownload.minus(interruptedNoWiFiItems.size)
                        logs(logTag, "slot-available: $availableParallelDownload waiting for Wifi: ${interruptedNoWiFiItems.size}")
                    }
                    logs(logTag, "Resume: wifi: $allowWifi")

                    // start all waiting for wifi as per slot
                    interruptedNoWiFiItems = interruptedNoWiFiItems.sortedByDescending { it.updatedAt }
                    for (n in 0 until allowWifi){
                        val item = interruptedNoWiFiItems[n]
                        if(item.wifiOnly) {
                            downloadFileFromNetwork(this@DownloadService, item)
                        } else
                            logs(logTag, "resume: wifi: Failed [not selected only over WiFi]")
                    }*/
                }
                /*PopType.RESUME_USER.name -> {
                    //slot full -> downloading & resumed waiting for wifi
                    if(availableParallelDownload <= 0) {
                        return@launch
                    }

                    //slot available
                    val downloadId = 1
                }*/
                PopType.START.name -> {
                    //slot full -> downloading & resumed waiting for wifi
                    if(availableParallelDownload <= 0) {
                        return@launch
                    }

                    //slot available
                    var allowNew = 0
                    if(waitingQueuedItems.size >= availableParallelDownload){
                        allowNew = availableParallelDownload
                        availableParallelDownload = 0
                        logs(logTag, "slot-available: 0 waiting for Wifi: $availableParallelDownload")
                    } else {
                        allowNew = waitingQueuedItems.size
                        availableParallelDownload = availableParallelDownload.minus(waitingQueuedItems.size)
                        logs(logTag, "slot-available: $availableParallelDownload waiting in queue: ${waitingQueuedItems.size}")
                    }
                    logs(logTag, "start: new: $allowNew")

                    // start all new waiting form queue as per slot
                    waitingQueuedItems = waitingQueuedItems.sortedByDescending { it.updatedAt }
                    for (n in 0 until allowNew){
                        val item = waitingQueuedItems[n]
                        if(item.wifiOnly){
                            if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI)
                                downloadFileFromNetwork(this@DownloadService, item)
                            else
                                logs(logTag, "start: new: Failed [selected only over WiFi and WiFi not available at this moment]")
                        } else {
                            if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI || downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.CELLULAR)
                                downloadFileFromNetwork(this@DownloadService, item)
                            else
                                logs(logTag, "start: new: Failed [both WiFi and Cellular not available at this moment]")
                        }
                    }
                }
            }

        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun actionResume(downloadId: Int) = coroutineScope.launch {
        val pausedQueuedItem = openQueuedList.single { it.id == downloadId }
        if(pausedQueuedItem.size > 0) {
            if(pausedQueuedItem.wifiOnly){
                if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI){
                    downloadFileFromNetwork(this@DownloadService, pausedQueuedItem)
                } else logs(logTag, "resume: User: Failed - no wifi [${downloadRepository.networkStatusLive.value?.first}]")
            } else {
                if(downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.WIFI || downloadRepository.networkStatusLive.value?.first == NetworkConnectivity.Status.CELLULAR) {
                    downloadFileFromNetwork(this@DownloadService, pausedQueuedItem)
                } else logs(logTag, "resume: User: Failed - no network [${downloadRepository.networkStatusLive.value?.first}]")
            }
        }
    }
    private fun actionPause(downloadId: Int) = coroutineScope.launch {
        downloadRepository.pauseDownload(downloadId)
    }
    private fun actionCancel(downloadId: Int) = coroutineScope.launch {
        downloadRepository.cancelDownload(downloadId)
    }

    private fun downloadFileFromNetwork(appContext:Context, downloadEntity: DownloadEntity) = coroutineScope.launch {
        logs(logTag, "process download... id => ${downloadEntity.id}")

        if(fileCacheDir == null) {
            logs(logTag, "downloading terminated [cache dir not found] id => ${downloadEntity.id}")
            return@launch
        }

        if (!fileCacheDir!!.exists()){
            fileCacheDir!!.mkdir()
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

                override fun onDownloadStarted(initialProgress: Int, downloadId: Int) {
                    logs(logTag, "onDownloadStarted() => progress => $initialProgress, downloadId => $downloadId")
                    notificationManager?.cancelNotification(downloadId)
                    notificationManager?.showFileDownloadNotification(initialProgress, downloadId, NotificationStatus.STARTED, false)
                }

                override fun onDownloadPaused(downloadId: Int, lastProgress: Int) {
                    logs(logTag, "onDownloadPaused() => downloadId => $downloadId")
                    notificationManager?.cancelNotification(downloadId)
                    val isIndeterminate = lastProgress <= 0
                    notificationManager?.showFileDownloadNotification(lastProgress,  downloadId, NotificationStatus.PAUSED, isIndeterminate)
                }

                override fun onDownloadCancelled(downloadId: Int) {
                    logs(logTag, "onDownloadCancelled() => downloadId => $downloadId")
                    notificationManager?.cancelNotification(downloadId)
                }

                override fun onInfiniteProgressUpdate(downloadedData: String, downloadId: Int) {
                    logs(logTag, "onInfiniteDownloadProgress() => $downloadedData, downloadId => $downloadId")
                    notificationManager?.showFileDownloadNotification(0, downloadId, NotificationStatus.IN_PROGRESS, true)
                }

                override fun onProgressUpdate(progress: Int, downloadId: Int) {
                    logs(logTag, "onProgressUpdate: $progress, downloadId => $downloadId")
                    notificationManager?.showFileDownloadNotification(progress, downloadId, NotificationStatus.IN_PROGRESS, false)
                }

                override fun onDownloadComplete(tmpPath: String, downloadId: Int) {
                    logs(logTag, "onDownloadComplete: downloadId => $downloadId | tmpPath=>$tmpPath")

                    val targetDocumentFileUri: Uri? = Uri.parse(downloadEntity.destinationUri)
                    val targetDocumentFile:DocumentFile? = targetDocumentFileUri?.let { DocumentFile.fromTreeUri(appContext, it) }

                    if(targetDocumentFile==null){
                        logs(logTag, "onDownloadComplete: fail to open targetDocumentFile => ${downloadEntity.destinationUri}")
                        return
                    }

                    val contentFile = targetDocumentFile.createFile(downloadEntity.mimeType, downloadEntity.name)
                    if (contentFile != null && contentFile.exists()) {

                        val fileCopied = copyFileToUri(baseContext, tmpPath, contentFile.uri)
                        logs(logTag, "onDownloadComplete: copied result => $fileCopied")

                        if (fileCopied.first) {

                            downloadRepository.updateDownloadedFileUri(downloadId, fileCopied.second, contentFile.uri.toString())

                            val tmpFile = File(tmpPath)
                            if (tmpFile.exists()) tmpFile.delete()
                        }
                    } else logs(logTag, "onDownloadComplete: output file not created")

                    notificationManager?.cancelNotification(downloadId)
                }

                override fun onDownloadFailed(errorMessage: String, downloadId: Int) {
                    logs(logTag, "onDownloadFailed: $errorMessage, downloadId => $downloadId")
                    notificationManager?.cancelNotification(downloadId)
                }
            })
        } else{
            logs(logTag, "tmpFile not opened")
        }
    }

}