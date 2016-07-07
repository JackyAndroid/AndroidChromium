// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.ManagedPreferenceDelegate;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

/**
 * Fragment to manage the Usage and crash reports preference and to explain to
 * the user what it does.
 */
public class UsageAndCrashReportsPreferenceFragment extends PreferenceFragment {
    private static final String PREF_USAGE_AND_CRASH_REPORTS_SWITCH =
            "usage_and_crash_reports_switch";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.usage_and_crash_reports_preferences);
        getActivity().setTitle(R.string.usage_and_crash_reports_title);
        initUsageAndCrashReportsSwitch();
    }

    private void initUsageAndCrashReportsSwitch() {
        ChromeSwitchPreference usageAndCrashReportsSwitch =
                (ChromeSwitchPreference) findPreference(PREF_USAGE_AND_CRASH_REPORTS_SWITCH);
        boolean enabled = PrivacyPreferencesManager.getInstance(getActivity())
                                  .isUsageAndCrashReportingEnabled();
        usageAndCrashReportsSwitch.setChecked(enabled);

        usageAndCrashReportsSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabled = (boolean) newValue;
                PrivacyPreferencesManager privacyManager =
                        PrivacyPreferencesManager.getInstance(getActivity());

                // Update new two-choice android and chromium preferences.
                PrefServiceBridge.getInstance().setMetricsReportingEnabled(enabled);
                privacyManager.setUsageAndCrashReporting(enabled);

                // Update old three-choice android and chromium preference.
                PrefServiceBridge.getInstance().setCrashReporting(enabled);
                privacyManager.initCrashUploadPreference(enabled);
                return true;
            }
        });

        usageAndCrashReportsSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return PrefServiceBridge.getInstance().isCrashReportManaged();
            }
        });
    }
}
