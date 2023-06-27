package com.bimalghara.filedownloader.domain.use_case

import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource

/**
 * Created by BimalGhara
 */

class RequestDownloadVideoFromNetworkUseCase(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val downloaderRepositorySource: DownloaderRepositorySource
) {
    private val logTag = javaClass.simpleName


    /*operator fun invoke(appContext: Context, url: String?, ext: String?): Flow<ResourceWrapper<FileProcessState>> = callbackFlow {
        send(ResourceWrapper.Loading())

        if(url.isNullOrEmpty() || !url.toString().startsWith("https://")) {
            send(ResourceWrapper.Error(CustomException(cause = ERROR_INVALID_URL)))
        } else if(ext.isNullOrEmpty()) {
            send(ResourceWrapper.Error(CustomException(cause = ERROR_INVALID_EXTENSION)))
        } else {
            try {
                downloaderRepositorySource.requestDownloadFileFromNetwork(appContext, url, ext) {
                    Log.e(logTag, "requestDownloadVideoFromNetwork callback : ${it.first}")

                    val sizeArray = it.third.split("|")

                    val data = FileProcessState(
                        progress = it.first,
                        filePath = it.second,
                        currentSize = sizeArray[0].toLong(),
                        totalSize = sizeArray[1].toLong()
                    )
                    launch { send(ResourceWrapper.Success(data = data)) }
                }
            } catch (e: CustomException) {
                send(ResourceWrapper.Error(e))
            }
        }

        awaitClose { }

    }.distinctUntilChanged().flowOn(dispatcherProviderSource.io)*/




}