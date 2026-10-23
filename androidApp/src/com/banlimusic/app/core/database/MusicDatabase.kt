package com.banlimusic.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.banlimusic.app.core.database.dao.DownloadDao
import com.banlimusic.app.core.database.dao.LibraryDao
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity

@Database(
    entities = [
        FavoriteTrackEntity::class,
        PlayHistoryEntity::class,
        SearchHistoryEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: MusicDatabase? = null

        fun get(context: Context): MusicDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MusicDatabase::class.java,
                "banli_music.db",
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
