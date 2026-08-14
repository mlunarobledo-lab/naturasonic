package com.naturasonic.app.data.repository

import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.entity.AlertEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertHistoryRepository @Inject constructor(
    private val dao: AlertEventDao
) {

    fun getAll(): Flow<List<AlertEvent>> = dao.getAll()

    fun getByClass(soundClass: String): Flow<List<AlertEvent>> = dao.getByClass(soundClass)

    suspend fun deleteOlderThan(beforeTimestamp: Long) = dao.deleteOlderThan(beforeTimestamp)
}
