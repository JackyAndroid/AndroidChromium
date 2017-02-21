// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.physicalweb.ListUrlsActivity;
import org.chromium.chrome.browser.physicalweb.PhysicalWeb;
import org.chromium.chrome.browser.physicalweb.PhysicalWebUma;
import org.chromium.chrome.browser.preferences.ButtonPreference;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;

/**
 * Fragment to manage the Physical Web preference and to explain to the user what it does.
 */
public class PhysicalWebPreferenceFragment extends PreferenceFragment {
    private static final String TAG = "PhysicalWeb";
    private static final String PREF_PHYSICAL_WEB_SWITCH = "physical_web_switch";
    private static final String PREF_PHYSICAL_WEB_LAUNCH = "physical_web_launch";
    private static final int REQUEST_ID = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.physical_web_preferences);
        getActivity().setTitle(R.string.physical_web_pref_title);
        initPhysicalWebSwitch();
        initLaunchButton();
    }

    private void ensureLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission already granted");
            } else {
                Log.d(TAG, "Requesting location permission");
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ID);
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    PhysicalWebUma.onPrefsLocationGranted(getActivity());
                    Log.d(TAG, "Location permission granted");
                    PhysicalWeb.startPhysicalWeb();
                } else {
                    PhysicalWebUma.onPrefsLocationDenied(getActivity());
                    Log.d(TAG, "Location permission denied");
                }
                break;
            default:
        }
    }

    private void initPhysicalWebSwitch() {
        ChromeSwitchPreference physicalWebSwitch =
                (ChromeSwitchPreference) findPreference(PREF_PHYSICAL_WEB_SWITCH);

        physicalWebSwitch.setChecked(
                PrivacyPreferencesManager.getInstance().isPhysicalWebEnabled());

        physicalWebSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabled = (boolean) newValue;
                if (enabled) {
                    PhysicalWebUma.onPrefsFeatureEnabled(getActivity());
                    ensureLocationPermission();
                } else {
                    PhysicalWebUma.onPrefsFeatureDisabled(getActivity());
                }
                PrivacyPreferencesManager.getInstance().setPhysicalWebEnabled(enabled);
                return true;
            }
        });
    }

    private void initLaunchButton() {
        ButtonPreference physicalWebLaunch =
                (ButtonPreference) findPreference(PREF_PHYSICAL_WEB_LAUNCH);

        physicalWebLaunch.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(createListUrlsIntent(getActivity()));
                return true;
            }
        });
    }

    private static Intent createListUrlsIntent(Context context) {
        Intent intent = new Intent(context, ListUrlsActivity.class);
        intent.putExtra(ListUrlsActivity.REFERER_KEY, ListUrlsActivity.PREFERENCE_REFERER);
        return intent;
    }
}
