package com.bimalghara.filedownloader.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Created by BimalGhara
 */

@Dao
interface DownloadsDao {

    //can't be suspending because it's Flow
    @Query("SELECT * FROM DownloadEntity")
    fun getDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(item: DownloadEntity): Long

    @Query("SELECT * FROM DownloadEntity where downloadStatus = 'WAITING' OR downloadStatus = 'DOWNLOADING' OR downloadStatus = 'PAUSED'")
    suspend fun getOpenQueuedList(): List<DownloadEntity>


}