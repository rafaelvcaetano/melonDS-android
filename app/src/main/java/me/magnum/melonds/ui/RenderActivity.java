package me.magnum.melonds.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.magnum.melonds.*;
import me.magnum.melonds.model.ControllerConfiguration;
import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.RendererConfiguration;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.parcelables.RomParcelable;
import me.magnum.melonds.renderer.DSRenderer;
import me.magnum.melonds.repositories.SettingsRepository;
import me.magnum.melonds.ui.input.ButtonsInputHandler;
import me.magnum.melonds.ui.input.DpadInputHandler;
import me.magnum.melonds.ui.input.SingleButtonInputHandler;
import me.magnum.melonds.ui.input.TouchscreenInputHandler;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class RenderActivity extends AppCompatActivity implements DSRenderer.RendererListener {
	static {
		System.loadLibrary("melonDS-android-frontend");
	}

	private static final int REQUEST_SETTINGS = 1;

	public static final String KEY_ROM = "rom";

	private enum PauseMenuOptions {
		SETTINGS(R.string.settings),
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
	private RelativeLayout inputButtonsLayout;
	private ImageView imageToggleTouch;
	private TextView textFps;

	private SettingsRepository settingsRepository;
	private INativeInputListener nativeInputListener;
	private boolean emulatorReady;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setupFullscreen();
		setContentView(R.layout.activity_render);

		this.settingsRepository = ServiceLocator.get(SettingsRepository.class);

		RomParcelable romParcelable = getIntent().getParcelableExtra(KEY_ROM);
		if (romParcelable == null || romParcelable.getRom() == null)
			throw new NullPointerException("No ROM was specified");

		final Rom rom = romParcelable.getRom();

		MelonTouchHandler melonTouchHandler = new MelonTouchHandler();

		this.dsRenderer = new DSRenderer(this.buildRendererConfiguration());
		this.dsRenderer.setRendererListener(this);

		this.dsSurface = findViewById(R.id.surface_main);
		this.dsSurface.setEGLContextClientVersion(2);
		this.dsSurface.setRenderer(this.dsRenderer);

		this.textFps = findViewById(R.id.text_fps);
		this.textFps.setVisibility(View.INVISIBLE);
		final TextView loadingText = findViewById(R.id.text_loading);

		this.imageToggleTouch = findViewById(R.id.image_touch_toggle);
		this.inputButtonsLayout = findViewById(R.id.layout_input_buttons);

		this.inputArea = findViewById(R.id.view_input);
		this.inputArea.setOnTouchListener(new TouchscreenInputHandler(melonTouchHandler));

		findViewById(R.id.image_dpad).setOnTouchListener(new DpadInputHandler(melonTouchHandler));
		findViewById(R.id.image_buttons).setOnTouchListener(new ButtonsInputHandler(melonTouchHandler));
		findViewById(R.id.image_button_l).setOnTouchListener(new SingleButtonInputHandler(melonTouchHandler, Input.L));
		findViewById(R.id.image_button_r).setOnTouchListener(new SingleButtonInputHandler(melonTouchHandler, Input.R));
		findViewById(R.id.image_button_select).setOnTouchListener(new SingleButtonInputHandler(melonTouchHandler, Input.SELECT));
		findViewById(R.id.image_button_start).setOnTouchListener(new SingleButtonInputHandler(melonTouchHandler, Input.START));
		this.adjustInputOpacity();

		// TODO: load configuration from preferences
		this.nativeInputListener = new InputProcessor(new ControllerConfiguration(new HashMap<Integer, Input>(), 0f), melonTouchHandler);

		imageToggleTouch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (inputButtonsLayout.getVisibility() == View.VISIBLE) {
					inputButtonsLayout.setVisibility(View.INVISIBLE);
					imageToggleTouch.setImageDrawable(getResources().getDrawable(R.drawable.ic_touch_disabled));
				} else {
					inputButtonsLayout.setVisibility(View.VISIBLE);
					imageToggleTouch.setImageDrawable(getResources().getDrawable(R.drawable.ic_touch_enabled));
				}
			}
		});

		Single.create(new SingleOnSubscribe<MelonEmulator.LoadResult>() {
			@Override
			public void subscribe(SingleEmitter<MelonEmulator.LoadResult> emitter) throws Exception {
				boolean showBios = settingsRepository.showBootScreen();

				MelonEmulator.setupEmulator(getConfigDirPath(), getAssets());

				String sramPath = getSRAMPath(rom.getPath());

				MelonEmulator.LoadResult loadResult = MelonEmulator.loadRom(rom.getPath(), sramPath, !showBios, rom.getConfig().loadGbaCart(), rom.getConfig().getGbaCartPath(), rom.getConfig().getGbaSavePath());
				if (loadResult == MelonEmulator.LoadResult.NDS_FAILED)
					throw new Exception("Failed to load ROM");

				MelonEmulator.startEmulation();
				emitter.onSuccess(loadResult);
			}
		}).subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new SingleObserver<MelonEmulator.LoadResult>() {
					@Override
					public void onSubscribe(Disposable d) {
					}

					@Override
					public void onSuccess(MelonEmulator.LoadResult loadResult) {
						if (loadResult == MelonEmulator.LoadResult.SUCCESS_GBA_FAILED)
							Toast.makeText(RenderActivity.this, R.string.error_load_gba_rom, Toast.LENGTH_SHORT).show();

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

	private RendererConfiguration buildRendererConfiguration() {
		return new RendererConfiguration(this.settingsRepository.getVideoFiltering());
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

	private void adjustInputOpacity() {
		int opacity = this.settingsRepository.getSoftInputOpacity();

		float alpha = opacity / 100f;
		this.inputButtonsLayout.setAlpha(alpha);
		this.imageToggleTouch.setAlpha(alpha);
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
				.setTitle(R.string.pause)
				.setItems(options, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (values[which]) {
							case SETTINGS:
								Intent settingsIntent = new Intent(RenderActivity.this, SettingsActivity.class);
								startActivityForResult(settingsIntent, REQUEST_SETTINGS);
								break;
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case REQUEST_SETTINGS:
				this.dsRenderer.updateRendererConfiguration(this.buildRendererConfiguration());
				this.adjustInputOpacity();
				break;
		}
	}

	private String getConfigDirPath() {
		String path = this.settingsRepository.getBiosDirectory();

		if (path == null)
			throw new IllegalStateException("BIOS directory not set");

		return path + "/";
	}

	private String getSRAMPath(String romPath) {
		File romFile = new File(romPath);
		String filename = romFile.getName();

		boolean useRomDir = this.settingsRepository.saveNextToRomFile();

		String sramDir;
		if (useRomDir) {
			sramDir = romFile.getParent();
		} else {
			sramDir = this.settingsRepository.getSaveFileDirectory();

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
	public boolean dispatchGenericMotionEvent(MotionEvent ev) {
		return this.nativeInputListener.onMotionEvent(ev);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (this.nativeInputListener.onKeyEvent(event))
			return true;

		return super.dispatchKeyEvent(event);
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
