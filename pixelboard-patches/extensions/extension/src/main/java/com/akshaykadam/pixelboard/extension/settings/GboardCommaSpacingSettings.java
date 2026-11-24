/*
 * PixelBoard - Gboard Enhancement Mod
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

/**
 * Setting for smart comma spacing: removes accidental spaces before commas and ensures a single
 * space after them when typing normal text.
 */
public final class GboardCommaSpacingSettings {
    public static final String PREF_KEY_ENABLED = "comma_spacing_autofix_enabled";
    public static final boolean DEFAULT_ENABLED = true;

    static volatile Boolean enabledOverrideForTesting;
    private static volatile Method currentApplication;

    private GboardCommaSpacingSettings() {
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        return preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit().putBoolean(PREF_KEY_ENABLED, enabled).commit();
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED).apply();
        }
    }

    public static boolean isEnabled(Context context) {
        Boolean override = enabledOverrideForTesting;
        if (override != null) {
            return override.booleanValue();
        }
        if (context == null) {
            return DEFAULT_ENABLED;
        }
        try {
            return readEnabled(GboardPatchesSettings.preferences(context));
        } catch (Throwable throwable) {
            return DEFAULT_ENABLED;
        }
    }

    /** Runtime check from the keyboard process; falls back to the default on any failure. */
    public static boolean isEnabled() {
        Boolean override = enabledOverrideForTesting;
        if (override != null) {
            return override.booleanValue();
        }
        try {
            Context context = applicationContext();
            if (context == null) {
                return DEFAULT_ENABLED;
            }
            return readEnabled(GboardPatchesSettings.preferences(context));
        } catch (Throwable throwable) {
            return DEFAULT_ENABLED;
        }
    }

    public static void setEnabledOverrideForTesting(Boolean enabled) {
        enabledOverrideForTesting = enabled;
    }

    private static Context applicationContext() {
        try {
            Method method = currentApplication;
            if (method == null) {
                method = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
                currentApplication = method;
            }
            Object application = method.invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
