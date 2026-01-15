/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.Intent.ACTION_SHOW_BRIGHTNESS_DIALOG;
import static android.content.Intent.EXTRA_BRIGHTNESS_DIALOG_IS_FULL_WIDTH;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinear;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

public class BrightnessLevelPreferenceController extends BasePreferenceController implements
        LifecycleObserver, OnStart, OnStop {

    private static final String TAG = "BrightnessPreferenceCtrl";
    private static final Uri BRIGHTNESS_ADJ_URI = System.getUriFor(System.SCREEN_AUTO_BRIGHTNESS_ADJ);
    private static final Uri BRIGHTNESS_MODE_URI = System.getUriFor(System.SCREEN_BRIGHTNESS_MODE);
    private static final Uri BRIGHTNESS_URI = System.getUriFor(System.SCREEN_BRIGHTNESS);
    public static final String KEY_BRIGHTNESS_SLIDER = "brightness_slider";

    private final ContentResolver mContentResolver;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final DisplayManager mDisplayManager;
    private SeekBarPreference mPreference;

    private final ContentObserver mBrightnessObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (selfChange) return;
            updateState(mPreference);
        }
    };

    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            updateState(mPreference);
        }
    };

    public BrightnessLevelPreferenceController(Context context, String key) {
        super(context, key);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            mPreference.setContinuousUpdates(true);
            mPreference.setMax(GAMMA_SPACE_MAX);
            mPreference.setMin(GAMMA_SPACE_MIN);
            mPreference.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
            
            mPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int brightness = (int) newValue;
                setBrightness(brightness);
                return true;
            });
        }
    }

    @Override
    public void updateState(Preference preference) {
        if (preference == null) return;
        
        if (UserManager.get(mContext).hasBaseUserRestriction(
                UserManager.DISALLOW_CONFIG_BRIGHTNESS, Process.myUserHandle())) {
            preference.setEnabled(false);
            return;
        }
        
        // Always enable the slider regardless of auto-brightness state
        preference.setEnabled(true);
        
        if (preference instanceof SeekBarPreference) {
            int brightness = getCurrentBrightnessValue();
            ((SeekBarPreference) preference).setProgress(brightness);
        }
    }

    @Override
    public void onStart() {
        mContentResolver.registerContentObserver(BRIGHTNESS_ADJ_URI, false, mBrightnessObserver);
        mContentResolver.registerContentObserver(BRIGHTNESS_MODE_URI, false, mBrightnessObserver);
        mContentResolver.registerContentObserver(BRIGHTNESS_URI, false, mBrightnessObserver);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
    }

    @Override
    public void onStop() {
        mContentResolver.unregisterContentObserver(mBrightnessObserver);
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
    }

    private int getCurrentBrightnessValue() {
        // Fallback to system settings
        int brightness = Settings.System.getInt(mContentResolver, 
                Settings.System.SCREEN_BRIGHTNESS, -1);
        if (brightness >= 0) {
            return convertLinearToGamma(brightness, 0, 255);
        }
        
        return GAMMA_SPACE_MIN;
    }

    private void setBrightness(int brightness) {
        int linearBrightness = convertGammaToLinear(brightness, 0, 255);
        
        // Set brightness through system settings
        Settings.System.putInt(mContentResolver, 
                Settings.System.SCREEN_BRIGHTNESS, 
                linearBrightness);
        
        // For immediate effect, we'll use the old method of setting brightness
        try {
            android.provider.Settings.System.putInt(mContentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    linearBrightness);
        } catch (Exception e) {
            Log.e(TAG, "Error setting brightness", e);
        }
    }

    private boolean isAutoBrightnessEnabled() {
        return Settings.System.getInt(mContentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }
}
