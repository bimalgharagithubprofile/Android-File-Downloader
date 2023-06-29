package com.bimalghara.filedownloader.domain.model

data class FileDetails(
    val requestUrl: String,
    val contentLength: Long?,
    val contentType: String?,
    var fileName: String?,
    val fileExtension: String?,
    val lastModified: String?,
    val eTag: String?,
    val cacheControl: String?,
    val expires: String?,
    val acceptRanges: String?,
    val server: String?
)
