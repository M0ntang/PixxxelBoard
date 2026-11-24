package com.akshaykadam.pixelboard.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardCommaSpacingSettingsTest {

    @After
    public void tearDown() {
        GboardCommaSpacingSettings.setEnabledOverrideForTesting(null);
    }

    @Test
    public void defaultIsEnabledAndNullPreferencesAreSafe() {
        Assert.assertTrue(GboardCommaSpacingSettings.readEnabled(null));
        Assert.assertFalse(GboardCommaSpacingSettings.writeEnabled(null, false));
        GboardCommaSpacingSettings.ensureDefaults(null);

        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = GboardPatchesSettings.preferences(context);
        prefs.edit().clear().commit();

        Assert.assertTrue(GboardCommaSpacingSettings.readEnabled(prefs));
        Assert.assertTrue(GboardCommaSpacingSettings.isEnabled(context));
    }

    @Test
    public void readAndWriteRoundTripUpdatesPreference() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = GboardPatchesSettings.preferences(context);
        prefs.edit().clear().commit();

        GboardCommaSpacingSettings.ensureDefaults(prefs);
        Assert.assertTrue(prefs.contains(GboardCommaSpacingSettings.PREF_KEY_ENABLED));
        Assert.assertTrue(GboardCommaSpacingSettings.readEnabled(prefs));

        Assert.assertTrue(GboardCommaSpacingSettings.writeEnabled(prefs, false));
        Assert.assertFalse(GboardCommaSpacingSettings.readEnabled(prefs));
        Assert.assertFalse(GboardCommaSpacingSettings.isEnabled(context));

        Assert.assertTrue(GboardCommaSpacingSettings.writeEnabled(prefs, true));
        Assert.assertTrue(GboardCommaSpacingSettings.readEnabled(prefs));
        Assert.assertTrue(GboardCommaSpacingSettings.isEnabled(context));
    }
}
