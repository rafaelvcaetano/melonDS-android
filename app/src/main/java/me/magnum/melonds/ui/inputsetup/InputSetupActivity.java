package me.magnum.melonds.ui.inputsetup;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.magnum.melonds.R;
import me.magnum.melonds.ServiceLocator;
import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.InputConfig;

import java.util.ArrayList;
import java.util.List;

public class InputSetupActivity extends AppCompatActivity {
    private InputSetupViewModel viewModel;

    private InputListAdapter inputListAdapter;
    private boolean waitingForInput;
    private InputConfig inputUnderConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input_setup);

        this.viewModel = new ViewModelProvider(this, ServiceLocator.get(ViewModelProvider.Factory.class)).get(InputSetupViewModel.class);

        this.inputListAdapter = new InputListAdapter(new OnInputConfigClickedListener() {
            @Override
            public void onInputConfigClicked(InputConfig inputConfig) {
                if (inputUnderConfiguration != null)
                    viewModel.stopUpdatingInputConfig(inputUnderConfiguration.getInput());

                viewModel.startUpdatingInputConfig(inputConfig.getInput());
                waitingForInput = true;
                inputUnderConfiguration = inputConfig;
            }

            @Override
            public void onInputConfigCleared(InputConfig inputConfig) {
                if (inputUnderConfiguration != null) {
                    if (inputConfig.getInput() == inputUnderConfiguration.getInput())
                        viewModel.stopUpdatingInputConfig(inputConfig.getInput());

                    inputUnderConfiguration = null;
                    waitingForInput = false;
                }

                viewModel.clearInput(inputConfig.getInput());
            }
        });
        this.inputListAdapter.setHasStableIds(true);

        RecyclerView inputList = findViewById(R.id.list_input);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        inputList.setLayoutManager(layoutManager);
        inputList.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));
        inputList.setAdapter(this.inputListAdapter);

        viewModel.getInputConfig().observe(this, new Observer<List<StatefulInputConfig>>() {
            @Override
            public void onChanged(List<StatefulInputConfig> statefulInputConfigs) {
                inputListAdapter.setInputList(statefulInputConfigs);
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (waitingForInput) {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                viewModel.updateInputConfig(inputUnderConfiguration.getInput(), event.getKeyCode());
            } else {
                viewModel.stopUpdatingInputConfig(inputUnderConfiguration.getInput());
            }

            waitingForInput = false;
            inputUnderConfiguration = null;
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private static int getInputName(Input input) {
        switch (input) {
            case A: return R.string.input_a;
            case B: return R.string.input_b;
            case X: return R.string.input_x;
            case Y: return R.string.input_y;
            case LEFT: return R.string.input_left;
            case RIGHT: return R.string.input_right;
            case UP: return R.string.input_up;
            case DOWN: return R.string.input_down;
            case L: return R.string.input_l;
            case R: return R.string.input_r;
            case START: return R.string.input_start;
            case SELECT: return R.string.input_select;
            case HINGE: return R.string.input_lid;
            case PAUSE: return R.string.input_pause;
            case FAST_FORWARD: return R.string.input_fast_forward;
            default: return -1;
        }
    }

    private class InputListAdapter extends RecyclerView.Adapter<InputListAdapter.InputViewHolder> {
        public class InputViewHolder extends RecyclerView.ViewHolder {
            private TextView textInputName;
            private TextView textAssignedInputName;
            private ImageView imageClearInput;

            private StatefulInputConfig inputConfig;

            public InputViewHolder(@NonNull View itemView) {
                super(itemView);
                this.textInputName = itemView.findViewById(R.id.text_input_name);
                this.textAssignedInputName = itemView.findViewById(R.id.text_assigned_input_name);
                this.imageClearInput = itemView.findViewById(R.id.image_input_clear);
            }

            public void setInputConfig(StatefulInputConfig config) {
                this.inputConfig = config;

                int inputNameResource = getInputName(config.getInputConfig().getInput());

                if (inputNameResource == -1)
                    this.textInputName.setText(config.getInputConfig().getInput().toString());
                else
                    this.textInputName.setText(inputNameResource);

                this.imageClearInput.setVisibility(config.getInputConfig().hasKeyAssigned() ? View.VISIBLE : View.GONE);

                if (config.isBeingConfigured()) {
                    this.textAssignedInputName.setText(R.string.press_any_button);
                } else {
                    if (config.getInputConfig().hasKeyAssigned()) {
                        String keyCodeString = KeyEvent.keyCodeToString(config.getInputConfig().getKey());
                        String keyName = keyCodeString.replace("KEYCODE", "").replace("_", " ");
                        this.textAssignedInputName.setText(keyName);
                    } else {
                        this.textAssignedInputName.setText(R.string.not_set);
                    }
                }
            }

            public InputConfig getInputConfig() {
                return inputConfig.getInputConfig();
            }
        }

        private ArrayList<StatefulInputConfig> inputList;
        private OnInputConfigClickedListener inputConfigClickedListener;

        public InputListAdapter(OnInputConfigClickedListener inputConfigClickedListener) {
            this.inputList = new ArrayList<>();
            this.inputConfigClickedListener = inputConfigClickedListener;
        }

        public void setInputList(List<StatefulInputConfig> inputList) {
            this.inputList.clear();
            this.inputList.addAll(inputList);
            this.notifyDataSetChanged();
        }

        @NonNull
        @Override
        public InputViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_input, parent, false);
            final InputViewHolder viewHolder = new InputViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    inputConfigClickedListener.onInputConfigClicked(viewHolder.getInputConfig());
                }
            });
            viewHolder.imageClearInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    inputConfigClickedListener.onInputConfigCleared(viewHolder.getInputConfig());
                }
            });

            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull InputViewHolder holder, int position) {
            holder.setInputConfig(inputList.get(position));
        }

        @Override
        public long getItemId(int position) {
            return this.inputList.get(position).getInputConfig().getInput().ordinal();
        }

        @Override
        public int getItemCount() {
            return inputList.size();
        }
    }

    private interface OnInputConfigClickedListener {
        void onInputConfigClicked(InputConfig inputConfig);
        void onInputConfigCleared(InputConfig inputConfig);
    }
}
