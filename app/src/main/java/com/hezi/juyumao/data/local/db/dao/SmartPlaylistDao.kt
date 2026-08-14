package com.hezi.juyumao.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hezi.juyumao.data.local.db.entity.SmartPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {

    @Insert
    suspend fun insert(playlist: SmartPlaylistEntity): Long

    @Update
    suspend fun update(playlist: SmartPlaylistEntity)

    @Delete
    suspend fun delete(playlist: SmartPlaylistEntity)

    @Query("SELECT * FROM smart_playlists ORDER BY id")
    fun getAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    suspend fun getById(id: Long): SmartPlaylistEntity?
}
