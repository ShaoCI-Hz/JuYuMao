package com.hezi.juyumao.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hezi.juyumao.data.local.db.dao.PlaylistDao
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SmartPlaylistDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.db.entity.PlayHistoryEntity
import com.hezi.juyumao.data.local.db.entity.PlaylistEntity
import com.hezi.juyumao.data.local.db.entity.PlaylistSongEntity
import com.hezi.juyumao.data.local.db.entity.ServerEntity
import com.hezi.juyumao.data.local.db.entity.SmartPlaylistEntity
import com.hezi.juyumao.data.local.db.entity.SongEntity

@Database(
    entities = [
        SongEntity::class, ServerEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        PlayHistoryEntity::class, SmartPlaylistEntity::class,
    ],
    version = 5,
    // 导出 schema JSON 到版本库，使迁移可用 MigrationTestHelper 验证（防止升级崩溃）
    exportSchema = true,
)
abstract class JuYuMaoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun serverDao(): ServerDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN albumArtist TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN composer TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN sampleRate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitsPerSample INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN hasEmbeddedLyrics INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN hasExternalLyrics INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                // 先去重再建索引，避免重复数据导致崩溃
                db.execSQL("DELETE FROM songs WHERE rowid NOT IN (SELECT MIN(rowid) FROM songs GROUP BY filePath)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs_filePath ON songs(filePath)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 歌单歌曲关联表（playlists 表已存在于 v1 起的 schema）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playlist_songs (" +
                        "playlistId INTEGER NOT NULL, " +
                        "songId INTEGER NOT NULL, " +
                        "PRIMARY KEY(playlistId, songId), " +
                        "FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs(playlistId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 星级评分（P2-13）
                db.execSQL("ALTER TABLE songs ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                // 播放历史表（P2-16）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS play_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "songId INTEGER NOT NULL, " +
                        "playedAt INTEGER NOT NULL, " +
                        "source TEXT NOT NULL DEFAULT 'LOCAL'" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_playedAt ON play_history(playedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_songId ON play_history(songId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 智能歌单表（P0-3）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS smart_playlists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "minRating INTEGER NOT NULL DEFAULT 0, " +
                        "minPlayCount INTEGER NOT NULL DEFAULT 0, " +
                        "addedWithinDays INTEGER NOT NULL DEFAULT 0, " +
                        "genre TEXT, " +
                        "source TEXT, " +
                        "isFavoriteOnly INTEGER NOT NULL DEFAULT 0" +
                        ")"
                )
            }
        }

        fun create(context: Context): JuYuMaoDatabase {
            return Room.databaseBuilder(
                context,
                JuYuMaoDatabase::class.java,
                "juyumao.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // 注意：不要使用 fallbackToDestructiveMigration()，它会在 schema 变更时静默销毁用户数据
                // 新增版本时必须提供 Migration
                .build()
        }
    }
}
