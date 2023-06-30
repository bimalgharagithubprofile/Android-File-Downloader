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

    @Query("SELECT * FROM DownloadEntity where downloadStatus = 'WAITING' OR downloadStatus = 'DOWNLOADING' OR downloadStatus = 'PAUSED' ORDER BY updatedAt DESC")
    suspend fun getOpenQueuedList(): List<DownloadEntity>


    @Query("UPDATE DownloadEntity SET downloadStatus=:downloadStatus, progress=:progress, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadProgress(id: Int, downloadStatus: String, progress: Int, timestamp: Long)

    @Query("UPDATE DownloadEntity SET downloadStatus=:downloadStatus, interruptedBy=:interruptedBy, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadEnd(id: Int, downloadStatus: String, interruptedBy: String?, timestamp: Long)

    @Query("DELETE FROM DownloadEntity WHERE id=:id")
    suspend fun deleteDownload(id: Int)

}