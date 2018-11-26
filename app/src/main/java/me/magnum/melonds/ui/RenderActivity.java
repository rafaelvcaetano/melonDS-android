package me.magnum.melonds.ui;

import android.content.DialogInterface;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.magnum.melonds.BuildConfig;
import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.R;
import me.magnum.melonds.renderer.DSRenderer;
import me.magnum.melonds.utils.PreferenceDirectoryUtils;

public class RenderActivity extends AppCompatActivity implements DSRenderer.RendererListener {
	static {
		System.loadLibrary("melonDS-android-frontend");
	}

	public static final String KEY_ROM_PATH = "rom_path";

	private enum PauseMenuOptions {
		EXIT(R.string.exit);

		private int textResource;

		PauseMenuOptions(int textResource) {
			this.textResource = textResource;
		}

		private int getTextResource() {
			return this.textResource;
		}
	}

	private GLSurfaceView dsSurface;
	private DSRenderer dsRenderer;
	private View inputArea;
	private TextView textFps;

	private boolean emulatorReady;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setupFullscreen();
		setContentView(R.layout.activity_render);

		final String romPath = getIntent().getStringExtra(KEY_ROM_PATH);
		if (romPath == null)
			throw new NullPointerException("No ROM path was specified");

		this.dsRenderer = new DSRenderer();
		this.dsRenderer.setRendererListener(this);

		this.dsSurface = findViewById(R.id.surface_main);
		this.dsSurface.setEGLContextClientVersion(2);
		this.dsSurface.setRenderer(this.dsRenderer);

		this.textFps = findViewById(R.id.text_fps);
		this.textFps.setVisibility(View.INVISIBLE);
		final TextView loadingText = findViewById(R.id.text_loading);

		this.inputArea = findViewById(R.id.view_input);
		this.inputArea.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						MelonEmulator.onKeyPress(16 + 6);
					case MotionEvent.ACTION_MOVE:
						float x = event.getX();
						float y = event.getY();
						int dsX = (int) (x / v.getWidth() * 256);
						int dsY = (int) (y / v.getHeight() * 192);

						MelonEmulator.onScreenTouch(dsX, dsY);
						break;
					case MotionEvent.ACTION_UP:
						MelonEmulator.onKeyRelease(16 + 6);
						MelonEmulator.onScreenRelease();
						break;
				}
				return true;
			}
		});

		Completable.create(new CompletableOnSubscribe() {
			@Override
			public void subscribe(CompletableEmitter emitter) throws Exception {
				MelonEmulator.setupEmulator(getConfigDirPath());

				String sramPath = getSRAMPath(romPath);
				if (!MelonEmulator.loadRom(romPath, sramPath))
					throw new Exception("Failed to load ROM");

				MelonEmulator.startEmulation();
				emitter.onComplete();
			}
		}).subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new CompletableObserver() {
					@Override
					public void onSubscribe(Disposable d) {
					}

					@Override
					public void onComplete() {
						textFps.setVisibility(View.VISIBLE);
						loadingText.setVisibility(View.GONE);
						emulatorReady = true;
					}

					@Override
					public void onError(Throwable e) {
						Toast.makeText(RenderActivity.this, R.string.error_load_rom, Toast.LENGTH_SHORT).show();
						finish();
					}
				});
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.dsSurface.onResume();

		if (this.emulatorReady)
			MelonEmulator.resumeEmulation();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		this.setupFullscreen();
	}

	private void setupFullscreen() {
		int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					  View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
					  View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
					  View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
					  View.SYSTEM_UI_FLAG_FULLSCREEN;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			uiFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

		this.getWindow().getDecorView().setSystemUiVisibility(uiFlags);
	}

	@Override
	public void onBackPressed() {
		final PauseMenuOptions[] values = PauseMenuOptions.values();
		String[] options = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			PauseMenuOptions option = values[i];
			options[i] = getString(option.getTextResource());
		}

		MelonEmulator.pauseEmulation();
		new AlertDialog.Builder(this)
				.setTitle("Pause")
				.setItems(options, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (values[which]) {
							case EXIT:
								finish();
								break;
						}
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						MelonEmulator.resumeEmulation();
					}
				})
				.show();
	}

	private String getConfigDirPath() {
		String preference = PreferenceManager.getDefaultSharedPreferences(this)
				.getString("bios_dir", null);

		String path = PreferenceDirectoryUtils.getSingleDirectoryFromPreference(preference);

		if (path == null)
			throw new IllegalStateException("BIOS directory not set");

		return path + "/";
	}

	private String getSRAMPath(String romPath) {
		File romFile = new File(romPath);
		String filename = romFile.getName();

		boolean useRomDir = PreferenceManager.getDefaultSharedPreferences(this)
				.getBoolean("use_rom_dir", true);

		String sramDir;
		if (useRomDir) {
			sramDir = romFile.getParent();
		} else {
			String preference = PreferenceManager.getDefaultSharedPreferences(this)
					.getString("sram_dir", null);

			sramDir = PreferenceDirectoryUtils.getSingleDirectoryFromPreference(preference);

			// If no directory is set, revert to using the ROM's directory
			if (sramDir == null)
				sramDir = romFile.getParent();
		}

		String nameWithoutExtension = filename.substring(0, filename.length() - 4);
		String sramFileName = nameWithoutExtension + ".sav";

		return new File(sramDir, sramFileName).getAbsolutePath();
	}

	@Override
	public void onRendererSizeChanged(final int width, final int height) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				float dsAspectRatio = 192 / 256f;
				int screenHeight = (int) (width * dsAspectRatio);

				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, screenHeight);
				params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				params.bottomMargin = (int) dsRenderer.getBottom();
				inputArea.setLayoutParams(params);
			}
		});
	}

	@Override
	public void updateFrameBuffer(ByteBuffer dst) {
		if (!this.emulatorReady)
			return;

		MelonEmulator.copyFrameBuffer(dst);
		final int fps = MelonEmulator.getFPS();

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textFps.setText(getString(R.string.info_fps, fps));
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.dsSurface.onPause();

		if (this.emulatorReady)
			MelonEmulator.pauseEmulation();
	}

	@Override
	protected void onDestroy() {
		if (this.emulatorReady)
			MelonEmulator.stopEmulation();

		super.onDestroy();
	}
}
