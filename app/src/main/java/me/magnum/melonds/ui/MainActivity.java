package me.magnum.melonds.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import me.magnum.melonds.R;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.parcelables.RomParcelable;
import me.magnum.melonds.ui.romlist.RomListFragment;

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
		intent.putExtra(RenderActivity.KEY_ROM, new RomParcelable(rom));
		startActivity(intent);
	}
}
