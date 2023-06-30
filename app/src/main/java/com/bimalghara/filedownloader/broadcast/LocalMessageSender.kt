package com.bimalghara.filedownloader.broadcast

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class LocalMessageSender(private val mContext: Context?) {

    private var messageIntent: Intent? = null

    init {
        messageIntent = Intent("${mContext?.packageName}.NOTIFICATION_BROAD_CAST")
    }

    fun sendMessage(action: String, downloadId: Int?) {

        if (messageIntent != null && mContext != null) {

            messageIntent?.putExtra("action", action)
            messageIntent?.putExtra("DOWNLOAD_ID", downloadId)

            LocalBroadcastManager.getInstance(mContext).sendBroadcast(messageIntent!!)
        }

    }
}