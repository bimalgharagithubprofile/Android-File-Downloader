package com.bimalghara.filedownloader.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bimalghara.filedownloader.R

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

    fun showFileDownloadNotification(progress: Int, notificationId: Int?) {
        val notificationLayout = NotificationCompat.Builder(context, CHANNEL_FILE_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCustomContentView(getCustomNotificationLayout(progress, notificationId))
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationId != null) {
            notificationManager.notify(notificationId, notificationLayout)
        }
    }

    private fun getCustomNotificationLayout(progress: Int, notificationId: Int?): RemoteViews {
        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_layout)
        notificationLayout.setProgressBar(R.id.progress_bar, 100, progress, false)
        notificationLayout.setOnClickPendingIntent(R.id.pause_button, getPendingIntent("DOWNLOAD_PAUSE", notificationId))
        notificationLayout.setOnClickPendingIntent(R.id.cancel_button, getPendingIntent("DOWNLOAD_CANCEL", notificationId))
        return notificationLayout
    }


    private fun getPendingIntent(action: String, notificationId: Int?): PendingIntent? {
        val intent = Intent(context, NotificationActionReceiver::class.java)
        intent.action = action
        intent.putExtra("DOWNLOAD_ID", notificationId)

        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancelAllNotifications(){
        notificationManager?.cancelAll()
    }

    fun cancelNotification(notificationId: Int){
        notificationManager?.cancel(notificationId)
    }
}
