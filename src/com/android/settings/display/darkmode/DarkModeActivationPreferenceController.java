/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.display.darkmode;

import android.app.UiModeManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for activate/deactivate night mode button with theme selection
 */
public class DarkModeActivationPreferenceController extends BasePreferenceController {

    private static final String BERRY_BLACK_THEME_KEY = "berry_black_theme";

    private final UiModeManager mUiModeManager;
    private final MetricsFeatureProvider mMetricsFeatureProvider;
    private final PowerManager mPowerManager;
    private final Configuration mConfiguration = new Configuration();
    private final ContentObserver mContentObserver;
    private final ContentObserver mBlackThemeObserver;

    private RadioButton mRadioLight;
    private RadioButton mRadioDark;
    private RadioButton mRadioBlack;
    private ImageView mLightModeImageView;
    private ImageView mDarkModeImageView;
    private ImageView mBlackModeImageView;
    private View mScheduleButton;
    private TextView mScheduleTextView;
    private LayoutPreference mPreference;

    public DarkModeActivationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mPowerManager = context.getSystemService(PowerManager.class);
        mUiModeManager = context.getSystemService(UiModeManager.class);
        mMetricsFeatureProvider = FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
        mConfiguration.setTo(context.getResources().getConfiguration());
        
        mContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                refreshUi();
            }
        };

        mBlackThemeObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                refreshUi();
            }
        };
    }

    public void onStart() {
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
                false, mContentObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(BERRY_BLACK_THEME_KEY),
                false, mBlackThemeObserver);
    }

    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        mContext.getContentResolver().unregisterContentObserver(mBlackThemeObserver);
    }

    @Override
    public final void updateState(Preference preference) {
        refreshUi();
    }

    private void refreshUi() {
        if (mPreference == null) return;
        
        final boolean batterySaver = mPowerManager.isPowerSaveMode();
        final boolean isBlackThemeEnabled = isBlackThemeEnabled();
        final boolean isNightMode = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_YES) != 0;

        if (batterySaver) {
            if (!isBlackThemeEnabled) {
                setBlackThemeEnabled(true);
            }
            if (!isNightMode) {
            mUiModeManager.setNightModeActivated(true);
            }
            return;
        }

        updateNightMode(isNightMode, isBlackThemeEnabled);
        updateScheduleSummary(batterySaver, isNightMode || isBlackThemeEnabled);
    }

    private void updateNightMode(boolean active, boolean isBlackTheme) {
        mRadioLight.setChecked(!active && !isBlackTheme);
        mRadioDark.setChecked(active && !isBlackTheme);
        mRadioBlack.setChecked(isBlackTheme);
        updateScheduleIconVisibility(active || isBlackTheme);
    }

    private void updateScheduleSummary(boolean batterySaver, boolean active) {
        if (mScheduleTextView == null) return;
        
        if (batterySaver) {
            final int stringId = active
                    ? R.string.dark_ui_mode_disabled_summary_dark_theme_on
                    : R.string.dark_ui_mode_disabled_summary_dark_theme_off;
            mScheduleTextView.setText(mContext.getString(stringId));
        } else {
            mScheduleTextView.setText(AutoDarkTheme.getStatus(mContext, active));
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());

        mLightModeImageView = mPreference.findViewById(R.id.iv_light_image);
        mDarkModeImageView = mPreference.findViewById(R.id.iv_dark_image);
        mBlackModeImageView = mPreference.findViewById(R.id.iv_black_image);
        mScheduleButton = mPreference.findViewById(R.id.schedule_button);
        mScheduleTextView = mPreference.findViewById(R.id.schedule_text);

        mRadioLight = mPreference.findViewById(R.id.radio_light);
        mRadioDark = mPreference.findViewById(R.id.radio_dark);
        mRadioBlack = mPreference.findViewById(R.id.radio_black);

        refreshUi();

        mRadioLight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setBlackThemeEnabled(false);
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
            }
        });

        mRadioDark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setBlackThemeEnabled(false);
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES); 
            }
        });

        mRadioBlack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
        	setBlackThemeEnabled(true);
            }
        });

        mLightModeImageView.setOnClickListener(v -> mRadioLight.setChecked(true));
        mDarkModeImageView.setOnClickListener(v -> mRadioDark.setChecked(true));
        mBlackModeImageView.setOnClickListener(v -> mRadioBlack.setChecked(true));

        mScheduleButton.setOnClickListener(v -> {
            new SubSettingLauncher(mContext)
                .setDestination(DarkModeSettingsFragment.class.getName())
                .setSourceMetricsCategory(SettingsEnums.DARK_UI_SETTINGS)
                .launch();
        });
    }

    private boolean isBlackThemeEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                BERRY_BLACK_THEME_KEY, 0) == 1;
    }

    private void setBlackThemeEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                BERRY_BLACK_THEME_KEY, enabled ? 1 : 0);
        refreshUi();
    }

    private void updateScheduleIconVisibility(boolean isDarkModeEnabled) {
        if (mScheduleButton != null) {
            mScheduleButton.setVisibility(isDarkModeEnabled ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE_UNSEARCHABLE;
    }
}
