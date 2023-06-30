package com.bimalghara.filedownloader.data.network.retrofit.service

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

/**
 * Created by BimalGhara
 */

interface ApiServiceDownload {


    @HEAD
    fun getFileDetails(@Url fileUrl: String): Call<Void>


    @GET
    @Streaming
    fun downloadFile(@Url url: String): Call<ResponseBody>

    @GET
    @Streaming
    fun downloadFileRange(@Url fileUrl: String, @Header("Range") range: String): Call<ResponseBody>

}