package com.bimalghara.filedownloader.data.repository

import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.DownloadsDao
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.bimalghara.filedownloader.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

open class BaseRepositoryImpl(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val downloadsDao: DownloadsDao
) {
    private val logTag = javaClass.simpleName

    private val coroutineScope = CoroutineScope(dispatcherProviderSource.io)


    fun putInWaiting(id: Int) =
        coroutineScope.launch(dispatcherProviderSource.io) {
            Logger.logs(logTag, "putInWaiting: id=> $id")
            downloadsDao.updateDownloadWaiting(
                id,
                DownloadStatus.WAITING.name,
                System.currentTimeMillis()
            )
        }
}