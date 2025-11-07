/*
 * Copyright (C) 2025 The VoltageOS Project
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

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.internal.util.voltage.rotation.RotationController;

public class PerAppRotationSettings extends SettingsPreferenceFragment
        implements ApplicationsState.Callbacks {

    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private PreferenceScreen mPreferenceScreen;
    private RotationController mRotationController;
    private String[] mRotationEntries;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.per_app_rotation_settings);
        mPreferenceScreen = getPreferenceScreen();

        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mSession = mApplicationsState.newSession(this, getSettingsLifecycle());
        mRotationController = new RotationController(getContext());
        mRotationEntries = getResources().getStringArray(R.array.per_app_rotation_entries);
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VOLTAGE;
    }

    @Override
    public void onAllSizesComputed() {}

    @Override
    public void onLauncherInfoChanged() {}

    @Override
    public void onPackageIconChanged() {
        rebuild();
    }

    @Override
    public void onPackageListChanged() {
        rebuild();
    }

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries == null) {
            return;
        }
        final var context = getPrefContext();
        if (context == null) {
            return;
        }
        mPreferenceScreen.removeAll();
        final ArrayList<AppItem> appList = new ArrayList<>();
        PackageManager pm = getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(mainIntent, 0);

        for (ApplicationsState.AppEntry entry : entries) {
            boolean isLauncherApp = false;
            for (ResolveInfo info : launcherApps) {
                if (info.activityInfo.packageName.equals(entry.info.packageName)) {
                    isLauncherApp = true;
                    break;
                }
            }
            if (isLauncherApp) {
                appList.add(new AppItem(entry));
            }
        }

        Collections.sort(appList, (a, b) -> a.label.compareTo(b.label));

        for (AppItem item : appList) {
            Preference pref = new Preference(context);
            pref.setKey(item.packageName);
            pref.setTitle(item.label);
            mApplicationsState.ensureIcon(item.entry);
            pref.setIcon(item.entry.icon);

            int currentRotation = mRotationController.getRotationForApp(item.packageName);
            pref.setSummary(mRotationEntries[currentRotation]);

            pref.setOnPreferenceClickListener(p -> {
                showRotationDialog(item);
                return true;
            });
            mPreferenceScreen.addPreference(pref);
        }
    }

    @Override
    public void onRunningStateChanged(boolean running) {}

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    private void rebuild() {
        mSession.rebuild(ApplicationsState.FILTER_EVERYTHING, ApplicationsState.ALPHA_COMPARATOR);
    }

    private void showRotationDialog(final AppItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(item.label);

        int currentRotation = mRotationController.getRotationForApp(item.packageName);

        builder.setSingleChoiceItems(R.array.per_app_rotation_entries, currentRotation,
                (dialog, which) -> {
                    setRotation(item.packageName, which);
                    dialog.dismiss();
                });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void setRotation(String packageName, int rotation) {
        Settings.System.putString(getContext().getContentResolver(),
            Settings.System.PER_APP_ROTATION,
            buildSettingsString(packageName, rotation));
        Preference pref = findPreference(packageName);
        if (pref != null) {
            pref.setSummary(mRotationEntries[rotation]);
        }
    }

    private String buildSettingsString(String packageName, int rotation) {
        String currentSetting = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.PER_APP_ROTATION);
        
        Map<String, Integer> rotationMap = new HashMap<>();
        if (!TextUtils.isEmpty(currentSetting)) {
            String[] entries = currentSetting.split(",");
            for (String entry : entries) {
                String[] pair = entry.split("=");
                if (pair.length == 2) {
                    try {
                        rotationMap.put(pair[0], Integer.parseInt(pair[1]));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }

        if (rotation == RotationController.ROTATION_DEFAULT) {
            rotationMap.remove(packageName);
        } else {
            rotationMap.put(packageName, rotation);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : rotationMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private static class AppItem {
        final ApplicationsState.AppEntry entry;
        final String packageName;
        final String label;

        AppItem(ApplicationsState.AppEntry entry) {
            this.entry = entry;
            this.packageName = entry.info.packageName;
            this.label = entry.label;
        }
    }
}
