package com.bimalghara.filedownloader.data.network.retrofit


import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by BimalGhara
 */

private const val timeoutRead = 180000   //In seconds (3min)
private const val timeoutConnect = 180000   //In seconds (3min)

@Singleton
class ApiServiceGenerator @Inject constructor() {
    private val okHttpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
    private val retrofit: Retrofit

    init {
        okHttpBuilder.connectTimeout(timeoutConnect.toLong(), TimeUnit.SECONDS)
        okHttpBuilder.readTimeout(timeoutRead.toLong(), TimeUnit.SECONDS)
        val client = okHttpBuilder.build()
        retrofit = Retrofit.Builder()
                .baseUrl("https://base_url.com").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
    }

    fun <S> createApiService(serviceClass: Class<S>): S {
        return retrofit.create(serviceClass)
    }
}
