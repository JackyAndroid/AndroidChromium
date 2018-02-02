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
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * The main Site Settings screen, which shows all the site settings categories: All sites, Location,
 * Microphone, etc. By clicking into one of these categories, the user can see or and modify
 * permissions that have been granted to websites, as well as enable or disable permissions
 * browser-wide.
 *
 * Depending on version and which experiment is running, this class also handles showing the Media
 * sub-menu, which contains Autoplay and Protected Content. To avoid the Media sub-menu having only
 * one sub-item, when either Autoplay or Protected Content should not be visible the other is shown
 * in the main setting instead (as opposed to under Media).
 */
public class SiteSettingsPreferences extends PreferenceFragment
        implements OnPreferenceClickListener {
    // The keys for each category shown on the Site Settings page.
    static final String ALL_SITES_KEY = "all_sites";
    static final String AUTOPLAY_KEY = "autoplay";
    static final String BACKGROUND_SYNC_KEY = "background_sync";
    static final String CAMERA_KEY = "camera";
    static final String COOKIES_KEY = "cookies";
    static final String JAVASCRIPT_KEY = "javascript";
    static final String LOCATION_KEY = "device_location";
    static final String MEDIA_KEY = "media";
    static final String MICROPHONE_KEY = "microphone";
    static final String NOTIFICATIONS_KEY = "notifications";
    static final String POPUPS_KEY = "popups";
    static final String PROTECTED_CONTENT_KEY = "protected_content";
    static final String STORAGE_KEY = "use_storage";
    static final String TRANSLATE_KEY = "translate";
    static final String USB_KEY = "usb";

    // Whether the Protected Content menu is available for display.
    boolean mProtectedContentMenuAvailable = false;

    // Whether this class is handling showing the Media sub-menu (and not the main menu).
    boolean mMediaSubMenu = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.site_settings_preferences);
        getActivity().setTitle(R.string.prefs_site_settings);

        mProtectedContentMenuAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        String category = "";
        if (getArguments() != null) {
            category = getArguments().getString(SingleCategoryPreferences.EXTRA_CATEGORY, "");
            if (MEDIA_KEY.equals(category)) {
                mMediaSubMenu = true;
                getActivity().setTitle(findPreference(MEDIA_KEY).getTitle().toString());
            }
        }

        configurePreferences();
        updatePreferenceStates();
    }

    private int keyToContentSettingsType(String key) {
        if (AUTOPLAY_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOPLAY;
        } else if (BACKGROUND_SYNC_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC;
        } else if (CAMERA_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA;
        } else if (COOKIES_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES;
        } else if (JAVASCRIPT_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT;
        } else if (LOCATION_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION;
        } else if (MICROPHONE_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC;
        } else if (NOTIFICATIONS_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS;
        } else if (POPUPS_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS;
        } else if (PROTECTED_CONTENT_KEY.equals(key)) {
            return ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER;
        }
        return -1;
    }

    private void configurePreferences() {
        if (mMediaSubMenu) {
            // The Media sub-menu only contains Protected Content and Autoplay, so remove all other
            // menus.
            getPreferenceScreen().removePreference(findPreference(ALL_SITES_KEY));
            getPreferenceScreen().removePreference(findPreference(BACKGROUND_SYNC_KEY));
            getPreferenceScreen().removePreference(findPreference(CAMERA_KEY));
            getPreferenceScreen().removePreference(findPreference(COOKIES_KEY));
            getPreferenceScreen().removePreference(findPreference(JAVASCRIPT_KEY));
            getPreferenceScreen().removePreference(findPreference(LOCATION_KEY));
            getPreferenceScreen().removePreference(findPreference(MEDIA_KEY));
            getPreferenceScreen().removePreference(findPreference(MICROPHONE_KEY));
            getPreferenceScreen().removePreference(findPreference(NOTIFICATIONS_KEY));
            getPreferenceScreen().removePreference(findPreference(POPUPS_KEY));
            getPreferenceScreen().removePreference(findPreference(STORAGE_KEY));
            getPreferenceScreen().removePreference(findPreference(TRANSLATE_KEY));
            getPreferenceScreen().removePreference(findPreference(USB_KEY));
        } else {
            // If both Autoplay and Protected Content menus are available, they'll be tucked under
            // the Media key. Otherwise, we can remove the Media menu entry.
            if (!mProtectedContentMenuAvailable) {
                getPreferenceScreen().removePreference(findPreference(MEDIA_KEY));
                getPreferenceScreen().removePreference(findPreference(PROTECTED_CONTENT_KEY));
            } else {
                // These two will be tucked under the Media subkey, so no reason to show them now.
                getPreferenceScreen().removePreference(findPreference(AUTOPLAY_KEY));
                getPreferenceScreen().removePreference(findPreference(PROTECTED_CONTENT_KEY));
            }
        }
    }

    private void updatePreferenceStates() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        // Translate preference.
        Preference translatePref = findPreference(TRANSLATE_KEY);
        if (translatePref != null) {
            setTranslateStateSummary(translatePref);
        }

        // Preferences that navigate to Website Settings.
        List<String> websitePrefs = new ArrayList<String>();
        if (mMediaSubMenu) {
            websitePrefs.add(PROTECTED_CONTENT_KEY);
            websitePrefs.add(AUTOPLAY_KEY);
        } else {
            // When showing the main menu, if Protected Content is not available, only Autoplay
            // will be visible.
            if (!mProtectedContentMenuAvailable) websitePrefs.add(AUTOPLAY_KEY);
            websitePrefs.add(BACKGROUND_SYNC_KEY);
            websitePrefs.add(CAMERA_KEY);
            websitePrefs.add(COOKIES_KEY);
            websitePrefs.add(JAVASCRIPT_KEY);
            websitePrefs.add(LOCATION_KEY);
            websitePrefs.add(MICROPHONE_KEY);
            websitePrefs.add(NOTIFICATIONS_KEY);
            websitePrefs.add(POPUPS_KEY);
        }

        // Initialize the summary and icon for all preferences that have an
        // associated content settings entry.
        for (String prefName : websitePrefs) {
            Preference p = findPreference(prefName);
            boolean checked = false;
            if (AUTOPLAY_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isAutoplayEnabled();
            } else if (BACKGROUND_SYNC_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isBackgroundSyncAllowed();
            } else if (CAMERA_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isCameraEnabled();
            } else if (COOKIES_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
            } else if (JAVASCRIPT_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().javaScriptEnabled();
            } else if (LOCATION_KEY.equals(prefName)) {
                checked = LocationSettings.getInstance().areAllLocationSettingsEnabled();
            } else if (MICROPHONE_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isMicEnabled();
            } else if (NOTIFICATIONS_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isNotificationsEnabled();
            } else if (POPUPS_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().popupsEnabled();
            } else if (PROTECTED_CONTENT_KEY.equals(prefName)) {
                checked = PrefServiceBridge.getInstance().isProtectedMediaIdentifierEnabled();
            }

            int contentType = keyToContentSettingsType(prefName);
            p.setTitle(ContentSettingsResources.getTitle(contentType));
            p.setOnPreferenceClickListener(this);

            // Disable autoplay preference if Data Saver is ON.
            if (AUTOPLAY_KEY.equals(prefName)
                    && DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) {
                p.setSummary(ContentSettingsResources.getAutoplayDisabledByDataSaverSummary());
                p.setEnabled(false);
            } else if (COOKIES_KEY.equals(prefName) && checked
                    && prefServiceBridge.isBlockThirdPartyCookiesEnabled()) {
                p.setSummary(ContentSettingsResources.getCookieAllowedExceptThirdPartySummary());
            } else if (LOCATION_KEY.equals(prefName) && checked
                    && prefServiceBridge.isLocationAllowedByPolicy()) {
                p.setSummary(ContentSettingsResources.getGeolocationAllowedSummary());
            } else {
                p.setSummary(ContentSettingsResources.getCategorySummary(contentType, checked));
            }

            if (p.isEnabled()) {
                p.setIcon(ContentSettingsResources.getIcon(contentType));
            } else {
                p.setIcon(ContentSettingsResources.getDisabledIcon(contentType, getResources()));
            }
        }

        Preference p = findPreference(ALL_SITES_KEY);
        if (p != null) p.setOnPreferenceClickListener(this);
        p = findPreference(MEDIA_KEY);
        if (p != null) p.setOnPreferenceClickListener(this);
        // TODO(finnur): Re-move this for Storage once it can be moved to the 'Usage' menu.
        p = findPreference(STORAGE_KEY);
        if (p != null) p.setOnPreferenceClickListener(this);
        p = findPreference(USB_KEY);
        if (p != null) p.setOnPreferenceClickListener(this);
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

    private void setTranslateStateSummary(Preference translatePref) {
        boolean translateEnabled = PrefServiceBridge.getInstance().isTranslateEnabled();
        translatePref.setSummary(translateEnabled
                ? R.string.website_settings_category_ask
                : R.string.website_settings_category_blocked);
    }
}
