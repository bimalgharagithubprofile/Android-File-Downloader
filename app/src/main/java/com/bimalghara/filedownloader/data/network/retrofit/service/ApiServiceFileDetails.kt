package com.bimalghara.filedownloader.data.network.retrofit.service

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Created by BimalGhara
 */

interface ApiServiceFileDetails {


    @Streaming
    @GET
    fun fileDetails(@Url url: String): Call<ResponseBody>



}