package me.magnum.melonds.domain.services

import android.app.Application
import android.net.Uri
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.repositories.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ConfigurationDirectoryVerifierTest {
    @Test
    fun `a valid DSi candidate still requires a valid configured DS folder`() {
        val settings = Proxy.newProxyInstance(SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getDsBiosDirectory" -> null
                else -> error("Unexpected settings access: ${method.name}")
            }
        } as SettingsRepository
        val verifier = object : ConfigurationDirectoryVerifier(settings) {
            override fun checkDsConfigurationDirectory(directory: Uri?) =
                ConfigurationDirResult(ConsoleType.DS, ConfigurationDirResult.Status.UNSET, emptyArray(), emptyArray())
            override fun checkDsiConfigurationDirectory(directory: Uri?) =
                ConfigurationDirResult(ConsoleType.DSi, ConfigurationDirResult.Status.VALID, emptyArray(), emptyArray())
        }

        val result = verifier.checkConsoleConfigurationDirectory(ConsoleType.DSi, Uri.parse("content://bios/dsi"))

        assertEquals(ConfigurationDirResult.Status.UNSET, result.status)
        assertEquals(ConsoleType.DS, result.consoleType)
    }
}
