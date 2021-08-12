package me.magnum.melonds.ui.shortcutsetup

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.romlist.RomListFragment
import me.magnum.melonds.ui.romlist.RomListViewModel

@AndroidEntryPoint
class ShortcutSetupActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_ROM_LIST = "rom_list"
    }

    private val viewModel: RomListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_setup)

        val fragment = RomListFragment.newInstance(false)
        fragment.setRomSelectedListener { onRomSelected(it) }

        supportFragmentManager.commit {
            replace(R.id.layout_root, fragment, FRAGMENT_ROM_LIST)
        }
    }

    private fun onRomSelected(rom: Rom) {
        val intent = Intent("${packageName}.LAUNCH_ROM").apply {
            putExtra(EmulatorActivity.KEY_URI, rom.uri.toString())
        }
        val romIcon = viewModel.getRomIcon(rom)
        val shortcutInfo = ShortcutInfoCompat.Builder(this, rom.uri.toString())
                .setShortLabel(rom.name)
                .setIcon(IconCompat.createWithBitmap(buildShortcutBitmap(romIcon)))
                .setIntent(intent)
                .build()

        val shortcutIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)

        setResult(Activity.RESULT_OK, shortcutIntent)
        finish()
    }

    private fun buildShortcutBitmap(romIcon: RomIcon): Bitmap {
        val iconBitmap = romIcon.bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.logo_splash)
        val shortcutBitmap = createBitmap(256, 256)

        return shortcutBitmap.applyCanvas {
            val iconRect = Rect(44, 44, shortcutBitmap.width - 44, shortcutBitmap.height - 44)
            drawBitmap(iconBitmap, null, iconRect, Paint().apply { isFilterBitmap = romIcon.filtering == RomIconFiltering.LINEAR })
        }
    }
}