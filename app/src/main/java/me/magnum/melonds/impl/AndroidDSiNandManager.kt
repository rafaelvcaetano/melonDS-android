package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSiNand
import me.magnum.melonds.common.suspendRunCatching
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.dsinand.DSiWareTitleFileType
import me.magnum.melonds.domain.model.dsinand.ImportDSiWareTitleResult
import me.magnum.melonds.domain.model.dsinand.OpenDSiNandResult
import me.magnum.melonds.domain.repositories.DSiWareMetadataRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.domain.services.DSiNandManager
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class AndroidDSiNandManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dsiWareMetadataRepository: DSiWareMetadataRepository,
    private val biosDirectoryVerifier: ConfigurationDirectoryVerifier,
) : DSiNandManager {

    private companion object {
        val DSIWARE_CATEGORY = 0x00030004.toUInt()
    }

    private val isNandOpen = AtomicBoolean(false)

    override suspend fun openNand(): OpenDSiNandResult {
        if (!isNandOpen.compareAndSet(false, true)) {
            return OpenDSiNandResult.NAND_ALREADY_OPEN
        }
        val dsiDirectoryStatus = biosDirectoryVerifier.checkDsiConfigurationDirectory()
        if (dsiDirectoryStatus.status != ConfigurationDirResult.Status.VALID) {
            isNandOpen.set(false)
            return OpenDSiNandResult.INVALID_DSI_SETUP
        }

        val result = MelonDSiNand.openNand(settingsRepository.getEmulatorConfiguration())
        return mapOpenNandReturnCodeToResult(result)
    }

    override suspend fun listTitles(): List<DSiWareTitle> {
        if (!isNandOpen.get()) {
            return emptyList()
        }

        return MelonDSiNand.listTitles()
    }

    override suspend fun importTitle(titleUri: Uri): ImportDSiWareTitleResult = withContext(Dispatchers.IO) {
        if (!isNandOpen.get()) {
            return@withContext ImportDSiWareTitleResult.NAND_NOT_OPEN
        }

        var categoryId: UInt = 0.toUInt()
        var titleId: UInt = 0.toUInt()

        context.contentResolver.openInputStream(titleUri)?.use {
            it.skip(0x230)
            titleId = it.readUInt()
            categoryId = it.readUInt()
        } ?: return@withContext ImportDSiWareTitleResult.ERROR_OPENING_FILE

        if (categoryId != DSIWARE_CATEGORY) {
            return@withContext ImportDSiWareTitleResult.NOT_DSIWARE_TITLE
        }

        val tmdMetadataResult = suspendRunCatching {
            dsiWareMetadataRepository.getDSiWareTitleMetadata(categoryId, titleId)
        }

        if (tmdMetadataResult.isFailure) {
            return@withContext ImportDSiWareTitleResult.METADATA_FETCH_FAILED
        }

        val result = MelonDSiNand.importTitle(titleUri.toString(), tmdMetadataResult.getOrThrow())
        mapImportTitleReturnCodeToResult(result)
    }

    override suspend fun deleteTitle(title: DSiWareTitle) {
        if (!isNandOpen.get()) {
            return
        }

        MelonDSiNand.deleteTitle((title.titleId and 0xFFFFFFFF).toInt())
    }

    override suspend fun importTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean {
        if (!isNandOpen.get()) {
            return false
        }

        return MelonDSiNand.importTitleFile((title.titleId and 0xFFFFFFFF).toInt(), fileType.ordinal, fileUri.toString())
    }

    override suspend fun exportTitleFile(title: DSiWareTitle, fileType: DSiWareTitleFileType, fileUri: Uri): Boolean {
        if (!isNandOpen.get()) {
            return false
        }

        return MelonDSiNand.exportTitleFile((title.titleId and 0xFFFFFFFF).toInt(), fileType.ordinal, fileUri.toString())
    }

    override fun closeNand() {
        if (!isNandOpen.compareAndSet(true, false)) {
            return
        }

        MelonDSiNand.closeNand()
    }

    private fun mapOpenNandReturnCodeToResult(returnCode: Int): OpenDSiNandResult {
        return when (returnCode) {
            0 -> OpenDSiNandResult.SUCCESS
            1 -> OpenDSiNandResult.NAND_ALREADY_OPEN
            2 -> OpenDSiNandResult.BIOS7_NOT_FOUND
            3 -> OpenDSiNandResult.NAND_OPEN_FAILED
            else -> OpenDSiNandResult.UNKNOWN
        }
    }

    private fun mapImportTitleReturnCodeToResult(returnCode: Int): ImportDSiWareTitleResult {
        return when (returnCode) {
            0 -> ImportDSiWareTitleResult.SUCCESS
            1 -> ImportDSiWareTitleResult.NAND_NOT_OPEN
            2 -> ImportDSiWareTitleResult.ERROR_OPENING_FILE
            3 -> ImportDSiWareTitleResult.NOT_DSIWARE_TITLE
            4 -> ImportDSiWareTitleResult.TITLE_ALREADY_IMPORTED
            5 -> ImportDSiWareTitleResult.INSATLL_FAILED
            else -> ImportDSiWareTitleResult.UNKNOWN
        }
    }

    private fun InputStream.readUInt(): UInt {
        return read().toUInt() or read().shl(8).toUInt() or read().shl(16).toUInt() or read().shl(24).toUInt()
    }
}