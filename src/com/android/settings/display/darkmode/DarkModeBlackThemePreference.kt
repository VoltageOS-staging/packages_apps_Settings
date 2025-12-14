/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settings.display.darkmode

import android.content.Context
import android.content.om.OverlayIdentifier
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.content.res.Configuration
import android.os.UserHandle
import android.util.Log
import com.android.settings.R
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.ReadWritePermit
import java.util.concurrent.Executor

private const val BLACK_THEME_KEY = "berry_black_theme"
private const val BLACK_THEME_TAG = "DarkModeBlackTheme"
private const val BLACK_THEME_OVERLAY_PACKAGE = "com.android.system.theme.black"
private const val BLACK_THEME_DISABLE_KEY = "android:neutral"

class DarkModeBlackThemePreference(
    private val context: Context,
    private val darkModeStorage: DarkModeStorage
) : PreferenceMetadata, BooleanValuePreference {

    private val storage = BlackThemeStorage(context)

    override val key: String
        get() = BLACK_THEME_KEY

    override val title: Int
        get() = R.string.berry_black_theme_title

    override val summary: Int
        get() = R.string.berry_black_theme_summary

    override fun storage(context: Context): KeyValueStore = storage

    override fun getReadPermissions(context: Context) = DarkModeStorage.getReadPermissions()

    override fun getWritePermissions(context: Context) = DarkModeStorage.getWritePermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun isEnabled(context: Context): Boolean {
        // Only enabled when dark mode is active
        return darkModeStorage.getBoolean(DarkModeMainSwitchPreference.KEY) ?: false
    }
}

class BlackThemeStorage(private val context: Context) : KeyValueStore {
    private val overlayManager: OverlayManager? =
        context.getSystemService(OverlayManager::class.java)

    override fun contains(key: String): Boolean = key == BLACK_THEME_KEY

    override fun <T : Any> getValue(key: String, valueType: Class<T>): T? {
        if (key != BLACK_THEME_KEY || valueType != Boolean::class.javaObjectType) return null
        if (overlayManager == null) return false as T
        val info = overlayManager.getOverlayInfo(BLACK_THEME_OVERLAY_PACKAGE, UserHandle.CURRENT)
        return (info?.isEnabled ?: false) as T
    }

    override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
        if (key != BLACK_THEME_KEY || 
            valueType != Boolean::class.javaObjectType || 
            value !is Boolean) {
            return
        }

        if (overlayManager == null) {
            Log.e(BLACK_THEME_TAG, "OverlayManager is null")
            return
        }

        try {
            val transaction = OverlayManagerTransaction.Builder()

            // Enable/disable black theme overlay
            transaction.setEnabled(
                getOverlayIdentifier(BLACK_THEME_OVERLAY_PACKAGE),
                value,
                UserHandle.USER_CURRENT
            )

            // Handle neutral theme (android:neutral)
            val isNightMode = (context.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            transaction.setEnabled(
                getOverlayIdentifier(BLACK_THEME_DISABLE_KEY),
                if (isNightMode) !value else true,
                UserHandle.USER_CURRENT
            )

            overlayManager.commit(transaction.build())
        } catch (e: Exception) {
            Log.e(BLACK_THEME_TAG, "Failed to toggle black theme overlay", e)
        }
    }

    private fun getOverlayIdentifier(packageName: String): OverlayIdentifier {
        return overlayManager?.getOverlayInfo(packageName, UserHandle.CURRENT)
            ?.overlayIdentifier
            ?: throw IllegalStateException("Overlay not found: $packageName")
    }

    override fun addObserver(observer: com.android.settingslib.datastore.KeyedObserver<String?>, executor: Executor) = false
    override fun addObserver(key: String, observer: com.android.settingslib.datastore.KeyedObserver<String>, executor: Executor) = false
    override fun notifyChange(reason: Int) {}
    override fun notifyChange(key: String, reason: Int) {}
    override fun removeObserver(observer: com.android.settingslib.datastore.KeyedObserver<String?>) = false
    override fun removeObserver(key: String, observer: com.android.settingslib.datastore.KeyedObserver<String>) = false
}
