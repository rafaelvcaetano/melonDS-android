package me.magnum.melonds.ui.romlist;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.DialogTitle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import me.magnum.melonds.R;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.utils.UIUtils;

import java.io.File;

public class RomConfigDialog extends AlertDialog {
    public interface OnRomConfigSavedListener {
        void onRomConfigSaved(RomConfig romConfig);
    }

    private String title;
    private RomConfig romConfig;
    private OnRomConfigSavedListener saveListener;

    private View prefLoadGbaRomView;
    private View prefGbaRomPathView;
    private View prefGbaSavePathView;
    private Switch loadGbaRomSwitch;
    private TextView gbaRomPathTextView;
    private TextView gbaSavePathTextView;

    public RomConfigDialog(@NonNull Context context, String title, RomConfig romConfig) {
        super(context);
        this.title = title;
        this.romConfig = romConfig;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_rom_config);
        setCancelable(true);

        this.prefLoadGbaRomView = this.findViewById(R.id.layout_rom_config_load_gba_rom);
        this.prefGbaRomPathView = this.findViewById(R.id.layout_rom_config_gba_rom_path);
        this.prefGbaSavePathView = this.findViewById(R.id.layout_rom_config_gba_save_path);
        this.loadGbaRomSwitch = this.findViewById(R.id.switch_load_gba_rom);
        this.gbaRomPathTextView = this.findViewById(R.id.label_preference_gba_cart_path_value);
        this.gbaSavePathTextView = this.findViewById(R.id.label_preference_gba_save_path_value);

        this.prefLoadGbaRomView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadGbaRomSwitch.setChecked(!loadGbaRomSwitch.isChecked());
            }
        });
        this.prefGbaRomPathView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.FILE_SELECT;
                properties.root = Environment.getExternalStorageDirectory();
                FilePickerDialog pickerDialog = new FilePickerDialog(getContext(), properties);
                pickerDialog.setDialogSelectionListener(new DialogSelectionListener() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        if (files.length > 0) {
                            if (new File(files[0]).isFile())
                                onGbaRomPathSelected(files[0]);
                        }
                    }
                });
                pickerDialog.show();
            }
        });
        this.prefGbaSavePathView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.FILE_SELECT;
                properties.root = Environment.getExternalStorageDirectory();
                FilePickerDialog pickerDialog = new FilePickerDialog(getContext(), properties);
                pickerDialog.setDialogSelectionListener(new DialogSelectionListener() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        if (files.length > 0) {
                            if (new File(files[0]).isFile())
                                onGbaSavePathSelected(files[0]);
                        }
                    }
                });
                pickerDialog.show();
            }
        });
        this.loadGbaRomSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setLoadGbaRom(isChecked);
            }
        });

        ((DialogTitle) this.findViewById(R.id.text_rom_config_title)).setText(title);
        this.loadGbaRomSwitch.setChecked(romConfig.loadGbaCart());
        this.gbaRomPathTextView.setText(getPathOrDefault(romConfig.getGbaCartPath()));
        this.gbaSavePathTextView.setText(getPathOrDefault(romConfig.getGbaSavePath()));
        UIUtils.setViewEnabled(this.prefGbaRomPathView, romConfig.loadGbaCart());
        UIUtils.setViewEnabled(this.prefGbaSavePathView, romConfig.loadGbaCart());

        this.findViewById(R.id.button_rom_config_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveListener != null)
                    saveListener.onRomConfigSaved(romConfig);
                dismiss();
            }
        });
        this.findViewById(R.id.button_rom_config_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    public RomConfigDialog setOnRomConfigSaveListener(OnRomConfigSavedListener listener) {
        this.saveListener = listener;
        return this;
    }

    private void setLoadGbaRom(boolean loadGbaRom) {
        this.romConfig.setLoadGbaCart(loadGbaRom);
        UIUtils.setViewEnabled(this.prefGbaRomPathView, loadGbaRom);
        UIUtils.setViewEnabled(this.prefGbaSavePathView, loadGbaRom);
    }

    private void onGbaRomPathSelected(String romPath) {
        this.romConfig.setGbaCartPath(romPath);
        this.gbaRomPathTextView.setText(getPathOrDefault(romPath));
    }

    private void onGbaSavePathSelected(String savePath) {
        this.romConfig.setGbaSavePath(savePath);
        this.gbaSavePathTextView.setText(getPathOrDefault(savePath));
    }

    private String getPathOrDefault(String path) {
        if (path != null)
            return path;

        return getContext().getString(R.string.not_set);
    }
}
