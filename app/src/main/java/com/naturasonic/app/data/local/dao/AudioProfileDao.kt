package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.naturasonic.app.data.local.entity.AudioProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioProfileDao {

    @Query("SELECT * FROM audio_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<AudioProfile>>

    @Query("SELECT * FROM audio_profiles WHERE mode = :mode ORDER BY createdAt DESC")
    fun getProfilesByMode(mode: String): Flow<List<AudioProfile>>

    @Query("SELECT * FROM audio_profiles WHERE isDefault = 1 AND mode = :mode LIMIT 1")
    suspend fun getDefaultProfile(mode: String): AudioProfile?

    @Query("SELECT * FROM audio_profiles WHERE id = :id")
    suspend fun getById(id: Long): AudioProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AudioProfile): Long

    @Update
    suspend fun update(profile: AudioProfile)

    @Delete
    suspend fun delete(profile: AudioProfile)

    @Query("UPDATE audio_profiles SET isDefault = 0 WHERE mode = :mode")
    suspend fun clearDefaultForMode(mode: String)

    @Query("SELECT COUNT(*) FROM audio_profiles")
    suspend fun count(): Int
}
