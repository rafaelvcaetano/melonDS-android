package me.magnum.melonds.ui.emulator.model

import android.content.Intent
import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.emulator.EmulatorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LaunchArgsSecurityTest {
    @Test
    fun `exported intent cannot inject a full rom parcelable`() {
        val attackerRom = Rom(
            name = "attacker",
            developerName = "attacker",
            fileName = "attacker.nds",
            uri = Uri.parse("file:///data/user/0/me.magnum.melonds/files/private.nds"),
            parentTreeUri = null,
            config = RomConfig.default(),
            isDsiWareTitle = false,
            retroAchievementsHash = "",
        )
        val intent = Intent().putExtra(EmulatorLaunchContract.KEY_ROM, RomParcelable(attackerRom))

        assertNull(LaunchArgs.fromIntent(intent))
    }

    @Test
    fun `saved state cannot inject a full rom parcelable`() {
        val attackerRom = Rom(
            name = "attacker",
            developerName = "attacker",
            fileName = "attacker.nds",
            uri = Uri.parse("file:///data/user/0/me.magnum.melonds/files/private.nds"),
            parentTreeUri = null,
            config = RomConfig.default(),
            isDsiWareTitle = false,
            retroAchievementsHash = "",
        )
        val state = SavedStateHandle(mapOf(EmulatorLaunchContract.KEY_ROM to RomParcelable(attackerRom)))

        assertNull(LaunchArgs.fromSavedStateHandle(state))
    }

    @Test
    fun `null legacy path is rejected without crashing`() {
        val intent = Intent().apply {
            extras?.putString(EmulatorLaunchContract.KEY_PATH, null)
            putExtra(EmulatorLaunchContract.KEY_PATH, null as String?)
        }

        assertNull(LaunchArgs.fromIntent(intent))
    }

    @Test
    fun `out of range firmware ordinal is rejected`() {
        val intent = Intent()
            .putExtra(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY, true)
            .putExtra(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_CONSOLE, Int.MAX_VALUE)

        assertNull(LaunchArgs.fromIntent(intent))
        assertNull(
            LaunchArgs.fromSavedStateHandle(
                SavedStateHandle(
                    mapOf(
                        EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY to true,
                        EmulatorLaunchContract.KEY_BOOT_FIRMWARE_CONSOLE to -2,
                    )
                )
            )
        )
    }

    @Test
    fun `exported launch rejects file uri and content uri without read grant`() {
        assertNull(LaunchArgs.fromIntent(Intent().setData(Uri.parse("file:///data/user/0/private.nds"))))
        assertNull(LaunchArgs.fromIntent(Intent().setData(Uri.parse("content://attacker/rom.nds"))))
    }

    @Test
    fun `exported launch accepts content uri only with an explicit read grant`() {
        val uri = Uri.parse("content://provider/rom.nds")
        val args = LaunchArgs.fromIntent(
            Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        assertEquals(LaunchArgs.RomUri(uri), args)
    }

    @Test
    fun `grant flag does not authorize a uri carried only in a custom extra`() {
        val intent = Intent()
            .putExtra(EmulatorLaunchContract.KEY_URI, "content://attacker/private.nds")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNull(LaunchArgs.fromIntent(intent))
    }

    @Test
    fun `shortcut launch is explicit and grants only its data uri`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("content://provider/game.nds")
        val intent = EmulatorLaunchContract.createRomIntent(context, uri)
        assertEquals(EmulatorActivity::class.java.name, intent.component?.className)
        assertEquals(uri, intent.data)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun `firmware shortcut launch is explicit and retains validated arguments`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = EmulatorActivity.getFirmwareEmulatorActivityIntent(context, ConsoleType.DSi)

        assertEquals(EmulatorActivity::class.java.name, intent.component?.className)
        assertEquals(LaunchArgs.Firmware(ConsoleType.DSi), LaunchArgs.fromIntent(intent))
    }

    @Test
    fun `wrongly typed exported extras are rejected without throwing`() {
        val intent = Intent()
            .putExtra(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY, "not-a-boolean")
            .putExtra(EmulatorLaunchContract.KEY_PATH, Uri.parse("content://attacker/path"))
        assertNull(LaunchArgs.fromIntent(intent))

        val state = SavedStateHandle(
            mapOf(
                EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY to "not-a-boolean",
                EmulatorLaunchContract.KEY_PATH to Uri.parse("content://attacker/path"),
            )
        )
        assertNull(LaunchArgs.fromSavedStateHandle(state))
    }

}
