package me.magnum.melonds.domain.services

import android.net.Uri
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.dsinand.ImportDSiWareTitleResult
import me.magnum.melonds.domain.model.dsinand.OpenDSiNandResult
import me.magnum.melonds.domain.model.dsinand.DSiWareTitleFileType

interface DSiNandManager {
    suspend fun openNand(): OpenDSiNandResult
    suspend fun listTitles(): List<DSiWareTitle>
    suspend fun importTitle(titleUri: Uri): ImportDSiWareTitleResult
    suspend fun deleteTitle(title: DSiWareTitle)
    suspend fun importTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    suspend fun exportTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean
    fun closeNand()
}