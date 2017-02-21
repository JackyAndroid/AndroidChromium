// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.preferences.ButtonPreference;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.widget.Toast;

/**
 * Fragment to keep track of the translate preferences.
 */
public class TranslatePreferences extends PreferenceFragment {

    public static final String PREF_TRANSLATE_SWITCH = "translate_switch";
    public static final String PREF_RESET_TRANSLATE_BUTTON = "reset_translate_button";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.translate_preferences);
        getActivity().setTitle(R.string.google_translate);
        setHasOptionsMenu(true);

        final Context context = getActivity();
        if (context == null) return;

        ChromeSwitchPreference translateSwitch =
                (ChromeSwitchPreference) findPreference(PREF_TRANSLATE_SWITCH);

        boolean isTranslateEnabled = PrefServiceBridge.getInstance().isTranslateEnabled();
        translateSwitch.setChecked(isTranslateEnabled);

        translateSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setTranslateEnabled((boolean) newValue);
                return true;
            }
        });
        translateSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isTranslateManaged();
            }
        });

        ButtonPreference resetTranslateButton = (ButtonPreference)
                findPreference(PREF_RESET_TRANSLATE_BUTTON);

        resetTranslateButton.setOnPreferenceClickListener(new OnPreferenceClickListener(){
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PrefServiceBridge.getInstance().resetTranslateDefaults();
                Toast.makeText(getActivity(), getString(
                        R.string.translate_prefs_toast_description),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help = menu.add(
                Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
        help.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.menu_id_reset, Menu.NONE, R.string.reset_translate_defaults);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_id_targeted_help) {
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_translate),
                            Profile.getLastUsedProfile(), null);
            return true;
        } else if (itemId == R.id.menu_id_reset) {
            PrefServiceBridge.getInstance().resetTranslateDefaults();
            Toast.makeText(getActivity(), getString(
                    R.string.translate_prefs_toast_description),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }
}
