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

        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_layout)

        notificationLayout.setProgressBar(
            R.id.progressIndicator, 100, notificationData.progress, notificationData.isIndeterminate
        )

        notificationLayout.setOnClickPendingIntent(
            R.id.resume_button,
            getPendingIntent(NotificationAction.DOWNLOAD_RESUME.name, notificationData.id)
        )

        notificationLayout.setOnClickPendingIntent(
            R.id.pause_button,
            getPendingIntent(NotificationAction.DOWNLOAD_PAUSE.name, notificationData.id)
        )

        notificationLayout.setOnClickPendingIntent(
            R.id.cancel_button,
            getPendingIntent(NotificationAction.DOWNLOAD_CANCEL.name, notificationData.id)
        )

        notificationLayout.setTextViewText(R.id.tvName, notificationData.name)

        if(notificationData.eta!=null){
            notificationLayout.setTextViewText(R.id.tvETA, notificationData.eta)
            notificationLayout.setViewVisibility(R.id.tvETA, View.VISIBLE)
        } else {
            notificationLayout.setViewVisibility(R.id.tvETA, View.GONE)
        }

        if(notificationData.actionData!=null){
            notificationLayout.setTextViewText(R.id.tvAction, notificationData.actionData)
            notificationLayout.setViewVisibility(R.id.tvAction, View.VISIBLE)
        } else {
            notificationLayout.setViewVisibility(R.id.tvAction, View.GONE)
        }

        if(notificationData.speed!=null){
            notificationLayout.setTextViewText(R.id.tvSpeed, notificationData.speed)
            notificationLayout.setViewVisibility(R.id.tvSpeed, View.VISIBLE)
            notificationLayout.setViewVisibility(R.id.tvSeparator, View.VISIBLE)
        } else {
            notificationLayout.setViewVisibility(R.id.tvSpeed, View.GONE)
            notificationLayout.setViewVisibility(R.id.tvSeparator, View.GONE)
        }

        if (notificationData.status == NotificationStatus.PAUSED) {
            notificationLayout.setViewVisibility(R.id.resume_button, View.VISIBLE)
            notificationLayout.setViewVisibility(R.id.pause_button, View.GONE)
        } else {
            notificationLayout.setViewVisibility(R.id.resume_button, View.GONE)
            notificationLayout.setViewVisibility(R.id.pause_button, View.VISIBLE)
        }



        val builder = NotificationCompat.Builder(context, CHANNEL_FILE_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCustomContentView(notificationLayout)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationData.id, builder)
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
