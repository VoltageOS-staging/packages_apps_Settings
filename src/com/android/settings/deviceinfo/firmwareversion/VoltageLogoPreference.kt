/*
 * Copyright (C) 2024 VoltageOS
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding

class VoltageLogoPreference : PreferenceMetadata, PreferenceBinding {

    override val key: String
        get() = "voltage_logo"

    // We don't need a text title because it's an image
    override val title: Int
        get() = 0

    override fun isIndexable(context: Context) = false

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        // This tells the preference to use your custom layout file
        preference.layoutResource = R.layout.voltage_logo_layout
        
        // Make it so you can't click the logo (visual only)
        preference.isSelectable = false
    }
}
