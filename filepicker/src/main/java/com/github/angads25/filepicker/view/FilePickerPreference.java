/*
 * Copyright (C) 2016 Angad Singh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.angads25.filepicker.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import androidx.preference.Preference;
import com.github.angads25.filepicker.R;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;

import java.io.File;

/**<p>
 * Created by angads25 on 15-07-2016.
 * </p>
 */

public class FilePickerPreference extends Preference implements
        DialogSelectionListener,
        Preference.OnPreferenceClickListener {
    private FilePickerDialog mDialog;
    private DialogProperties properties;
    private String titleText=null;

    public FilePickerPreference(Context context) {
        super(context);
        properties = new DialogProperties();
        setOnPreferenceClickListener(this);
    }

    public FilePickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        properties = new DialogProperties();
        initProperties(attrs);
        setOnPreferenceClickListener(this);
    }

    public FilePickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        properties = new DialogProperties();
        initProperties(attrs);
        setOnPreferenceClickListener(this);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return super.onGetDefaultValue(a, index);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (mDialog == null || !mDialog.isShowing()) {
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.dialogBundle = mDialog.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        showDialog(myState.dialogBundle);
    }

    private void showDialog(Bundle state) {
        mDialog = new FilePickerDialog(getContext());
        setProperties(properties);
        mDialog.setDialogSelectionListener(this);
        if (state != null) {
            mDialog.onRestoreInstanceState(state);
        }
        mDialog.setTitle(titleText);
        mDialog.show();
    }

    @Override
    public void onSelectedFilePaths(String[] files) {
        StringBuilder buff=new StringBuilder();
        for(String path:files)
        {   buff.append(path).append(":");
        }
        String dFiles = buff.toString();
        if (isPersistent()) {
            persistString(dFiles);
        }
        try {
            getOnPreferenceChangeListener().onPreferenceChange(this, dFiles);
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        showDialog(null);
        return false;
    }

    public void setProperties(DialogProperties properties)
    {   mDialog.setProperties(properties);
    }

    private static class SavedState extends BaseSavedState {
        Bundle dialogBundle;

        public SavedState(Parcel source) {
            super(source);
            dialogBundle = source.readBundle(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("unused")
        public static final Creator<SavedState> CREATOR =
        new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void initProperties(AttributeSet attrs) {
        TypedArray tarr=getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.FilePickerPreference,0,0);
        final int N = tarr.getIndexCount();
        for (int i = 0; i < N; ++i)
        {   int attr = tarr.getIndex(i);
            if (attr == R.styleable.FilePickerPreference_selection_mode) {
                properties.selection_mode=tarr.getInteger(R.styleable.FilePickerPreference_selection_mode, DialogConfigs.SINGLE_MODE);
            }
            else if (attr == R.styleable.FilePickerPreference_selection_type) {
                properties.selection_type=tarr.getInteger(R.styleable.FilePickerPreference_selection_type,DialogConfigs.FILE_SELECT);
            }
            else if (attr == R.styleable.FilePickerPreference_root_dir) {
                String root_dir=tarr.getString(R.styleable.FilePickerPreference_root_dir);
                if(root_dir!=null&&!root_dir.equals(""))
                {   properties.root=new File(root_dir);
                }
            }
            else if (attr == R.styleable.FilePickerPreference_error_dir) {
                String error_dir=tarr.getString(R.styleable.FilePickerPreference_error_dir);
                if(error_dir!=null&&!error_dir.equals(""))
                {   properties.error_dir=new File(error_dir);
                }
            }
            else if (attr == R.styleable.FilePickerPreference_offset_dir) {
                String offset_dir=tarr.getString(R.styleable.FilePickerPreference_offset_dir);
                if(offset_dir!=null&&!offset_dir.equals(""))
                {   properties.offset=new File(offset_dir);
                }
            }
            else if (attr == R.styleable.FilePickerPreference_extensions) {
                String extensions=tarr.getString(R.styleable.FilePickerPreference_extensions);
                if(extensions!=null&&!extensions.equals(""))
                {   properties.extensions= extensions.split(":");
                }
            }
            else if (attr == R.styleable.FilePickerPreference_title_text) {
                titleText=tarr.getString(R.styleable.FilePickerPreference_title_text);
            }
        }
        tarr.recycle();
    }
}
