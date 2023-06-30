package com.bimalghara.filedownloader.data.di

import android.app.Application
import androidx.room.Room
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.database.AppDatabase
import com.bimalghara.filedownloader.data.network.retrofit.ApiServiceGenerator
import com.bimalghara.filedownloader.data.repository.DownloadRepositoryImpl
import com.bimalghara.filedownloader.data.repository.FileRepositoryImpl
import com.bimalghara.filedownloader.domain.repository.FileRepositorySource
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
    fun provideDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideFileRepository(
        dispatcherProviderSource: DispatcherProviderSource,
        serviceGenerator: ApiServiceGenerator,
        db: AppDatabase
    ): FileRepositorySource {
        return FileRepositoryImpl(
            dispatcherProviderSource = dispatcherProviderSource,
            serviceGenerator = serviceGenerator,
            downloadsDao = db.downloadsDao
        )
    }

    @Provides
    @Singleton
    fun provideDownloaderRepository(
        dispatcherProviderSource: DispatcherProviderSource,
        serviceGenerator: ApiServiceGenerator,
        db: AppDatabase
    ): DownloadRepositoryImpl {
        return DownloadRepositoryImpl(
            dispatcherProviderSource = dispatcherProviderSource,
            serviceGenerator = serviceGenerator,
            downloadsDao = db.downloadsDao
        )
    }


}