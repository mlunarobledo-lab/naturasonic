package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naturasonic.app.data.local.entity.AlertEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {

    @Query("SELECT * FROM alert_log ORDER BY detectedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<AlertEvent>>

    @Insert
    suspend fun insert(event: AlertEvent): Long

    @Query("DELETE FROM alert_log WHERE detectedAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
