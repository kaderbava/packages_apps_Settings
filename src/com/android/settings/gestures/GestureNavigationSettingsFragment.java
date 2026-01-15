/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.gestures;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.preference.Preference;

import com.android.internal.util.infinity.ThemeUtils;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.LabeledSeekBarPreference;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.search.SearchIndexable;

/**
 * A fragment to include all the settings related to Gesture Navigation mode.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class GestureNavigationSettingsFragment extends DashboardFragment
    implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "GestureNavigationSettingsFragment";

    public static final String GESTURE_NAVIGATION_SETTINGS =
            "com.android.settings.GESTURE_NAVIGATION_SETTINGS";

    private static final String LEFT_EDGE_SEEKBAR_KEY = "gesture_left_back_sensitivity";
    private static final String RIGHT_EDGE_SEEKBAR_KEY = "gesture_right_back_sensitivity";
    private static final String GESTURE_BACK_HEIGHT_KEY = "gesture_back_height";
    private static final String GESTURE_NAVBAR_LENGTH_KEY = "gesture_navbar_length_preference";
    private static final String GESTURE_NAVBAR_HEIGHT_MODE_KEY = "gesture_navbar_height_preference";
    private static final String KEY_HIDE_IME_STYLE = "hide_ime_space_style";

    private static final String[] HIDE_IME_OVERLAYS = {
            "com.android.system.theme.hide_ime_space_narrow",
            "com.android.system.theme.hide_ime_space_no_space",
    };

    private WindowManager mWindowManager;
    private BackGestureIndicatorView mIndicatorView;
    private ThemeUtils mThemeUtils;

    private float[] mBackGestureInsetScales;
    private float mDefaultBackGestureInset;

    private float[] mBackGestureHeightScales = { 0f, 1f, 2f, 3f };
    private int mCurrentRightWidth;
    private int mCurrentLefttWidth;
    private LabeledSeekBarPreference mGestureNavbarLengthPreference;
    private Preference mHideImePref;

    public GestureNavigationSettingsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mThemeUtils = new ThemeUtils(getActivity());
        mIndicatorView = new BackGestureIndicatorView(getActivity());
        mWindowManager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Resources res = getActivity().getResources();
        mDefaultBackGestureInset = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
        mBackGestureInsetScales = getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_backGestureInsetScales));

        initSeekBarPreference(LEFT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(RIGHT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(GESTURE_BACK_HEIGHT_KEY);
        initGestureNavbarLengthPreference();
        initGestureNavbarHeightPreference();

        mHideImePref = findPreference(KEY_HIDE_IME_STYLE);
        mHideImePref.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                getActivity().getWindow().getAttributes()));
    }

    @Override
    public void onPause() {
        super.onPause();

        mWindowManager.removeView(mIndicatorView);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.gesture_navigation_settings;
    }

    @Override
    public int getHelpResource() {
        // TODO(b/146001201): Replace with gesture navigation help page when ready.
        return R.string.help_uri_default;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURE_NAV_BACK_SENSITIVITY_DLG;
    }

    private void initSeekBarPreference(final String key) {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(key);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);

        String settingsKey;
        float initScale = 0;

        switch(key) {
            case LEFT_EDGE_SEEKBAR_KEY:
                settingsKey = Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT;
                break;
            case RIGHT_EDGE_SEEKBAR_KEY:
                settingsKey = Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT;
                break;
            case GESTURE_BACK_HEIGHT_KEY:
                settingsKey = Settings.System.BACK_GESTURE_HEIGHT;
                break;
            default:
                settingsKey = "";
                break;
        }

        if (settingsKey != "") {
            initScale = Settings.Secure.getFloat(
                  getContext().getContentResolver(), settingsKey, 1.0f);
        }

        // needed if we just change the height
        float currentWidthScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT, 1.0f);
        mCurrentRightWidth = (int) (mDefaultBackGestureInset * currentWidthScale);
        currentWidthScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT, 1.0f);
        mCurrentLefttWidth = (int) (mDefaultBackGestureInset * currentWidthScale);

        if (key == GESTURE_BACK_HEIGHT_KEY) {
            mBackGestureInsetScales = mBackGestureHeightScales;
            initScale = Settings.System.getInt(
                    getContext().getContentResolver(), settingsKey, 0);
        }

        // Find the closest value to initScale
        float minDistance = Float.MAX_VALUE;
        int minDistanceIndex = -1;
        for (int i = 0; i < mBackGestureInsetScales.length; i++) {
            float d = Math.abs(mBackGestureInsetScales[i] - initScale);
            if (d < minDistance) {
                minDistance = d;
                minDistanceIndex = i;
            }
        }
        pref.setProgress(minDistanceIndex);

        pref.setOnPreferenceChangeListener((p, v) -> {
            if (key != GESTURE_BACK_HEIGHT_KEY) {
                final int width = (int) (mDefaultBackGestureInset * mBackGestureInsetScales[(int) v]);
                mIndicatorView.setIndicatorWidth(width, key == LEFT_EDGE_SEEKBAR_KEY);
                if (key == LEFT_EDGE_SEEKBAR_KEY) {
                    mCurrentLefttWidth = width;
                } else {
                    mCurrentRightWidth = width;
                }
            } else {
                final int heightScale = (int) (mBackGestureInsetScales[(int) v]);
                mIndicatorView.setIndicatorHeightScale(heightScale);
                // dont use updateViewLayout else it will animate
                mWindowManager.removeView(mIndicatorView);
                mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                        getActivity().getWindow().getAttributes()));
                // peek the indicators
                mIndicatorView.setIndicatorWidth(mCurrentRightWidth, false);
                mIndicatorView.setIndicatorWidth(mCurrentLefttWidth, true);
            }
            return true;
        });

        pref.setOnPreferenceChangeStopListener((p, v) -> {
            final float scale = mBackGestureInsetScales[(int) v];
            if (key == GESTURE_BACK_HEIGHT_KEY) {
                mIndicatorView.setIndicatorWidth(0, false);
                mIndicatorView.setIndicatorWidth(0, true);
                Settings.System.putInt(getContext().getContentResolver(), settingsKey, (int) scale);
            } else {
                mIndicatorView.setIndicatorWidth(0, key == LEFT_EDGE_SEEKBAR_KEY);
                Settings.Secure.putFloat(getContext().getContentResolver(), settingsKey, scale);
            }
            return true;
        });
    }

    private void updateHideImeSpaceStyle() {
        int style = Settings.System.getIntForUser(getContext().getContentResolver(),
                KEY_HIDE_IME_STYLE, 0, UserHandle.USER_CURRENT);
        mThemeUtils.setOverlayEnabled("android.theme.customization.hide_ime_space", "android", "android");
        if (style > 0 && style <= HIDE_IME_OVERLAYS.length) {
            mThemeUtils.setOverlayEnabled("android.theme.customization.hide_ime_space",
                    HIDE_IME_OVERLAYS[style - 1], "android");
        }
    }

    private void initGestureNavbarLengthPreference() {
        final ContentResolver resolver = getContext().getContentResolver();
        mGestureNavbarLengthPreference = getPreferenceScreen().findPreference(GESTURE_NAVBAR_LENGTH_KEY);
        mGestureNavbarLengthPreference.setContinuousUpdates(true);
        mGestureNavbarLengthPreference.setProgress(Settings.System.getIntForUser(
            resolver, Settings.System.GESTURE_NAVBAR_LENGTH_MODE,
            1, UserHandle.USER_CURRENT));
        mGestureNavbarLengthPreference.setOnPreferenceChangeListener((p, v) ->
            Settings.System.putIntForUser(resolver, Settings.System.GESTURE_NAVBAR_LENGTH_MODE,
                (Integer) v, UserHandle.USER_CURRENT));
    }

    private void initGestureNavbarHeightPreference() {
        final LabeledSeekBarPreference pref = getPreferenceScreen().
            findPreference(GESTURE_NAVBAR_HEIGHT_MODE_KEY);
        pref.setContinuousUpdates(true);
        pref.setProgress(Settings.System.getIntForUser(getContext().getContentResolver(),
            Settings.System.GESTURE_NAVBAR_HEIGHT_MODE, 3, UserHandle.USER_CURRENT));
        pref.setOnPreferenceChangeListener((p, v) ->
            Settings.System.putIntForUser(getContext().getContentResolver(),
                Settings.System.GESTURE_NAVBAR_HEIGHT_MODE, (Integer) v, UserHandle.USER_CURRENT));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHideImePref) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    KEY_HIDE_IME_STYLE, value, UserHandle.USER_CURRENT);
            updateHideImeSpaceStyle();
            return true;
        }
        // Handle other preferences
        return false;
    }

    private static float[] getFloatArray(TypedArray array) {
        int length = array.length();
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = array.getFloat(i, 1.0f);
        }
        array.recycle();
        return floatArray;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gesture_navigation_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SystemNavigationPreferenceController.isGestureAvailable(context);
                }
            };
}
