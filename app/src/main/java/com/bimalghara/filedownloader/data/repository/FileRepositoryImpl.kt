package com.bimalghara.filedownloader.data.repository

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.broadcast.LocalMessageSender
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.DownloadsDao
import com.bimalghara.filedownloader.data.local.preferences.DataStoreSource
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.network.retrofit.service.ApiServiceDownload
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.domain.repository.FileRepositorySource
import com.bimalghara.filedownloader.notification.AppNotificationManager
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.deleteDownloadedFile
import com.bimalghara.filedownloader.utils.FileUtil.deleteTmpFile
import com.bimalghara.filedownloader.utils.FileUtil.getMimeType
import com.bimalghara.filedownloader.utils.FunUtil.createFileDetailsFromHeaders
import com.bimalghara.filedownloader.utils.FunUtil.wakeUpDownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject


/**
 * Created by BimalGhara
 */

class FileRepositoryImpl @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val serviceGenerator: ApiServiceGenerator,
    private val downloadsDao: DownloadsDao,
    private val dataStoreSource: DataStoreSource,
) : BaseRepositoryImpl(dispatcherProviderSource, downloadsDao), FileRepositorySource {
    private val logTag = javaClass.simpleName


    override suspend fun addQueue(
        appContext: Context,
        wifiOnly: Boolean,
        fileDetails: FileDetails,
        destinationUri: Uri
    ): ResourceWrapper<Boolean> {
        try {
            val mimeType = getMimeType(fileDetails.fileExtension!!)
            if(mimeType.isNullOrBlank()){
                return ResourceWrapper.Error(appContext.getStringFromResource(R.string.error_invalid_mime_type))
            }

            val data = DownloadEntity(
                url = fileDetails.requestUrl,
                wifiOnly = wifiOnly,
                destinationUri = destinationUri.toString(),
                name = "${fileDetails.fileName}.${fileDetails.fileExtension}",//name + ext
                mimeType = mimeType,
                size = fileDetails.contentLength ?: 0,//in bytes [total]
                supportRange = !fileDetails.acceptRanges.isNullOrBlank(),
                downloadStatus = DownloadStatus.WAITING.name,
                updatedAt = System.currentTimeMillis()
            )

            val id = downloadsDao.addDownload(data)
            if(id <= 0){
                return ResourceWrapper.Error(appContext.getStringFromResource(R.string.error_failed_to_queue))
            }

            //start service
            wakeUpDownloadService(appContext, action = NotificationAction.DOWNLOAD_START.name)

            return ResourceWrapper.Success(data = true)

        } catch (e: Exception){
            e.printStackTrace()
            return ResourceWrapper.Error(e.localizedMessage ?: appContext.getStringFromResource(R.string.error_failed_to_queue))
        }
    }

    override suspend fun reAddIntoQueue(
        appContext: Context,
        existingId: Int,
        restartDownloadEntity: DownloadEntity
    ) {

        val id = downloadsDao.addDownload(restartDownloadEntity)
        if(id > 0) {
            downloadsDao.deleteDownload(existingId)

            //start service
            wakeUpDownloadService(appContext, action = NotificationAction.DOWNLOAD_START.name)
        }

    }

    override suspend fun pauseFromQueue(downloadId: Int) {
        downloadsDao.updateDownloadEnd(
            downloadId,
            DownloadStatus.PAUSED.name,
            0,
            InterruptedBy.USER.name,
            System.currentTimeMillis()
        )
    }

    override suspend fun removeDownload(
        context: Context,
        downloadId: Int,
        downloadStatus: String
    ) {
        when (downloadStatus) {
            DownloadStatus.DOWNLOADING.name -> {
                LocalMessageSender.sendMessageToBackground(context, action = NotificationAction.DOWNLOAD_CANCEL.name, downloadId = downloadId)
            }
            DownloadStatus.COMPLETED.name -> {
                deleteDownloadedFile(context, downloadsDao, downloadId)
                downloadsDao.deleteDownload(downloadId)
            }
            else -> {
                deleteTmpFile(context, downloadsDao, downloadId)
                downloadsDao.deleteDownload(downloadId)
                val appNotificationManager = AppNotificationManager.from(context)
                appNotificationManager.cancelNotification(downloadId)
            }
        }
    }

    override fun requestDownloadsFromLocal(): Flow<List<DownloadEntity>> {
        return downloadsDao.getDownloads().flowOn(dispatcherProviderSource.io)
    }

    override suspend fun requestFileDetailsFromNetwork(
        context: Context,
        url: String
    ): Flow<ResourceWrapper<FileDetails>>  = flow {
        emit(ResourceWrapper.Loading())
        try {
            val fileInfoService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

            val response = fileInfoService.getFileDetails(url).execute()
            if (response.isSuccessful) {

                val fileDetails = createFileDetailsFromHeaders(url, response.headers())

                emit(ResourceWrapper.Success(data = fileDetails))
            } else {
                emit(ResourceWrapper.Error(context.getStringFromResource(R.string.network_not_supported)))
            }

        } catch (e: Exception) {
            emit(ResourceWrapper.Error(e.localizedMessage ?: context.getStringFromResource(R.string.error_default)))
        }
    }.flowOn(dispatcherProviderSource.io)


}

