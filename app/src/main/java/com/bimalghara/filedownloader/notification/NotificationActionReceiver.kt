package com.bimalghara.filedownloader.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bimalghara.filedownloader.broadcast.LocalMessageSender

class NotificationActionReceiver : BroadcastReceiver() {

    private val TAG = "NotificationActionReceiver"

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent != null) {

            if (intent.action == "DOWNLOAD_PAUSE" || intent.action == "DOWNLOAD_CANCEL") {

                val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)

                // Pass the downloadId to localMessageSender
                LocalMessageSender(context).sendMessage(intent.action!!, downloadId)
            }
        }
    }

}