// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.preferences.ChromeBaseCheckBoxPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.widget.Toast;

/**
 * Fragment to keep track of the translate preferences.
 */
public class LanguagePreferences extends PreferenceFragment {

    private static final String PREF_TRANSLATE_CHECKBOX = "translate_checkbox";
    public static final String PREF_AUTO_DETECT_CHECKBOX = "auto_detect_encoding_checkbox";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.language_preferences);
        getActivity().setTitle(R.string.language);
        setHasOptionsMenu(true);

        final Context context = getActivity();
        if (context == null) return;

        ChromeBaseCheckBoxPreference translateCheckBox =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_TRANSLATE_CHECKBOX);

        boolean isTranslateEnabled = PrefServiceBridge.getInstance().isTranslateEnabled();
        translateCheckBox.setChecked(isTranslateEnabled);

        translateCheckBox.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setTranslateEnabled((boolean) newValue);
                return true;
            }
        });
        translateCheckBox.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isTranslateManaged();
            }
        });

        ChromeBaseCheckBoxPreference autoDetectCheckBox =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_AUTO_DETECT_CHECKBOX);

        boolean isAutoDetectEncodingEnabled =
                PrefServiceBridge.getInstance().isAutoDetectEncodingEnabled();
        autoDetectCheckBox.setChecked(isAutoDetectEncodingEnabled);

        autoDetectCheckBox.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setAutoDetectEncodingEnabled((boolean) newValue);
                return true;
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help = menu.add(
                Menu.NONE, R.id.menu_id_translate_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
        help.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem reset = menu.add(Menu.NONE, Menu.NONE, Menu.NONE,
                R.string.reset_translate_defaults);
        reset.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                PrefServiceBridge.getInstance().resetTranslateDefaults();
                Toast.makeText(getActivity(), getString(
                        R.string.translate_prefs_toast_description),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_id_translate_help) {
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_translate),
                            Profile.getLastUsedProfile(), null);
            return true;
        }
        return false;
    }
}
