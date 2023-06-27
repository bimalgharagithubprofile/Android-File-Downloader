package com.bimalghara.filedownloader.data.network


/**
 * Created by BimalGhara
 */

interface RemoteDataSource {


    suspend fun requestDownload(url: String, callback: DownloadCallback)


}
