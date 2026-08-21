package me.magnum.melonds.ui.shortcutsetup

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.common.component.romlist.RomItem
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.romlist.RomListViewModel
import me.magnum.melonds.ui.romlist.ui.RomList
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class ShortcutSetupActivity : AppCompatActivity() {

    private val viewModel: RomListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            val roms by viewModel.roms.collectAsStateWithLifecycle()
            val romScanningStatus by viewModel.romScanningStatus.collectAsStateWithLifecycle(initialValue = RomScanningStatus.NOT_SCANNING)

            MelonTheme {
                ShortcutSetupScreen(
                    roms = roms ?: emptyList(),
                    isRefreshing = romScanningStatus == RomScanningStatus.SCANNING,
                    onRefresh = { viewModel.refreshRoms() },
                    onRomSelected = { rom ->
                        viewModel.setRomLastPlayedNow(rom)
                        onRomSelected(rom)
                    },
                    retrieveRomIcon = { rom ->
                        viewModel.getRomIcon(rom)
                    },
                )
            }
        }
    }

    private fun onRomSelected(rom: Rom) {
        val intent = Intent("${packageName}.LAUNCH_ROM").apply {
            putExtra(EmulatorActivity.KEY_URI, rom.uri.toString())
        }

        lifecycleScope.launch {
            val romIcon = viewModel.getRomIcon(rom)
            val shortcutInfo = ShortcutInfoCompat.Builder(this@ShortcutSetupActivity, rom.uri.toString())
                .setShortLabel(rom.name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(buildShortcutBitmap(romIcon)))
                .setIntent(intent)
                .build()

            val shortcutIntent = ShortcutManagerCompat.createShortcutResultIntent(this@ShortcutSetupActivity, shortcutInfo)

            setResult(RESULT_OK, shortcutIntent)
            finish()
        }
    }

    private fun buildShortcutBitmap(romIcon: RomIcon): Bitmap {
        val iconBitmap = romIcon.bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.logo_splash)
        val shortcutBitmap = createBitmap(256, 256)

        return shortcutBitmap.applyCanvas {
            drawRect(Rect(0, 0, width, height), Paint().apply { color = Color.WHITE })
            val iconRect = Rect(77, 77, shortcutBitmap.width - 77, shortcutBitmap.height - 77)
            drawBitmap(iconBitmap, null, iconRect, Paint().apply { isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR })
        }
    }
}

@Composable
private fun ShortcutSetupScreen(
    roms: List<Rom>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRomSelected: (Rom) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon,
) {
    Scaffold(
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.rom_shortcut))
                    },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        RomList(
            modifier = Modifier.fillMaxSize(),
            roms = roms,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) { modifier, rom ->
            RomItem(
                modifier = modifier.fillMaxWidth(),
                item = rom,
                onClick = { onRomSelected(rom) },
                retrieveTitleIcon = { retrieveRomIcon(rom) },
            )
        }
    }
}