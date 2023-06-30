package com.bimalghara.filedownloader.presentation

import android.content.Context
import android.text.Editable
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.common.dispatcher.DispatcherProviderSource
import com.bimalghara.filedownloader.data.local.preferences.DataStoreSource
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.domain.repository.FileRepositorySource
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FunUtil.convertTimestampToLocalDate
import com.bimalghara.filedownloader.utils.Logger.logs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Created by BimalGhara
 */

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dispatcherProviderSource: DispatcherProviderSource,
    private val dataStore: DataStoreSource,
    private val networkConnectivity: NetworkConnectivity,
    private val fileRepositorySource: FileRepositorySource,
) : ViewModel() {
    private val logTag = javaClass.simpleName

    val _errorSingleEvent = MutableLiveData<SingleEvent<Any>>()
    val errorSingleEvent: LiveData<SingleEvent<Any>> get() = _errorSingleEvent

    private val _selectedPathLiveData = MutableLiveData<DocumentFile?>(null)
    val selectedPathLiveData: LiveData<DocumentFile?> get() = _selectedPathLiveData

    private val _downloadsLiveData = MutableLiveData<ResourceWrapper<List<DownloadEntity>>>()
    val downloadsLiveData: LiveData<ResourceWrapper<List<DownloadEntity>>> get() = _downloadsLiveData

    private val _enqueueLiveData = MutableLiveData<ResourceWrapper<Boolean>>()
    val enqueueLiveData: LiveData<ResourceWrapper<Boolean>> get() = _enqueueLiveData

    private var _fileDetailsJob: Job? = null
    private val _fileDetailsLiveData = MutableLiveData<ResourceWrapper<FileDetails>>()
    val fileDetailsLiveData: LiveData<ResourceWrapper<FileDetails>> get() = _fileDetailsLiveData


    init {
        viewModelScope.launch(dispatcherProviderSource.io) {
            val downloadLimit = getSettingParallelDownload()
            if(downloadLimit.isNullOrBlank())
                dataStore.saveString(DS_KEY_SETTING_PARALLEL_DOWNLOAD, DEFAULT_PARALLEL_DOWNLOAD_LIMIT)
        }
    }

    suspend fun getSettingParallelDownload():String? = withContext(dispatcherProviderSource.io){
        return@withContext dataStore.getString(DS_KEY_SETTING_PARALLEL_DOWNLOAD)
    }
    fun updateSettingParallelDownload(value: Float) = viewModelScope.launch(dispatcherProviderSource.io) {
        dataStore.saveString(DS_KEY_SETTING_PARALLEL_DOWNLOAD, value.toInt().toString())
    }

    fun setSelectedPath(documentFile: DocumentFile?) {
        _selectedPathLiveData.value = documentFile
    }

    private fun getUsersDataFromCached(context: Context) = viewModelScope.launch {
        _downloadsLiveData.value = ResourceWrapper.Loading()
        fileRepositorySource.requestDownloadsFromLocal().onEach { newList ->
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
                    _downloadsLiveData.postValue(ResourceWrapper.Error(context.getStringFromResource(R.string.no_downloads)))
                } else {
                    val groupedRecords = completeList.groupBy { record ->
                        convertTimestampToLocalDate(record.updatedAt)
                    }
                    for ((date, records) in groupedRecords) {
                        logs(logTag, "Date: $date")
                        for (record in records) {
                            logs(logTag,"Record: ${record.name}")
                        }
                    }
                }
            } else _downloadsLiveData.postValue(ResourceWrapper.Error(context.getStringFromResource(R.string.no_downloads)))
        }.launchIn(viewModelScope)
    }

    fun getFileDetails(context: Context, url: Editable?) = viewModelScope.launch {
        val networkStatus = async { getNetworkStatus() }.await()

        if (networkStatus != NetworkConnectivity.Status.Available) {
            _errorSingleEvent.postValue(SingleEvent(context.getStringFromResource(R.string.no_internet)))
        } else {
            requestFileDetailsFromCloud(context, url)
        }
    }
    private suspend fun requestFileDetailsFromCloud(context: Context, url: Editable?) {
        if(url.isNullOrEmpty() || !url.toString().startsWith("http")){
            _errorSingleEvent.value = SingleEvent(context.getStringFromResource(R.string.error_invalid_url))
        } else {

            _fileDetailsJob?.cancel()//to prevent creating duplicate flow, fun is called multiple times
            _fileDetailsJob = fileRepositorySource.requestFileDetailsFromNetwork(context, url.toString()).onEach {
                _fileDetailsLiveData.value = it
                when (it) {
                    is ResourceWrapper.Error -> _errorSingleEvent.value = SingleEvent(it.error!!)
                    else -> Unit
                }
            }.launchIn(viewModelScope)
        }
    }


    fun addIntoQueue(context: Context, newName: Editable?, wifiOnly: Boolean) = viewModelScope.launch(dispatcherProviderSource.io) {
        if(newName.isNullOrEmpty()) {
            _errorSingleEvent.postValue(SingleEvent(context.getStringFromResource(R.string.error_invalid_name)))
        } else if(_fileDetailsLiveData.value?.data?.fileExtension.isNullOrEmpty()) {
            _errorSingleEvent.postValue(SingleEvent(context.getStringFromResource(R.string.error_invalid_extension)))
        } else if(_selectedPathLiveData.value?.uri?.path.isNullOrEmpty()) {
            _errorSingleEvent.postValue(SingleEvent(context.getStringFromResource(R.string.error_invalid_destination_path)))
        } else {

            val newFIleDetails = _fileDetailsLiveData.value!!.data!!.also {
                it.fileName = newName.toString()
            }

            val response = fileRepositorySource.addQueue(context, wifiOnly, newFIleDetails, _selectedPathLiveData.value!!.uri)
            _enqueueLiveData.postValue(response)
        }
    }


    private suspend fun getNetworkStatus(): NetworkConnectivity.Status {
        val result = networkConnectivity.getStatus(dispatcherProviderSource.io)
        logs(logTag, "network status: $result")
        return result
    }

    fun clearSession() {
        _fileDetailsLiveData.value = ResourceWrapper.None()
        _enqueueLiveData.value = ResourceWrapper.None()
    }



}