package me.magnum.melonds.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.magnum.melonds.R
import me.magnum.melonds.model.Rom
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romlist.RomListFragment

class MainActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_ROM_LIST = "ROM_LIST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        addRomListFragment()
    }

    private fun addRomListFragment() {
        var romListFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_ROM_LIST) as RomListFragment?
        if (romListFragment == null) {
            romListFragment = RomListFragment.newInstance()
            supportFragmentManager.beginTransaction()
                    .add(R.id.layout_main, romListFragment, FRAGMENT_ROM_LIST)
                    .commit()
        }
        romListFragment.setRomSelectedListener { rom -> loadRom(rom) }
    }

    private fun loadRom(rom: Rom) {
        val intent = Intent(this, EmulatorActivity::class.java)
        intent.putExtra(EmulatorActivity.KEY_ROM, RomParcelable(rom))
        startActivity(intent)
    }
}