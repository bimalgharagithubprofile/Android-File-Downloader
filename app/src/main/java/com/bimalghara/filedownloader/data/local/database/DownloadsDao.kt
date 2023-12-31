package com.bimalghara.filedownloader.data.local.database

import androidx.room.*
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

    @Query("SELECT name FROM DownloadEntity WHERE id=:id")
    fun getName(id: Int): String

    @Query("SELECT downloadedFileUri FROM DownloadEntity WHERE id=:id")
    fun getDownloadedFileUri(id: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(item: DownloadEntity): Long

    @Query("SELECT * FROM DownloadEntity where downloadStatus = 'WAITING' OR downloadStatus = 'DOWNLOADING' OR downloadStatus = 'PAUSED' ORDER BY updatedAt DESC")
    suspend fun getOpenQueuedList(): List<DownloadEntity>


    @Query("UPDATE DownloadEntity SET downloadStatus=:downloadStatus, interruptedBy=:interruptedBy, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadProgress(id: Int, downloadStatus: String, interruptedBy: String?, timestamp: Long)

    @Query("UPDATE DownloadEntity SET downloadStatus=:downloadStatus, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadWaiting(id: Int, downloadStatus: String, timestamp: Long)

    @Query("UPDATE DownloadEntity SET downloadStatus=:downloadStatus, lastProgress=:lastProgress, interruptedBy=:interruptedBy, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadEnd(id: Int, downloadStatus: String, lastProgress: Int, interruptedBy: String?, timestamp: Long)

    @Query("UPDATE DownloadEntity SET size=:size, downloadedFileUri=:downloadedUri, updatedAt=:timestamp WHERE id=:id")
    suspend fun updateDownloadedFileUri(id: Int, size: Long, downloadedUri: String, timestamp: Long)

    @Query("DELETE FROM DownloadEntity WHERE id=:id")
    suspend fun deleteDownload(id: Int)


}