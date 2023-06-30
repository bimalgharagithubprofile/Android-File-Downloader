package com.bimalghara.filedownloader.common.di

import android.content.Context
import com.bimalghara.filedownloader.common.dispatcher.DefaultDispatcherProvider
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.utils.NetworkConnectivityImpl
import com.bimalghara.filedownloader.utils.NetworkConnectivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Created by BimalGhara
 */

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @Provides
    @Singleton
    fun provideDefaultDispatcher(): DispatcherProviderSource {
        return DefaultDispatcherProvider()
    }

    @Provides
    @Singleton
    fun provideNetworkConnectivity(@ApplicationContext context: Context): NetworkConnectivity {
        return NetworkConnectivityImpl(context)
    }
}