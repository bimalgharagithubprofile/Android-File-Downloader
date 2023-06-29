package com.bimalghara.filedownloader.data.network.retrofit.service

import retrofit2.Call
import retrofit2.http.HEAD
import retrofit2.http.Url

/**
 * Created by BimalGhara
 */

interface ApiServiceDownload {


    @HEAD
    fun getFileDetails(@Url fileUrl: String): Call<Void>



}