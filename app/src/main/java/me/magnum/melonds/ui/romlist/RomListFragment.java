package me.magnum.melonds.ui.romlist;

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ImageViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import me.magnum.melonds.R;
import me.magnum.melonds.ServiceLocator;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.model.RomScanningStatus;
import me.magnum.melonds.ui.SettingsActivity;
import me.magnum.melonds.utils.ConfigurationUtils;
import me.magnum.melonds.utils.RomProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RomListFragment extends Fragment {
	private static final int REQUEST_STORAGE_PERMISSION = 1;

	private RomListViewModel romListViewModel;
	private RomSelectedListener romSelectedListener;

	private SwipeRefreshLayout swipeRefreshLayout;
	private RecyclerView romList;

	private RomListAdapter romListAdapter;

	public static RomListFragment newInstance() {
		return new RomListFragment();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.rom_list_fragment, container, false);
		this.romList = view.findViewById(R.id.list_roms);
		this.swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_roms);

		this.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				updateRomList();
			}
		});

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		this.romListViewModel = ViewModelProviders.of(this, ServiceLocator.get(ViewModelProvider.Factory.class)).get(RomListViewModel.class);

		this.romListAdapter = new RomListAdapter(getContext());
		this.romListAdapter.setRomClickListener(new RomClickListener() {
			@Override
			public void onRomClicked(Rom rom) {
				selectRom(rom);
			}

			@Override
			public void onRomConfigClicked(final Rom rom) {
				new RomConfigDialog(getActivity(), rom.getName(), rom.getConfig().clone())
						.setOnRomConfigSaveListener(new RomConfigDialog.OnRomConfigSavedListener() {
							@Override
							public void onRomConfigSaved(RomConfig romConfig) {
								romListViewModel.updateRomConfig(rom, romConfig);
							}
						})
						.show();
			}
		});

		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		this.romList.setLayoutManager(layoutManager);
		this.romList.addItemDecoration(new DividerItemDecoration(getContext(), layoutManager.getOrientation()));
		this.romList.setAdapter(this.romListAdapter);

		this.romListViewModel.getRomScanningStatus()
				.observe(this, new Observer<RomScanningStatus>() {
					@Override
					public void onChanged(@Nullable RomScanningStatus status) {
						swipeRefreshLayout.setRefreshing(status == RomScanningStatus.SCANNING);
					}
				});
		this.romListViewModel.getRoms()
				.observe(this, new Observer<List<Rom>>() {
					@Override
					public void onChanged(@Nullable List<Rom> roms) {
						romListAdapter.setRoms(roms);
					}
				});
	}

	@Override
	public void onStart() {
		super.onStart();
		if (!this.checkConfigDirectorySetup())
			return;

		if (!this.isStoragePermissionGranted()) {
			this.requestStoragePermission(false);
		}
	}

	private void updateRomList() {
		if (!isStoragePermissionGranted())
			return;

		if (!isConfigDirectorySetup())
			return;

		this.romListViewModel.refreshRoms();
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
					this.updateRomList();
				else
					Toast.makeText(getContext(), getString(R.string.info_no_storage_permission), Toast.LENGTH_LONG).show();
				break;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_rom_list_refresh:
				this.updateRomList();
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

		public void setRoms(List<Rom> roms) {
			this.roms.clear();
			this.roms.addAll(roms);
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

			int configButtonTint;
			if (rom.getConfig().loadGbaCart())
				configButtonTint = ContextCompat.getColor(context, R.color.romConfigButtonEnabled);
			else
				configButtonTint = ContextCompat.getColor(context, R.color.romConfigButtonDefault);

			ImageViewCompat.setImageTintList(romViewHolder.buttonRomConfig, ColorStateList.valueOf(configButtonTint));
			romViewHolder.buttonRomConfig.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (romClickListener != null)
						romClickListener.onRomConfigClicked(rom);
				}
			});
		}

		@Override
		public int getItemCount() {
			return this.roms.size();
		}

		public class RomViewHolder extends RecyclerView.ViewHolder {
			public ImageView imageIcon;
			public TextView textRomName;
			public TextView textRomPath;
			public AppCompatImageView buttonRomConfig;

			public RomViewHolder(@NonNull View itemView) {
				super(itemView);
				this.imageIcon = itemView.findViewById(R.id.image_rom_icon);
				this.textRomName = itemView.findViewById(R.id.text_rom_name);
				this.textRomPath = itemView.findViewById(R.id.text_rom_path);
				this.buttonRomConfig = itemView.findViewById(R.id.button_rom_config);
			}
		}
	}

	private interface RomClickListener {
		void onRomClicked(Rom rom);
		void onRomConfigClicked(Rom rom);
	}

	public interface RomSelectedListener {
		void onRomSelected(Rom rom);
	}
}
