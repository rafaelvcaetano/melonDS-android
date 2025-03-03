package me.magnum.melonds.domain.repositories

import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.domain.model.Background
import java.util.UUID

interface BackgroundRepository {
    fun getBackgrounds(): Flow<List<Background>>
    suspend fun getBackground(id: UUID): Background?
    suspend fun addBackground(background: Background)
    suspend fun deleteBackground(background: Background)
}