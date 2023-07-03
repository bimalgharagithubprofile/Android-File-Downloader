package com.bimalghara.filedownloader.broadcast

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bimalghara.filedownloader.domain.model.ProgressData
import com.bimalghara.filedownloader.notification.model.NotificationData

object LocalMessageSender {

    fun sendMessageToBackground(context: Context, action: String, downloadId: Int? = null) {
        val messageIntent = Intent("${context.packageName}.NOTIFICATION_BROAD_CAST")

        messageIntent.putExtra("action", action)
        messageIntent.putExtra("DOWNLOAD_ID", downloadId)

        LocalBroadcastManager.getInstance(context).sendBroadcast(messageIntent)

    }

    fun sendMessageToForeground(context: Context, progressData: ProgressData) {
        //only if app is in foreground

        val messageIntent = Intent("${context.packageName}.NOTIFICATION_BROAD_CAST")

        messageIntent.putExtra("PROGRESS_DATA", progressData)

        LocalBroadcastManager.getInstance(context).sendBroadcast(messageIntent)

    }
}