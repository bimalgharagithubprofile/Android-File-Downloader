package com.bimalghara.filedownloader.domain.repository

import android.content.Context
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.utils.ResourceWrapper
import kotlinx.coroutines.flow.Flow


/**
 * Created by BimalGhara
 */

interface DownloaderRepositorySource {

    suspend fun addQueue(
        appContext: Context,
        wifiOnly: Boolean,
        fileDetails: FileDetails,
        selectedDirectory: String
    ): ResourceWrapper<Boolean>

    suspend fun requestDownloadsFromLocal(): Flow<List<DownloadEntity>>

    suspend fun requestFileDetailsFromNetwork(appContext: Context, url: String): Flow<ResourceWrapper<FileDetails>>

    suspend fun requestDownloadFileFromNetwork(appContext: Context, url: String, ext: String, callback: (Triple<Int, String?, String>) -> Unit)

}