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

import android.content.Context;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

public class PerAppRotationGlobalSwitchController extends TogglePreferenceController {

    private static final int SMALLEST_WIDTH_TABLET_DP = 600;

    public PerAppRotationGlobalSwitchController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        int smallestWidthDp = mContext.getResources().getConfiguration().smallestScreenWidthDp;
        return smallestWidthDp >= SMALLEST_WIDTH_TABLET_DP ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PER_APP_ROTATION_ENABLED, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PER_APP_ROTATION_ENABLED, isChecked ? 1 : 0);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
