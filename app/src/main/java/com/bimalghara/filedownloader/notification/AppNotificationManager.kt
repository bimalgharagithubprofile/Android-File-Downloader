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
import java.lang.ref.WeakReference

object AppNotificationManager {
    private const val CHANNEL_FILE_DOWNLOAD = "file_download_channel"
    private var notificationManager: NotificationManager? = null
    private var appContext: WeakReference<Context>? = null

    fun from(context: Context) : AppNotificationManager {
        this.appContext = WeakReference(context)
        createNotificationChannels()

        return this
    }

    private fun createNotificationChannels() {
        appContext?.get()?.let { context ->
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
    }

    fun showFileDownloadNotification(notificationData: NotificationData) {
        appContext?.get()?.let { context ->
            val notificationLayoutSmall =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    createRemoteViewsSmall(context, notificationData)
                } else {
                    createRemoteViewsSmallOld(context, notificationData)
                }
            val notificationLayoutSmallLarge = createRemoteViewsLarge(context, notificationData)

            val builder = NotificationCompat.Builder(context, CHANNEL_FILE_DOWNLOAD)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCustomContentView(notificationLayoutSmall)
                .setCustomBigContentView(notificationLayoutSmallLarge)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationData.id, builder)
        }
    }

    private fun createRemoteViewsSmall(context: Context, notificationData: NotificationData): RemoteViews {
        return  RemoteViews(context.packageName, R.layout.notification_layout_small).apply {
            setProgressBar(
                R.id.progressIndicator_small, 100, notificationData.progress, notificationData.isIndeterminate
            )

            if(notificationData.eta!=null)
                setTextViewText(R.id.tv_notific_small_eta, notificationData.eta)

        }
    }

    private fun createRemoteViewsSmallOld(context: Context, notificationData: NotificationData): RemoteViews {
        return  RemoteViews(context.packageName, R.layout.notification_layout_small_old).apply {
            setProgressBar(
                R.id.progressIndicator_small_old, 100, notificationData.progress, notificationData.isIndeterminate
            )

            if(notificationData.eta!=null)
                setTextViewText(R.id.tv_notific_small_old_eta, notificationData.eta)

        }
    }

    private fun createRemoteViewsLarge(context: Context, notificationData: NotificationData): RemoteViews {
        return  RemoteViews(context.packageName, R.layout.notification_layout_large).apply {
            setProgressBar(
                R.id.progressIndicator_large, 100, notificationData.progress, notificationData.isIndeterminate
            )

            if(notificationData.eta!=null)
                setTextViewText(R.id.tv_notific_large_eta, notificationData.eta)

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
        if(appContext?.get() != null ) {
            val intent = Intent(appContext!!.get()!!, NotificationActionReceiver::class.java)
            intent.action = action
            intent.putExtra("DOWNLOAD_ID", notificationId)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    appContext!!.get()!!,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getBroadcast(
                    appContext!!.get()!!,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }
        return null
    }

    fun cancelAllNotifications(){
        notificationManager?.cancelAll()
    }

    fun cancelNotification(notificationId: Int){
        notificationManager?.cancel(notificationId)
    }
}

