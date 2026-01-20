package me.magnum.melonds.ui.shortcutsetup

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityShortcutSetupBinding
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.rom.Rom
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
        val binding = ActivityShortcutSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.viewStatusBarBackground.updateLayoutParams {
                height = insets.top
            }
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets.inset(insets.left, insets.top, insets.right, 0)
        }

        val fragment = if (savedInstanceState == null) {
            RomListFragment.newInstance(false, RomListFragment.RomEnableCriteria.ENABLE_ALL).also {
                supportFragmentManager.commit {
                    replace(binding.layoutRoot.id, it, FRAGMENT_ROM_LIST)
                }
            }
        } else {
            supportFragmentManager.findFragmentByTag(FRAGMENT_ROM_LIST) as RomListFragment
        }

        fragment.setRomSelectedListener { onRomSelected(it) }
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