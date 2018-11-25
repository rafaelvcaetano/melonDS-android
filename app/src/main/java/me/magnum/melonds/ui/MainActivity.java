package me.magnum.melonds.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import me.magnum.melonds.R;
import me.magnum.melonds.model.Rom;

public class MainActivity extends AppCompatActivity {
	private static final String FRAGMENT_ROM_LIST = "ROM_LIST";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		this.addRomListFragment();
	}

	private void addRomListFragment() {
		RomListFragment romListFragment = (RomListFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_ROM_LIST);
		if (romListFragment == null) {
			romListFragment = RomListFragment.newInstance();

			getSupportFragmentManager().beginTransaction()
					.add(R.id.layout_main, romListFragment, FRAGMENT_ROM_LIST)
					.commit();
		}

		romListFragment.setRomSelectedListener(new RomListFragment.RomSelectedListener() {
			@Override
			public void onRomSelected(Rom rom) {
				loadRom(rom);
			}
		});
	}

	private void loadRom(Rom rom) {
		Intent intent = new Intent(this, RenderActivity.class);
		intent.putExtra(RenderActivity.KEY_ROM_PATH, rom.getPath());
		startActivity(intent);
	}
}
