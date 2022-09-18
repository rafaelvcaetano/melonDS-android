package me.magnum.melonds.domain.services

import android.net.Uri
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.dsinand.ImportTitleResult
import me.magnum.melonds.domain.model.dsinand.OpenNandResult

interface DSiNandManager {
    suspend fun openNand(): OpenNandResult
    suspend fun listTitles(): List<DSiWareTitle>
    suspend fun importTitle(titleUri: Uri): ImportTitleResult
    suspend fun deleteTitle(title: DSiWareTitle)
    fun closeNand()
}