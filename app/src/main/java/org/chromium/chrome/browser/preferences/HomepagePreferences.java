// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;

/**
 * Fragment that allows the user to configure homepage related preferences.
 */
public class HomepagePreferences extends PreferenceFragment {
    private static final String PREF_HOMEPAGE_SWITCH = "homepage_switch";
    private static final String PREF_HOMEPAGE_EDIT = "homepage_edit";

    private HomepageManager mHomepageManager;
    private ChromeSwitchPreference mHomepageSwitch;
    private Preference mHomepageEdit;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mHomepageManager = HomepageManager.getInstance(getActivity());
        getActivity().setTitle(R.string.options_homepage_title);
        addPreferencesFromResource(R.xml.homepage_preferences);

        mHomepageSwitch = (ChromeSwitchPreference) findPreference(PREF_HOMEPAGE_SWITCH);
        boolean isHomepageEnabled = mHomepageManager.getPrefHomepageEnabled();
        mHomepageSwitch.setChecked(isHomepageEnabled);
        mHomepageSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mHomepageManager.setPrefHomepageEnabled((boolean) newValue);
                return true;
            }
        });

        mHomepageEdit = findPreference(PREF_HOMEPAGE_EDIT);
        updateCurrentHomepageUrl();
    }
    private void updateCurrentHomepageUrl() {
        mHomepageEdit.setSummary((mHomepageManager.getPrefHomepageUseDefaultUri()
                ? PartnerBrowserCustomizations.getHomePageUrl()
                : mHomepageManager.getPrefHomepageCustomUri()));
    }
    @Override
    public void onResume() {
        super.onResume();
        updateCurrentHomepageUrl();
    }

}
