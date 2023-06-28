package com.bimalghara.filedownloader.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity

/**
 * Created by BimalGhara
 */

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase: RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "file_downloader_db"
    }

    abstract val downloadsDao: DownloadsDao
}