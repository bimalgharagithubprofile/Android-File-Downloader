package com.bimalghara.filedownloader.domain.di

import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource
import com.bimalghara.filedownloader.domain.use_case.RequestDownloadVideoFromNetworkUseCase
import com.bimalghara.filedownloader.domain.use_case.RequestFileDetailsFromNetworkUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Created by BimalGhara
 */

@InstallIn(SingletonComponent::class)
@Module
class DomainModule {

    @Provides
    fun provideGetFileDetailsFromNetworkUseCase(dispatcherProviderSource: DispatcherProviderSource, downloaderRepositorySource: DownloaderRepositorySource): RequestFileDetailsFromNetworkUseCase {
        return RequestFileDetailsFromNetworkUseCase(dispatcherProviderSource = dispatcherProviderSource, downloaderRepositorySource = downloaderRepositorySource)
    }
    @Provides
    fun provideDownloadFileFromNetworkUseCase(dispatcherProviderSource: DispatcherProviderSource, downloaderRepositorySource: DownloaderRepositorySource): RequestDownloadVideoFromNetworkUseCase {
        return RequestDownloadVideoFromNetworkUseCase(dispatcherProviderSource = dispatcherProviderSource, downloaderRepositorySource = downloaderRepositorySource)
    }

}