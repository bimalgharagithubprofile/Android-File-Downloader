package com.bimalghara.filedownloader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bimalghara.filedownloader.utils.DS_PREFERENCE_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Created by BimalGhara
 */

class DataStoreImpl @Inject constructor(
    private val context: Context
) : DataStoreSource {

    private var dataStore: DataStore<Preferences>? = null

    init {
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            migrations = listOf(SharedPreferencesMigration(context, DS_PREFERENCE_NAME)),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(DS_PREFERENCE_NAME) }
        )
    }

    override suspend fun saveString(key: String, value: String) {
        val dataStoreKey = stringPreferencesKey(key)
        dataStore?.edit {
            it[dataStoreKey] = value
        }
    }

    override suspend fun getString(key: String): String? {
        val dataStoreKey = stringPreferencesKey(key)
        val preferences = dataStore?.data?.first()
        return preferences?.get(dataStoreKey)
    }


}