package com.naturasonic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naturasonic.app.data.local.entity.DosimetrySample

@Dao
interface DosimetrySampleDao {

    @Insert
    suspend fun insert(sample: DosimetrySample)

    @Query("SELECT * FROM dosimetry_samples WHERE recordedAt >= :since ORDER BY recordedAt ASC")
    suspend fun getSince(since: Long): List<DosimetrySample>

    @Query("DELETE FROM dosimetry_samples WHERE recordedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
