package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naturasonic.app.data.local.entity.TranscriptionEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcription_history ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TranscriptionEntry>>

    @Insert
    suspend fun insert(entry: TranscriptionEntry): Long

    @Query("SELECT * FROM transcription_history ORDER BY createdAt DESC")
    suspend fun getAllForBackup(): List<TranscriptionEntry>

    @Query("DELETE FROM transcription_history WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
