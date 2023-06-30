package com.bimalghara.filedownloader.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.data.network.DownloadCallback
import com.bimalghara.filedownloader.data.repository.DownloadRepositoryImpl
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.notification.AppNotificationManager
import com.bimalghara.filedownloader.utils.FileUtil.copyFileToUri
import com.bimalghara.filedownloader.utils.NetworkConnectivity
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
    lateinit var networkConnectivity: NetworkConnectivity
    private var networkStatusLiveData = NetworkConnectivity.Status.Unavailable

    private var notificationManager: AppNotificationManager? = null

    private var tempFilePath: String? = null

    private val openQueuedList:MutableList<DownloadEntity> = arrayListOf()

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("action")
            if (data != null) {
                when (data) {
                    "POP_DOWNLOAD" -> {
                        Log.e(logTag, "pop Download")
                        actionPopDownload()
                    }
                    "DOWNLOAD_PAUSE" -> {
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionPauseDownload(downloadId)
                        } else Log.w(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
                    }
                    "DOWNLOAD_CANCEL" -> {
                        val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                        if (downloadId != -1) {
                            actionPauseDownload(downloadId)

                            //delete the temp file so that the process starts from beginning
                            if (tempFilePath != null) {
                                val tempFile = File(tempFilePath!!)
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                            }
                        } else Log.w(logTag, "onReceive: DOWNLOAD_PAUSE => DOWNLOAD_ID INVALID")
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
                networkStatusLiveData = it
                if(it == NetworkConnectivity.Status.WIFI || it == NetworkConnectivity.Status.CELLULAR){
                    actionPopDownload()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.e(logTag, "Download Service startId => $startId")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(logTag, "onDestroy: CALLED" )

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

        job.cancel()
    }

    private fun actionPopDownload() = coroutineScope.launch {
        openQueuedList.clear()
        openQueuedList.addAll(downloadRepository.getOpenQueuedList())

        openQueuedList.forEach { queued ->
            Log.w(logTag, "queued => ${queued.toString()}" )

        }
    }

    private fun startFileDownload(downloadEntity: DownloadEntity, targetDocumentFile: DocumentFile) {
        Log.e(logTag, "downloading... id => ${downloadEntity.id}")

        val fileCachePath = noBackupFilesDir?.path + "/downloads"
        val fileCacheDir = File(fileCachePath)
        if (!fileCacheDir.exists()){
            fileCacheDir.mkdir()
        }

        tempFilePath = fileCacheDir.path + "/" + downloadEntity.id + "_" + downloadEntity.name
        val file = File(tempFilePath!!)
        var fileCreated = file.exists()
        if (!fileCreated){
            fileCreated =  file.createNewFile()
        }

        if (fileCreated){
            downloadRepository.downloadFile(baseContext, downloadEntity, tempFilePath!!,  object : DownloadCallback {

                override fun onDownloadStarted(initialProgress: Int, downloadId: Int) {
                    Log.e(logTag, "onDownloadStarted() => progress => $initialProgress, downloadId => $downloadId")

                    notificationManager?.showFileDownloadNotification(initialProgress, downloadId)
                }

                override fun onDownloadCancelled(downloadId: Int) {
                    Log.e(logTag, "onDownloadCancelled() => downloadId => $downloadId")
                }

                override fun onInfiniteProgressUpdate(downloadedData: String, downloadId: Int) {
                    Log.e(logTag, "onInfiniteDownloadProgress() => $downloadedData, downloadId => $downloadId")
                }

                override fun onProgressUpdate(progress: Int, downloadId: Int) {
                    Log.e(logTag, "onProgressUpdate: $progress, downloadId => $downloadId")

                    notificationManager?.showFileDownloadNotification(progress, downloadId)
                }

                override fun onDownloadComplete(tmpPath: String, downloadId: Int) {
                    Log.e(logTag, "onDownloadComplete: $tmpPath, downloadId => $downloadId")

                    notificationManager?.showFileDownloadNotification(100, downloadId)

                    val contentFile = targetDocumentFile.createFile(downloadEntity.mimeType, downloadEntity.name)
                    if (contentFile != null && contentFile.exists()) {

                        val fileCopied = copyFileToUri(baseContext, tmpPath, contentFile.uri)
                        Log.e(logTag, "onDownloadComplete: copied result => $fileCopied")

                        if (fileCopied) {
                            val cacheFile = File(tmpPath)
                            if (cacheFile.exists()) {
                                cacheFile.delete()
                            } else {
                                Log.e(logTag, "onDownloadComplete: temp file not exists")
                            }
                        }
                    } else {
                        Log.e(logTag, "onDownloadComplete: output file not created")
                    }
                }

                override fun onDownloadFailed(errorMessage: String, downloadId: Int) {
                    Log.e(logTag, "onDownloadFailed: $errorMessage, downloadId => $downloadId")
                }
            })
        }
    }

    fun actionPauseDownload(downloadId: Int?) = coroutineScope.launch {
        downloadRepository.cancelDownload(downloadId)
    }


}