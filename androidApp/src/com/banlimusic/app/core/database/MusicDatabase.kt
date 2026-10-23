package com.banlimusic.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.banlimusic.app.core.database.dao.DownloadDao
import com.banlimusic.app.core.database.dao.LibraryDao
import com.banlimusic.app.core.database.dao.LocalMusicDao
import com.banlimusic.app.core.database.entity.DownloadTaskEntity
import com.banlimusic.app.core.database.entity.FavoriteTrackEntity
import com.banlimusic.app.core.database.entity.LocalTrackEntity
import com.banlimusic.app.core.database.entity.PlayHistoryEntity
import com.banlimusic.app.core.database.entity.SearchHistoryEntity

@Database(
    entities = [
        FavoriteTrackEntity::class,
        PlayHistoryEntity::class,
        SearchHistoryEntity::class,
        DownloadTaskEntity::class,
        LocalTrackEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    abstract fun downloadDao(): DownloadDao

    abstract fun localMusicDao(): LocalMusicDao

    companion object {
        @Volatile
        private var instance: MusicDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE favorites ADD COLUMN category TEXT NOT NULL DEFAULT '默认收藏'",
                )
                db.execSQL("ALTER TABLE downloads ADD COLUMN trackId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE downloads ADD COLUMN qualityKbps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloadLyrics INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloadArtwork INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_tracks (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        trackJson TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        durationMs INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): MusicDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MusicDatabase::class.java,
                "banli_music.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
