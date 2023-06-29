package com.bimalghara.filedownloader.utils


/**
 * Created by BimalGhara
 */
sealed class ResourceWrapper<T>(val data:T?=null, val error: String?=null){

    class None<T> : ResourceWrapper<T>()
    class Loading<T> : ResourceWrapper<T>()
    class Success<T>(data: T?): ResourceWrapper<T>(data=data)
    class Error<T>(customException: String) : ResourceWrapper<T>(error = customException)

    override fun toString(): String {
        return when (this){
            is None -> "None"
            is Loading -> "Loading"
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[Exception=$error]"
        }
    }
}
