package com.bimalghara.filedownloader.domain.model

data class FileDetails(

    var name: String? = null,
    var fileSize: Long = 0,
    var ext: String? = null
)
