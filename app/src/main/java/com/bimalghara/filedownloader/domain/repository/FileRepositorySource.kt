package com.bimalghara.filedownloader.domain.repository

import android.content.Context
import android.net.Uri
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.utils.ResourceWrapper
import kotlinx.coroutines.flow.Flow


/**
 * Created by BimalGhara
 */

interface FileRepositorySource {

    suspend fun addQueue(
        appContext: Context,
        wifiOnly: Boolean,
        fileDetails: FileDetails,
        destinationUri: Uri
    ): ResourceWrapper<Boolean>

    suspend fun reAddIntoQueue(
        appContext: Context,
        existingId: Int,
        restartDownloadEntity: DownloadEntity
    )

    fun requestDownloadsFromLocal(): Flow<List<DownloadEntity>>

    suspend fun requestFileDetailsFromNetwork(appContext: Context, url: String): Flow<ResourceWrapper<FileDetails>>


}