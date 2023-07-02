package com.bimalghara.filedownloader.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.notification.model.NotificationData
import com.bimalghara.filedownloader.utils.NotificationAction
import com.bimalghara.filedownloader.utils.NotificationStatus

class AppNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_FILE_DOWNLOAD = "file_download_channel"
    }

    var notificationManager: NotificationManager? = null

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fileDownloadChannel = NotificationChannel(
                CHANNEL_FILE_DOWNLOAD,
                "File Download",
                NotificationManager.IMPORTANCE_LOW
            )

            fileDownloadChannel.lightColor = Color.GREEN
            fileDownloadChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            notificationManager?.createNotificationChannel(fileDownloadChannel)
        }
    }

    fun showFileDownloadNotification(notificationData: NotificationData) {

        val notificationLayoutSmall = createRemoteViewsSmall(notificationData)
        val notificationLayoutSmallLarge = createRemoteViewsLarge(notificationData)

        val builder = NotificationCompat.Builder(context, CHANNEL_FILE_DOWNLOAD)
            .setSmallIcon(R.drawable.logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCustomContentView(notificationLayoutSmall)
            .setCustomBigContentView(notificationLayoutSmallLarge)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationData.id, builder)
    }

    private fun createRemoteViewsSmall(notificationData: NotificationData): RemoteViews {
        return  RemoteViews(context.packageName, R.layout.notification_layout_small).apply {
            setProgressBar(
                R.id.progressIndicator_small, 100, notificationData.progress, notificationData.isIndeterminate
            )

            setTextViewText(R.id.tv_notific_small_eta, "EE:EE Left")

            if (notificationData.status == NotificationStatus.PAUSED) {
                setViewVisibility(R.id.btn_notific_small_resume, View.VISIBLE)
                setViewVisibility(R.id.btn_notific_small_pause, View.GONE)
            } else {
                setViewVisibility(R.id.btn_notific_small_resume, View.GONE)
                setViewVisibility(R.id.btn_notific_small_pause, View.VISIBLE)
            }

            setOnClickPendingIntent(
                R.id.btn_notific_small_resume,
                getPendingIntent(NotificationAction.DOWNLOAD_RESUME.name, notificationData.id)
            )
            setOnClickPendingIntent(
                R.id.btn_notific_small_pause,
                getPendingIntent(NotificationAction.DOWNLOAD_PAUSE.name, notificationData.id)
            )
            setOnClickPendingIntent(
                R.id.btn_notific_small_cancel,
                getPendingIntent(NotificationAction.DOWNLOAD_CANCEL.name, notificationData.id)
            )
        }
    }

    private fun createRemoteViewsLarge(notificationData: NotificationData): RemoteViews {
        return  RemoteViews(context.packageName, R.layout.notification_layout_large).apply {
            setProgressBar(
                R.id.progressIndicator_large, 100, notificationData.progress, notificationData.isIndeterminate
            )

            setTextViewText(R.id.tv_notific_large_eta, "EE:EE Left")
            setTextViewText(R.id.tv_notofic_name, notificationData.name)
            if(notificationData.actionData!=null){
                setTextViewText(R.id.tv_notofic_action, notificationData.actionData)
                setViewVisibility(R.id.tv_notofic_action, View.VISIBLE)
            } else {
                setViewVisibility(R.id.tv_notofic_action, View.GONE)
            }
            if(notificationData.speed!=null){
                setTextViewText(R.id.tv_notofic_speed, notificationData.speed)
                setViewVisibility(R.id.tv_notofic_speed, View.VISIBLE)
                setViewVisibility(R.id.iv_notofic_separator, View.VISIBLE)
            } else {
                setViewVisibility(R.id.tv_notofic_speed, View.GONE)
                setViewVisibility(R.id.iv_notofic_separator, View.GONE)
            }

            if (notificationData.status == NotificationStatus.PAUSED) {
                setViewVisibility(R.id.btn_notific_large_resume, View.VISIBLE)
                setViewVisibility(R.id.btn_notific_large_pause, View.GONE)
            } else {
                setViewVisibility(R.id.btn_notific_large_resume, View.GONE)
                setViewVisibility(R.id.btn_notific_large_pause, View.VISIBLE)
            }

            setOnClickPendingIntent(
                R.id.btn_notific_large_resume,
                getPendingIntent(NotificationAction.DOWNLOAD_RESUME.name, notificationData.id)
            )
            setOnClickPendingIntent(
                R.id.btn_notific_large_pause,
                getPendingIntent(NotificationAction.DOWNLOAD_PAUSE.name, notificationData.id)
            )
            setOnClickPendingIntent(
                R.id.btn_notific_large_cancel,
                getPendingIntent(NotificationAction.DOWNLOAD_CANCEL.name, notificationData.id)
            )
        }
    }

    private fun getPendingIntent(action: String, notificationId: Int): PendingIntent? {
        val intent = Intent(context, NotificationActionReceiver::class.java)
        intent.action = action
        intent.putExtra("DOWNLOAD_ID", notificationId)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    fun cancelAllNotifications(){
        notificationManager?.cancelAll()
    }

    fun cancelNotification(notificationId: Int){
        notificationManager?.cancel(notificationId)
    }
}
