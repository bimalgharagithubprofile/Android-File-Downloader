package com.bimalghara.filedownloader.domain.repository

import android.content.Context
import com.bimalghara.filedownloader.domain.model.FileDetails


/**
 * Created by BimalGhara
 */

interface DownloaderRepositorySource {

    suspend fun requestFileDetailsFromNetwork(appContext: Context, url: String): FileDetails

    suspend fun requestDownloadFileFromNetwork(appContext: Context, url: String, ext: String, callback: (Triple<Int, String?, String>) -> Unit)


}