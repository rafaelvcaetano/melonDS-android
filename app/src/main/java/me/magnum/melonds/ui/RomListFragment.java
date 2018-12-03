package me.magnum.melonds.ui;

import android.Manifest;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import me.magnum.melonds.InternalRomLocationCache;
import me.magnum.melonds.R;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.utils.ConfigurationUtils;
import me.magnum.melonds.utils.RomProcessor;
import me.magnum.melonds.viewmodels.RomListViewModel;

public class RomListFragment extends Fragment {
	private static final int REQUEST_STORAGE_PERMISSION = 1;

	private RomListViewModel romViewModel;
	private RomSelectedListener romSelectedListener;

	private RecyclerView romList;

	private Disposable romListDisposable;
	private RomListAdapter romListAdapter;

	public static RomListFragment newInstance() {
		return new RomListFragment();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.rom_list_fragment, container, false);
		this.romList = view.findViewById(R.id.list_roms);

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		romViewModel = ViewModelProviders.of(this, new ViewModelProvider.Factory() {
			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				return (T) new RomListViewModel(new InternalRomLocationCache(getActivity()));
			}
		}).get(RomListViewModel.class);

		this.romListAdapter = new RomListAdapter(getContext());
		this.romListAdapter.setRomClickListener(new RomClickListener() {
			@Override
			public void onRomClicked(Rom rom) {
				selectRom(rom);
			}
		});

		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		this.romList.setLayoutManager(layoutManager);
		this.romList.addItemDecoration(new DividerItemDecoration(getContext(), layoutManager.getOrientation()));
		this.romList.setAdapter(this.romListAdapter);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (!this.checkConfigDirectorySetup())
			return;

		if (!this.isStoragePermissionGranted()) {
			this.requestStoragePermission(false);
			return;
		}

		this.updateRomList(false);
	}

	private void updateRomList(boolean clearCache) {
		if (!isStoragePermissionGranted())
			return;

		if (!isConfigDirectorySetup())
			return;

		if (this.romListDisposable != null && !this.romListDisposable.isDisposed())
			this.romListDisposable.dispose();

		this.romListAdapter.clearRoms();
		this.romListDisposable = romViewModel.getRoms(clearCache)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Consumer<Rom>() {
					@Override
					public void accept(Rom newRom) throws Exception {
						romListAdapter.addRom(newRom);
					}
				}, new Consumer<Throwable>() {
					@Override
					public void accept(Throwable throwable) {
						throwable.printStackTrace();
					}
				});
	}

	private boolean isStoragePermissionGranted() {
		return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	private void requestStoragePermission(boolean overrideRationaleRequest) {
		if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			new AlertDialog.Builder(getContext())
					.setTitle(R.string.storage_permission_required)
					.setMessage(R.string.storage_permission_required_info)
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							requestStoragePermission(true);
						}
					})
					.show();
		} else {
			this.requestPermissions(
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
					REQUEST_STORAGE_PERMISSION);
		}
	}

	private boolean isConfigDirectorySetup() {
		String configDir = PreferenceManager.getDefaultSharedPreferences(getContext())
				.getString("bios_dir", null);

		if (configDir != null) {
			String[] parts = configDir.split(":");
			if (parts.length > 0)
				configDir = parts[0];
		}

		ConfigurationUtils.ConfigurationDirStatus dirStatus = ConfigurationUtils.checkConfigurationDirectory(configDir);
		return dirStatus == ConfigurationUtils.ConfigurationDirStatus.VALID;
	}

	private boolean checkConfigDirectorySetup() {
		String configDir = PreferenceManager.getDefaultSharedPreferences(getContext())
				.getString("bios_dir", null);

		if (configDir != null) {
			String[] parts = configDir.split(":");
			if (parts.length > 0)
				configDir = parts[0];
		}

		ConfigurationUtils.ConfigurationDirStatus dirStatus = ConfigurationUtils.checkConfigurationDirectory(configDir);
		if (dirStatus == ConfigurationUtils.ConfigurationDirStatus.VALID)
			return true;

		switch (dirStatus) {
			case UNSET:
				new AlertDialog.Builder(getContext())
						.setTitle(R.string.bios_dir_not_set)
						.setMessage(R.string.bios_dir_not_set_info)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Intent intent = new Intent(getContext(), SettingsActivity.class);
								startActivity(intent);
							}
						})
						.show();
				break;
			case INVALID:
				new AlertDialog.Builder(getContext())
						.setTitle(R.string.incorrect_bios_dir)
						.setMessage(R.string.incorrect_bios_dir_info)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Intent intent = new Intent(getContext(), SettingsActivity.class);
								startActivity(intent);
							}
						})
						.show();
				break;
		}

		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case REQUEST_STORAGE_PERMISSION:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
					this.updateRomList(false);
				else
					Toast.makeText(getContext(), getString(R.string.info_no_storage_permission), Toast.LENGTH_LONG).show();
				break;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_rom_list_refresh:
				this.updateRomList(true);
				return true;
			case R.id.action_settings:
				Intent intent = new Intent(getContext(), SettingsActivity.class);
				startActivity(intent);
				return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.rom_list_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	public void setRomSelectedListener(RomSelectedListener listener) {
		this.romSelectedListener = listener;
	}

	private void selectRom(Rom rom) {
		if (this.romSelectedListener != null)
			this.romSelectedListener.onRomSelected(rom);
	}

	@Override
	public void onStop() {
		if (this.romListDisposable != null)
			this.romListDisposable.dispose();

		super.onStop();
	}

	private class RomListAdapter extends RecyclerView.Adapter<RomListAdapter.RomViewHolder> {
		private Context context;
		private ArrayList<Rom> roms;
		private RomClickListener romClickListener;

		public RomListAdapter(Context context) {
			this.context = context;
			this.roms = new ArrayList<>();
		}

		public void setRomClickListener(RomClickListener listener) {
			this.romClickListener = listener;
		}

		public void clearRoms() {
			this.roms.clear();
			this.notifyDataSetChanged();
		}

		public void addRom(Rom rom) {
			this.roms.add(rom);
			this.notifyItemInserted(this.roms.size() - 1);
		}

		@NonNull
		@Override
		public RomViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
			View view = LayoutInflater.from(this.context).inflate(R.layout.item_rom, viewGroup, false);
			return new RomViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RomViewHolder romViewHolder, int i) {
			final Rom rom = this.roms.get(i);
			romViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (romClickListener != null)
						romClickListener.onRomClicked(rom);
				}
			});

			File romFile = new File(rom.getPath());
			try {
				Bitmap icon = RomProcessor.getRomIcon(romFile);
				romViewHolder.imageIcon.setImageBitmap(icon);
			} catch (Exception e) {
				e.printStackTrace();
				romViewHolder.imageIcon.setImageBitmap(null);
			}

			romViewHolder.textRomName.setText(rom.getName());
			romViewHolder.textRomPath.setText(rom.getPath());
		}

		@Override
		public int getItemCount() {
			return this.roms.size();
		}

		public class RomViewHolder extends RecyclerView.ViewHolder {
			public ImageView imageIcon;
			public TextView textRomName;
			public TextView textRomPath;

			public RomViewHolder(@NonNull View itemView) {
				super(itemView);
				this.imageIcon = itemView.findViewById(R.id.image_rom_icon);
				this.textRomName = itemView.findViewById(R.id.text_rom_name);
				this.textRomPath = itemView.findViewById(R.id.text_rom_path);
			}
		}
	}

	private interface RomClickListener {
		void onRomClicked(Rom rom);
	}

	public interface RomSelectedListener {
		void onRomSelected(Rom rom);
	}
}
