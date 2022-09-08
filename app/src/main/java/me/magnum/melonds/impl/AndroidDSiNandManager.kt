package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSiNand
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.repositories.DSiWareMetadataRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.domain.services.DSiNandManager
import java.util.concurrent.atomic.AtomicBoolean

class AndroidDSiNandManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dSiWareMetadataRepository: DSiWareMetadataRepository,
    private val biosDirectoryVerifier: ConfigurationDirectoryVerifier,
) : DSiNandManager {

    private companion object {
        val DSIWARE_CATEGORY = 0x00030004.toUInt()
    }

    private val isNandOpen = AtomicBoolean(false)

    override suspend fun openNand() {
        if (!isNandOpen.compareAndSet(false, true)) {
            return
        }
        val dsiDirectoryStatus = biosDirectoryVerifier.checkDsiConfigurationDirectory()
        if (dsiDirectoryStatus.status != ConfigurationDirResult.Status.VALID) {
            isNandOpen.set(false)
            return
        }

        MelonDSiNand.openNand(settingsRepository.getEmulatorConfiguration())
    }

    override suspend fun listTitles(): List<DSiWareTitle> {
        if (!isNandOpen.get()) {
            return emptyList()
        }

        return MelonDSiNand.listTitles()
    }

    override suspend fun importTitle(titleUri: Uri) = withContext(Dispatchers.IO) {
        if (!isNandOpen.get()) {
            return@withContext
        }

        var categoryId: UInt = 0.toUInt()
        var titleId: UInt = 0.toUInt()

        context.contentResolver.openInputStream(titleUri)?.use {
            it.skip(0x230)
            titleId = it.read().toUInt() or it.read().shl(8).toUInt() or it.read().shl(16).toUInt() or it.read().shl(24).toUInt()
            categoryId = it.read().toUInt() or it.read().shl(8).toUInt() or it.read().shl(16).toUInt() or it.read().shl(24).toUInt()
        } ?: return@withContext

        if (categoryId != DSIWARE_CATEGORY) {
            return@withContext
        }

        val tmdMetadata = dSiWareMetadataRepository.getDSiWareTitleMetadata(categoryId, titleId)

        MelonDSiNand.importTitle(titleUri.toString(), tmdMetadata)
    }

    override suspend fun deleteTitle(title: DSiWareTitle) {
        if (!isNandOpen.get()) {
            return
        }

        MelonDSiNand.deleteTitle((title.titleId and 0xFFFFFFFF).toInt())
    }

    override fun closeNand() {
        if (!isNandOpen.compareAndSet(true, false)) {
            return
        }

        MelonDSiNand.closeNand()
    }
}