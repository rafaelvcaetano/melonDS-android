package me.magnum.melonds.ui.inputsetup;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import me.magnum.melonds.model.ControllerConfiguration;
import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.InputConfig;
import me.magnum.melonds.repositories.SettingsRepository;

import java.util.ArrayList;
import java.util.List;

public class InputSetupViewModel extends ViewModel {
    private SettingsRepository settingsRepository;

    private ArrayList<StatefulInputConfig> inputConfigs;
    private BehaviorSubject<List<StatefulInputConfig>> inputConfigsBehaviour;
    private CompositeDisposable disposables;

    public InputSetupViewModel(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;

        ControllerConfiguration currentConfiguration = settingsRepository.getControllerConfiguration();
        this.inputConfigs = new ArrayList<>();
        for (InputConfig config : currentConfiguration.getConfigList()) {
            this.inputConfigs.add(new StatefulInputConfig(config.clone()));
        }

        this.inputConfigsBehaviour = BehaviorSubject.createDefault((List<StatefulInputConfig>) this.inputConfigs);
        this.disposables = new CompositeDisposable();
    }

    public LiveData<List<StatefulInputConfig>> getInputConfig() {
        final MutableLiveData<List<StatefulInputConfig>> inputConfigLiveData = new MutableLiveData<>();
        Disposable disposable = this.inputConfigsBehaviour.subscribe(new Consumer<List<StatefulInputConfig>>() {
            @Override
            public void accept(List<StatefulInputConfig> statefulInputConfigs) {
                inputConfigLiveData.setValue(statefulInputConfigs);
            }
        });
        this.disposables.add(disposable);

        return inputConfigLiveData;
    }

    public void startUpdatingInputConfig(Input input) {
        for (int i = 0; i < inputConfigs.size(); i++) {
            InputConfig inputConfig = inputConfigs.get(i).getInputConfig();
            if (inputConfig.getInput() == input) {
                inputConfigs.get(i).setIsBeingConfigured(true);
                this.onConfigsChanged();
                break;
            }
        }
    }

    public void stopUpdatingInputConfig(Input input) {
        for (int i = 0; i < inputConfigs.size(); i++) {
            InputConfig inputConfig = inputConfigs.get(i).getInputConfig();
            if (inputConfig.getInput() == input) {
                inputConfigs.get(i).setIsBeingConfigured(false);
                this.onConfigsChanged();
                break;
            }
        }
    }

    public void updateInputConfig(Input input, int key) {
        for (int i = 0; i < inputConfigs.size(); i++) {
            InputConfig inputConfig = inputConfigs.get(i).getInputConfig();
            if (inputConfig.getInput() == input) {
                inputConfig.setKey(key);
                inputConfigs.get(i).setIsBeingConfigured(false);
                this.onConfigsChanged();
                break;
            }
        }
    }

    public void clearInput(Input input) {
        updateInputConfig(input, InputConfig.KEY_NOT_SET);
    }

    private void onConfigsChanged() {
        inputConfigsBehaviour.onNext(this.inputConfigs);

        ControllerConfiguration currentConfiguration = this.buildCurrentControllerConfiguration();
        this.settingsRepository.setControllerConfiguration(currentConfiguration);
    }

    private ControllerConfiguration buildCurrentControllerConfiguration() {
        ArrayList<InputConfig> configs = new ArrayList<>();
        for (StatefulInputConfig statefulConfig : this.inputConfigs) {
            configs.add(statefulConfig.getInputConfig().clone());
        }
        return new ControllerConfiguration(configs);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        this.disposables.dispose();
    }
}
