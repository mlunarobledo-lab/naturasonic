package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naturasonic.app.data.local.entity.AudiogramRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiogramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AudiogramRecord): Long

    @Query("SELECT * FROM audiogram_records WHERE isActive = 1 ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLatestActive(): AudiogramRecord?

    @Query("SELECT * FROM audiogram_records WHERE isActive = 1 ORDER BY dateMillis DESC LIMIT 1")
    fun observeLatestActive(): Flow<AudiogramRecord?>

    @Query("SELECT * FROM audiogram_records ORDER BY dateMillis DESC")
    fun getAll(): Flow<List<AudiogramRecord>>

    @Query("SELECT * FROM audiogram_records ORDER BY dateMillis DESC")
    suspend fun getAllForBackup(): List<AudiogramRecord>

    @Query("UPDATE audiogram_records SET isActive = 0")
    suspend fun deactivateAll()
}
