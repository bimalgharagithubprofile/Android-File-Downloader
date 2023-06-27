package com.bimalghara.filedownloader.data.di

import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.network.RemoteDataImpl
import com.bimalghara.filedownloader.data.network.RemoteDataSource
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.repository.DownloaderRepositoryImpl
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Created by BimalGhara
 */



@InstallIn(SingletonComponent::class)
@Module
class DataModuleDataSources {

    @Provides
    @Singleton
    fun provideDownloadRepository(dispatcherProviderSource: DispatcherProviderSource, remoteDataSource: RemoteDataSource): DownloaderRepositorySource {
        return DownloaderRepositoryImpl(dispatcherProviderSource = dispatcherProviderSource, remoteDataSource = remoteDataSource)
    }


    @Provides
    @Singleton
    fun provideRemoteData(serviceGenerator: ApiServiceGenerator): RemoteDataSource {
        return RemoteDataImpl(serviceGenerator)
    }

}