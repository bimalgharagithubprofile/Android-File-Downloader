package com.bimalghara.filedownloader.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bimalghara.filedownloader.broadcast.LocalMessageSender
import com.bimalghara.filedownloader.utils.Logger.logs

class NotificationActionReceiver : BroadcastReceiver() {

    private val TAG = "NotificationActionReceiver"

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent != null) {

            if (intent.action == "DOWNLOAD_PAUSE"
                || intent.action == "DOWNLOAD_CANCEL"
                || intent.action == "DOWNLOAD_RESUME"
            ) {

                val downloadId = intent.getIntExtra("DOWNLOAD_ID", -1)
                if(downloadId != -1) {
                    LocalMessageSender(context).sendMessage(intent.action!!, downloadId)
                } else logs(TAG,"download id not found from pendingIntent action!")
            }
        }
    }

}