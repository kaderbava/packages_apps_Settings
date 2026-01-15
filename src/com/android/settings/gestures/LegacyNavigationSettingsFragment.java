/*
 * Copyright (C) 2016-2025 crDroid Android Project
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
import android.content.Context;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.internal.logging.nano.MetricsProto;

import static com.android.systemui.shared.recents.utilities.Utilities.isLargeScreen;
/**
 * A fragment to include all the settings related to Legacy Navigation mode.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LegacyNavigationSettingsFragment extends DashboardFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "LegacyNavigationSettingsFragment";

    public static final String LEGACY_NAVIGATION_SETTINGS =
            "com.android.settings.LEGACY_NAVIGATION_SETTINGS";

    private static final String KEY_ENABLE_TASKBAR = "enable_taskbar";

    public LegacyNavigationSettingsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isLargeScreen(getContext())) {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(KEY_ENABLE_TASKBAR));
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.legacy_navigation_settings;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_default;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.INFINITY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.legacy_navigation_settings);
}
