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
public final class GboardQuickPasteSettingsTest {

    @After
    public void tearDown() {
        GboardQuickPasteSettings.setEnabledOverrideForTesting(null);
    }

    @Test
    public void defaultIsEnabledAndNullPreferencesAreSafe() {
        Assert.assertTrue(GboardQuickPasteSettings.readEnabled(null));
        Assert.assertFalse(GboardQuickPasteSettings.writeEnabled(null, false));
        GboardQuickPasteSettings.ensureDefaults(null);

        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = GboardPatchesSettings.preferences(context);
        prefs.edit().clear().commit();

        Assert.assertTrue(GboardQuickPasteSettings.readEnabled(prefs));
        Assert.assertTrue(GboardQuickPasteSettings.isEnabled(context));
    }

    @Test
    public void readAndWriteRoundTripUpdatesPreference() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = GboardPatchesSettings.preferences(context);
        prefs.edit().clear().commit();

        GboardQuickPasteSettings.ensureDefaults(prefs);
        Assert.assertTrue(prefs.contains(GboardQuickPasteSettings.PREF_KEY_ENABLED));
        Assert.assertTrue(GboardQuickPasteSettings.readEnabled(prefs));

        Assert.assertTrue(GboardQuickPasteSettings.writeEnabled(prefs, false));
        Assert.assertFalse(GboardQuickPasteSettings.readEnabled(prefs));
        Assert.assertFalse(GboardQuickPasteSettings.isEnabled(context));

        Assert.assertTrue(GboardQuickPasteSettings.writeEnabled(prefs, true));
        Assert.assertTrue(GboardQuickPasteSettings.readEnabled(prefs));
        Assert.assertTrue(GboardQuickPasteSettings.isEnabled(context));
    }
}
