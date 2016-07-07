// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * The main Site Settings screen, which shows all the site settings categories: All sites, Location,
 * Microphone, etc. By clicking into one of these categories, the user can see or and modify
 * permissions that have been granted to websites, as well as enable or disable permissions
 * browser-wide.
 */
public class SiteSettingsPreferences extends PreferenceFragment
        implements OnPreferenceClickListener {
    // The keys for each category shown on the Site Settings page.
    static final String ALL_SITES_KEY = "all_sites";
    static final String CAMERA_KEY = "camera";
    static final String COOKIES_KEY = "cookies";
    static final String FULLSCREEN_KEY = "fullscreen";
    static final String LOCATION_KEY = "device_location";
    static final String MICROPHONE_KEY = "microphone";
    static final String JAVASCRIPT_KEY = "javascript";
    static final String BLOCK_POPUPS_KEY = "block_popups";
    static final String NOTIFICATIONS_KEY = "notifications";
    static final String POPUPS_KEY = "popups";
    static final String PROTECTED_CONTENT_KEY = "protected_content";
    static final String STORAGE_KEY = "use_storage";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.site_settings_preferences);
        getActivity().setTitle(R.string.prefs_site_settings);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            getPreferenceScreen().removePreference(findPreference(PROTECTED_CONTENT_KEY));
        }

        updatePreferenceStates();
    }

    private int keyToContentSettingsType(String key) {
        if (CAMERA_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA;
        } else if (COOKIES_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES;
        } else if (FULLSCREEN_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN;
        } else if (LOCATION_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION;
        } else if (MICROPHONE_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC;
        } else if (JAVASCRIPT_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT;
        } else if (NOTIFICATIONS_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS;
        } else if (POPUPS_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS;
        } else if (PROTECTED_CONTENT_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER;
        }
        return -1;
    }

    private void updatePreferenceStates() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        // Preferences that navigate to Website Settings.
        List<String> websitePrefs = new ArrayList<String>();
        websitePrefs.add(LOCATION_KEY);
        if (Build.VERSION.SDK_INT >= 19) {
            websitePrefs.add(PROTECTED_CONTENT_KEY);
        }
        websitePrefs.add(COOKIES_KEY);
        websitePrefs.add(CAMERA_KEY);
        websitePrefs.add(FULLSCREEN_KEY);
        websitePrefs.add(JAVASCRIPT_KEY);
        websitePrefs.add(MICROPHONE_KEY);
        websitePrefs.add(NOTIFICATIONS_KEY);
        websitePrefs.add(POPUPS_KEY);
        // Initialize the summary and icon for all preferences that have an
        // associated content settings entry.
        for (String prefName : websitePrefs) {
            Preference p = findPreference(prefName);
            boolean checked = false;
            if (LOCATION_KEY.equals(prefName)) {
                checked = LocationSettings.getInstance().areAllLocationSettingsEnabled();
            } else if (CAMERA_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isCameraEnabled();
            } else if (JAVASCRIPT_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().javaScriptEnabled();
            } else if (MICROPHONE_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isMicEnabled();
            } else if (PROTECTED_CONTENT_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isProtectedMediaIdentifierEnabled();
            } else if (COOKIES_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
            } else if (NOTIFICATIONS_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isPushNotificationsEnabled();
            } else if (POPUPS_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().popupsEnabled();
            } else if (FULLSCREEN_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isFullscreenAllowed();
            }
            int contentType = keyToContentSettingsType(prefName);
            p.setTitle(ContentSettingsResources.getTitle(contentType));
            if (COOKIES_KEY.equals(prefName) && checked
                    && prefServiceBridge.isBlockThirdPartyCookiesEnabled()) {
                p.setSummary(ContentSettingsResources.getCookieAllowedExceptThirdPartySummary());
            } else if (LOCATION_KEY.equals(prefName) && checked
                    && prefServiceBridge.isLocationAllowedByPolicy()) {
                p.setSummary(ContentSettingsResources.getGeolocationAllowedSummary());
            } else {
                p.setSummary(ContentSettingsResources.getCategorySummary(contentType, checked));
            }
            p.setIcon(ContentSettingsResources.getIcon(contentType));
            p.setOnPreferenceClickListener(this);
        }

        Preference p = findPreference(ALL_SITES_KEY);
        p.setOnPreferenceClickListener(this);
        // TODO(finnur): Re-move this for Storage once it can be moved to the 'Usage' menu.
        p = findPreference(STORAGE_KEY);
        p.setOnPreferenceClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceStates();
    }

    // OnPreferenceClickListener:

    @Override
    public boolean onPreferenceClick(Preference preference) {
        preference.getExtras().putString(
                SingleCategoryPreferences.EXTRA_CATEGORY, preference.getKey());
        preference.getExtras().putString(SingleCategoryPreferences.EXTRA_TITLE,
                preference.getTitle().toString());
        return false;
    }
}
