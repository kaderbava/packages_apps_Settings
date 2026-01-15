/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display;

import android.content.Context;
import androidx.annotation.NonNull;

import com.android.settings.accessibility.Flags;
import com.android.settings.widget.SeekBarPreference;
import androidx.preference.Preference;

/**
 * The brightness slider preference controller for SetupWizard.
 */
public class BrightnessLevelPreferenceControllerForSetupWizard extends
        BrightnessLevelPreferenceController {

    public BrightnessLevelPreferenceControllerForSetupWizard(@NonNull Context context,
            @NonNull String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!Flags.addBrightnessSettingsInSuw()) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        return super.getAvailabilityStatus();
    }

    @Override
    public void updateState(Preference preference) {
        if (!Flags.addBrightnessSettingsInSuw()) {
            preference.setVisible(false);
            return;
        }
        super.updateState(preference);

        if (preference instanceof SeekBarPreference) {
            SeekBarPreference seekBar = (SeekBarPreference) preference;
            seekBar.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_NONE);
        }
    }
}
