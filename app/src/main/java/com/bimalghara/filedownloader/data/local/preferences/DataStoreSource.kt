package com.bimalghara.filedownloader.data.local.preferences


/**
 * Created by BimalGhara
 */

interface DataStoreSource {
    suspend fun saveString(key: String, value: String)

    suspend fun getString(key: String): String?

}