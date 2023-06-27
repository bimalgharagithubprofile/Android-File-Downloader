package com.bimalghara.filedownloader.data.network.retrofit.service

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Created by BimalGhara
 */

interface ApiServiceDownload {


    @Streaming
    @GET
    fun downloadVideo(@Url url: String): Call<ResponseBody>



}