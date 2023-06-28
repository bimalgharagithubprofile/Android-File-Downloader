package com.bimalghara.filedownloader.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.domain.repository.DownloaderRepositorySource
import com.bimalghara.filedownloader.utils.FunUtil.convertTimestampToLocalDate
import com.bimalghara.filedownloader.utils.NetworkConnectivitySource
import com.bimalghara.filedownloader.utils.ResourceWrapper
import com.bimalghara.filedownloader.utils.SingleEvent
import com.bimalghara.filedownloader.utils.getStringFromResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

/**
 * Created by BimalGhara
 */

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val networkConnectivitySource: NetworkConnectivitySource,
    private val downloaderRepositorySource: DownloaderRepositorySource,
) : ViewModel() {
    private val logTag = javaClass.simpleName

    private val _errorSingleEvent = MutableLiveData<SingleEvent<Any>>()
    val errorSingleEvent: LiveData<SingleEvent<Any>> get() = _errorSingleEvent

    private val _downloadsLiveData = MutableLiveData<ResourceWrapper<List<DownloadEntity>>>()
    val downloadsLiveData: LiveData<ResourceWrapper<List<DownloadEntity>>> get() = _downloadsLiveData

    private var _fileDetailsJob: Job? = null


    private fun getUsersDataFromCached(context: Context) = viewModelScope.launch {
        _downloadsLiveData.value = ResourceWrapper.Loading()
        downloaderRepositorySource.requestDownloadsFromLocal().onEach { newList ->
            if (newList.isNotEmpty()) {
                val completeList: MutableList<DownloadEntity> = arrayListOf()
                if (_downloadsLiveData.value?.data != null) {
                    val existingList = _downloadsLiveData.value!!.data!!.toMutableList()
                    existingList.removeAll(newList)
                    completeList.addAll(existingList.plus(newList).toSet().toList())
                } else {
                    completeList.addAll(newList.toSet().toList())
                }

                if (completeList.isEmpty()) {
                    _downloadsLiveData.value = ResourceWrapper.Error(context.getStringFromResource(R.string.no_downloads))
                } else {
                    val groupedRecords = completeList.groupBy { record ->
                        convertTimestampToLocalDate(record.updatedAt)
                    }
                    for ((date, records) in groupedRecords) {
                        Log.e(logTag, "Date: $date")
                        for (record in records) {
                            Log.e(logTag,"Record: ${record.name}")
                        }
                    }
                }
            } else _downloadsLiveData.value = ResourceWrapper.Error(context.getStringFromResource(R.string.no_downloads))
        }.launchIn(viewModelScope)
    }


    fun getFileDetails(context: Context) = viewModelScope.launch {
        val networkStatus = async { getNetworkStatus() }.await()

        if (networkStatus != NetworkConnectivitySource.Status.Available) {
            _errorSingleEvent.value = SingleEvent(context.getStringFromResource(R.string.no_internet))
        } else {
            requestFileDetailsFromCloud()
        }
    }
    private fun requestFileDetailsFromCloud() {
        _fileDetailsJob?.cancel()//to prevent creating duplicate flow, fun is called multiple times
        /*_fileDetailsJob = downloaderRepositorySource.requestF().onEach {
            when (it) {
                is ResourceWrapper.Error -> showError(it.error)
                else -> Unit
            }
        }.launchIn(viewModelScope)*/
    }


    fun addIntoQueue(context: Context){

    }


    private suspend fun getNetworkStatus(): NetworkConnectivitySource.Status {
        val result = networkConnectivitySource.getStatus(dispatcherProviderSource.io)
        Log.i(logTag, "network status: $result")
        return result
    }

}