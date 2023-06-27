package com.bimalghara.filedownloader.data.network


import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.network.retrofit.service.ApiServiceDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import javax.inject.Inject

/**
 * Created by BimalGhara
 */

class RemoteDataImpl @Inject constructor(
    private val serviceGenerator: ApiServiceGenerator
) : RemoteDataSource {

    override suspend fun requestDownload(url: String, callback: DownloadCallback) {
        val videoService = serviceGenerator.createApiService(ApiServiceDownload::class.java)

        val call = videoService.downloadVideo(url)

        try {
            val response = call.awaitResponse()
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    callback.onDataReceive(responseBody, callback)
                } else {
                    withContext(Dispatchers.Main) {
                        callback.onDownloadFailed("Empty response body.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback.onDownloadFailed("Download failed with response code: ${response.code()}")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback.onDownloadFailed("Download failed: ${e.message}")
            }
        }
    }


}
