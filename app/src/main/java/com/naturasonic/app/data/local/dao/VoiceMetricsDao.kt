package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naturasonic.app.data.local.entity.VoiceMetricsEntry

@Dao
interface VoiceMetricsDao {

    @Insert
    suspend fun insert(entry: VoiceMetricsEntry)

    @Query("SELECT * FROM voice_metrics WHERE recordedAt >= :since ORDER BY recordedAt ASC")
    suspend fun getSince(since: Long): List<VoiceMetricsEntry>

    @Query("DELETE FROM voice_metrics WHERE recordedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
