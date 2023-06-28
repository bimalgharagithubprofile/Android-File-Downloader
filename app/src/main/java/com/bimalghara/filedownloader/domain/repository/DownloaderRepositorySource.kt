package com.bimalghara.filedownloader.domain.repository

import android.content.Context
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow


/**
 * Created by BimalGhara
 */

interface DownloaderRepositorySource {

    suspend fun requestDownloadsFromLocal(): Flow<List<DownloadEntity>>

    suspend fun requestFileDetailsFromNetwork(appContext: Context, url: String): FileDetails

    suspend fun requestDownloadFileFromNetwork(appContext: Context, url: String, ext: String, callback: (Triple<Int, String?, String>) -> Unit)


}