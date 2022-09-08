package me.magnum.melonds.domain.services

import android.net.Uri
import me.magnum.melonds.domain.model.DSiWareTitle

interface DSiNandManager {
    suspend fun openNand()
    suspend fun listTitles(): List<DSiWareTitle>
    suspend fun importTitle(titleUri: Uri)
    suspend fun deleteTitle(title: DSiWareTitle)
    fun closeNand()
}