package com.bimalghara.filedownloader.domain.use_case

import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource

/**
 * Created by BimalGhara
 */

class RequestFileDetailsFromNetworkUseCase(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val downloaderRepositorySource: DownloaderRepositorySource
) {

    /*operator fun invoke(appContext: Context, url: Editable?, selectedDirectory: String?): Flow<ResourceWrapper<FileDetails>> = flow {
        emit(ResourceWrapper.Loading())

        if(url.isNullOrEmpty() || !url.toString().startsWith("https://")){
            emit(ResourceWrapper.Error(CustomException(cause = ERROR_EMPTY_FIELDS)))
        } else if(selectedDirectory.isNullOrEmpty()){
            emit(ResourceWrapper.Error(CustomException(cause = ERROR_INVALID_DIRECTORY)))
        } else {
            try {
                val videoDetails = downloaderRepositorySource.requestFileDetailsFromNetwork(appContext, url.toString())
                emit(ResourceWrapper.Success(data = videoDetails))
            } catch (e: CustomException) {
                emit(ResourceWrapper.Error(e))
            }
        }
    }.flowOn(dispatcherProviderSource.io)*/


}