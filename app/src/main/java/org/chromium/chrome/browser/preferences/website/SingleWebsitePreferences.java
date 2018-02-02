// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;
import android.text.format.Formatter;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.WebContents;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Shows the permissions and other settings for a particular website.
 */
public class SingleWebsitePreferences extends PreferenceFragment
        implements DialogInterface.OnClickListener, OnPreferenceChangeListener,
                OnPreferenceClickListener {
    // SingleWebsitePreferences expects either EXTRA_SITE (a Website) or
    // EXTRA_ORIGIN (a WebsiteAddress) to be present (but not both). If
    // EXTRA_SITE is present, the fragment will display the permissions in that
    // Website object. If EXTRA_ORIGIN is present, the fragment will find all
    // permissions for that website address and display those. If EXTRA_LOCATION
    // is present, the fragment will add a Location toggle, even if the site
    // specifies no Location permission.
    public static final String EXTRA_SITE = "org.chromium.chrome.preferences.site";
    public static final String EXTRA_ORIGIN = "org.chromium.chrome.preferences.origin";
    public static final String EXTRA_LOCATION = "org.chromium.chrome.preferences.location";

    public static final String EXTRA_WEB_CONTENTS = "org.chromium.chrome.preferences.web_contents";
    public static final String EXTRA_USB_INFO = "org.chromium.chrome.preferences.usb_info";

    // Preference keys, see single_website_preferences.xml
    // Headings:
    public static final String PREF_SITE_TITLE = "site_title";
    public static final String PREF_USAGE = "site_usage";
    public static final String PREF_PERMISSIONS = "site_permissions";
    public static final String PREF_OS_PERMISSIONS_WARNING = "os_permissions_warning";
    public static final String PREF_OS_PERMISSIONS_WARNING_EXTRA = "os_permissions_warning_extra";
    public static final String PREF_OS_PERMISSIONS_WARNING_DIVIDER =
            "os_permissions_warning_divider";
    // Actions at the top (if adding new, see hasUsagePreferences below):
    public static final String PREF_CLEAR_DATA = "clear_data";
    // Buttons:
    public static final String PREF_RESET_SITE = "reset_site_button";
    // Website permissions (if adding new, see hasPermissionsPreferences and resetSite below):
    public static final String PREF_AUTOPLAY_PERMISSION = "autoplay_permission_list";
    public static final String PREF_BACKGROUND_SYNC_PERMISSION = "background_sync_permission_list";
    public static final String PREF_CAMERA_CAPTURE_PERMISSION = "camera_permission_list";
    public static final String PREF_COOKIES_PERMISSION = "cookies_permission_list";
    public static final String PREF_JAVASCRIPT_PERMISSION = "javascript_permission_list";
    public static final String PREF_KEYGEN_PERMISSION = "keygen_permission_list";
    public static final String PREF_LOCATION_ACCESS = "location_access_list";
    public static final String PREF_MIC_CAPTURE_PERMISSION = "microphone_permission_list";
    public static final String PREF_MIDI_SYSEX_PERMISSION = "midi_sysex_permission_list";
    public static final String PREF_NOTIFICATIONS_PERMISSION = "push_notifications_list";
    public static final String PREF_POPUP_PERMISSION = "popup_permission_list";
    public static final String PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION =
            "protected_media_identifier_permission_list";

    // All permissions from the permissions preference category must be listed here.
    // TODO(mvanouwerkerk): Use this array in more places to reduce verbosity.
    private static final String[] PERMISSION_PREFERENCE_KEYS = {
            PREF_AUTOPLAY_PERMISSION,
            PREF_BACKGROUND_SYNC_PERMISSION,
            PREF_CAMERA_CAPTURE_PERMISSION,
            PREF_COOKIES_PERMISSION,
            PREF_JAVASCRIPT_PERMISSION,
            PREF_KEYGEN_PERMISSION,
            PREF_LOCATION_ACCESS,
            PREF_MIC_CAPTURE_PERMISSION,
            PREF_MIDI_SYSEX_PERMISSION,
            PREF_NOTIFICATIONS_PERMISSION,
            PREF_POPUP_PERMISSION,
            PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION,
    };

    // The website this page is displaying details about.
    private Website mSite;

    // The address of the site we want to display. Used only if EXTRA_ADDRESS is provided.
    private WebsiteAddress mSiteAddress;

    // The number of USB device permissions displayed.
    private int mUsbPermissionCount;

    private class SingleWebsitePermissionsPopulator
            implements WebsitePermissionsFetcher.WebsitePermissionsCallback {
        private final WebContents mWebContents;

        public SingleWebsitePermissionsPopulator(WebContents webContents) {
            mWebContents = webContents;
        }

        @Override
        public void onWebsitePermissionsAvailable(Collection<Website> sites) {
            // This method may be called after the activity has been destroyed.
            // In that case, bail out.
            if (getActivity() == null) return;

            // TODO(mvanouwerkerk): Avoid modifying the outer class from this inner class.
            mSite = mergePermissionInfoForTopLevelOrigin(mSiteAddress, sites);

            // Display Keygen Content Setting if Keygen is blocked.
            if (mSite.getKeygenInfo() == null && mWebContents != null
                    && WebsitePreferenceBridge.getKeygenBlocked(mWebContents)) {
                String origin = mSiteAddress.getOrigin();
                mSite.setKeygenInfo(new KeygenInfo(origin, origin, false));
            }

            displaySitePermissions();
        }
    }

    /**
     * Creates a Bundle with the correct arguments for opening this fragment for
     * the website with the given url.
     *
     * @param url The URL to open the fragment with. This is a complete url including scheme,
     *            domain, port,  path, etc.
     * @return The bundle to attach to the preferences intent.
     */
    public static Bundle createFragmentArgsForSite(String url) {
        Bundle fragmentArgs = new Bundle();
        // TODO(mvanouwerkerk): Define a pure getOrigin method in UrlUtilities that is the
        // equivalent of the call below, because this is perfectly fine for non-display purposes.
        String origin =
                UrlFormatter.formatUrlForSecurityDisplay(URI.create(url), true /* showScheme */);
        fragmentArgs.putString(SingleWebsitePreferences.EXTRA_ORIGIN, origin);
        return fragmentArgs;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getActivity().setTitle(R.string.prefs_site_settings);
        ListView listView = (ListView) getView().findViewById(android.R.id.list);
        listView.setDivider(null);

        Object extraSite = getArguments().getSerializable(EXTRA_SITE);
        Object extraOrigin = getArguments().getSerializable(EXTRA_ORIGIN);
        getArguments().setClassLoader(WebContents.class.getClassLoader());
        Object webContents = getArguments().get(EXTRA_WEB_CONTENTS);

        if (extraSite != null && extraOrigin == null) {
            mSite = (Website) extraSite;
            displaySitePermissions();
        } else if (extraOrigin != null && extraSite == null) {
            mSiteAddress = WebsiteAddress.create((String) extraOrigin);
            WebsitePermissionsFetcher fetcher;
            fetcher = new WebsitePermissionsFetcher(
                new SingleWebsitePermissionsPopulator((WebContents) webContents));
            fetcher.fetchAllPreferences();
        } else {
            assert false : "Exactly one of EXTRA_SITE or EXTRA_SITE_ADDRESS must be provided.";
        }

        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Given an address and a list of sets of websites, returns a new site with the same origin
     * as |address| which has merged into it the permissions of the matching input sites. If a
     * permission is found more than once, the one found first is used and the latter are ignored.
     * This should not drop any relevant data as there should not be duplicates like that in the
     * first place.
     *
     * @param address The address to search for.
     * @param websites The websites to search in.
     * @return The merged website.
     */
    private static Website mergePermissionInfoForTopLevelOrigin(
            WebsiteAddress address, Collection<Website> websites) {
        String origin = address.getOrigin();
        String host = Uri.parse(origin).getHost();
        Website merged = new Website(address, null);
        // This loop looks expensive, but the amount of data is likely to be relatively small
        // because most sites have very few permissions.
        for (Website other : websites) {
            if (merged.getGeolocationInfo() == null && other.getGeolocationInfo() != null
                    && permissionInfoIsForTopLevelOrigin(other.getGeolocationInfo(), origin)) {
                merged.setGeolocationInfo(other.getGeolocationInfo());
            }
            if (merged.getKeygenInfo() == null && other.getKeygenInfo() != null
                    && permissionInfoIsForTopLevelOrigin(other.getKeygenInfo(), origin)) {
                merged.setKeygenInfo(other.getKeygenInfo());
            }
            if (merged.getMidiInfo() == null && other.getMidiInfo() != null
                    && permissionInfoIsForTopLevelOrigin(other.getMidiInfo(), origin)) {
                merged.setMidiInfo(other.getMidiInfo());
            }
            if (merged.getProtectedMediaIdentifierInfo() == null
                    && other.getProtectedMediaIdentifierInfo() != null
                    && permissionInfoIsForTopLevelOrigin(
                               other.getProtectedMediaIdentifierInfo(), origin)) {
                merged.setProtectedMediaIdentifierInfo(other.getProtectedMediaIdentifierInfo());
            }
            if (merged.getNotificationInfo() == null
                    && other.getNotificationInfo() != null
                    && permissionInfoIsForTopLevelOrigin(
                               other.getNotificationInfo(), origin)) {
                merged.setNotificationInfo(other.getNotificationInfo());
            }
            if (merged.getCameraInfo() == null && other.getCameraInfo() != null
                    && permissionInfoIsForTopLevelOrigin(
                               other.getCameraInfo(), origin)) {
                merged.setCameraInfo(other.getCameraInfo());
            }
            if (merged.getMicrophoneInfo() == null && other.getMicrophoneInfo() != null
                    && permissionInfoIsForTopLevelOrigin(other.getMicrophoneInfo(), origin)) {
                merged.setMicrophoneInfo(other.getMicrophoneInfo());
            }
            if (merged.getLocalStorageInfo() == null
                    && other.getLocalStorageInfo() != null
                    && origin.equals(other.getLocalStorageInfo().getOrigin())) {
                merged.setLocalStorageInfo(other.getLocalStorageInfo());
            }
            for (StorageInfo storageInfo : other.getStorageInfo()) {
                if (host.equals(storageInfo.getHost())) {
                    merged.addStorageInfo(storageInfo);
                }
            }
            for (UsbInfo usbInfo : other.getUsbInfo()) {
                if (origin.equals(usbInfo.getOrigin())
                        && (usbInfo.getEmbedder() == null || usbInfo.getEmbedder().equals("*"))) {
                    merged.addUsbInfo(usbInfo);
                }
            }

            // TODO(mvanouwerkerk): Make the various info types share a common interface that
            // supports reading the origin or host.
            // TODO(mvanouwerkerk): Merge in PopupExceptionInfo? It uses a pattern, and is never
            // set on Android.
            // TODO(mvanouwerkerk): Merge in JavaScriptExceptionInfo? It uses a pattern.
            // TODO(lshang): Merge in CookieException? It will use patterns.
        }
        return merged;
    }

    private static boolean permissionInfoIsForTopLevelOrigin(PermissionInfo info, String origin) {
        // TODO(mvanouwerkerk): Find a more generic place for this method.
        return origin.equals(info.getOrigin())
                && (origin.equals(info.getEmbedderSafe()) || "*".equals(info.getEmbedderSafe()));
    }

    /**
     * Updates the permissions displayed in the UI by fetching them from mSite.
     * Must only be called once mSite is set.
     */
    private void displaySitePermissions() {
        addPreferencesFromResource(R.xml.single_website_preferences);

        Set<String> permissionPreferenceKeys =
                new HashSet<>(Arrays.asList(PERMISSION_PREFERENCE_KEYS));
        int maxPermissionOrder = 0;
        ListAdapter preferences = getPreferenceScreen().getRootAdapter();
        for (int i = 0; i < preferences.getCount(); ++i) {
            Preference preference = (Preference) preferences.getItem(i);
            if (PREF_SITE_TITLE.equals(preference.getKey())) {
                preference.setTitle(mSite.getTitle());
            } else if (PREF_CLEAR_DATA.equals(preference.getKey())) {
                long usage = mSite.getTotalUsage();
                if (usage > 0) {
                    Context context = preference.getContext();
                    preference.setTitle(String.format(
                            context.getString(R.string.origin_settings_storage_usage_brief),
                            Formatter.formatShortFileSize(context, usage)));
                    ((ClearWebsiteStorage) preference).setConfirmationListener(this);
                } else {
                    getPreferenceScreen().removePreference(preference);
                }
            } else if (PREF_RESET_SITE.equals(preference.getKey())) {
                preference.setOnPreferenceClickListener(this);
            } else if (PREF_AUTOPLAY_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getAutoplayPermission());
            } else if (PREF_BACKGROUND_SYNC_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getBackgroundSyncPermission());
            } else if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getCameraPermission());
            } else if (PREF_COOKIES_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getCookiePermission());
            } else if (PREF_JAVASCRIPT_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getJavaScriptPermission());
            } else if (PREF_KEYGEN_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getKeygenPermission());
            } else if (PREF_LOCATION_ACCESS.equals(preference.getKey())) {
                setUpLocationPreference(preference);
            } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getMicrophonePermission());
            } else if (PREF_MIDI_SYSEX_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getMidiPermission());
            } else if (PREF_NOTIFICATIONS_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getNotificationPermission());
            } else if (PREF_POPUP_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getPopupPermission());
            } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preference.getKey())) {
                setUpListPreference(preference, mSite.getProtectedMediaIdentifierPermission());
            }

            if (permissionPreferenceKeys.contains(preference.getKey())) {
                maxPermissionOrder = Math.max(maxPermissionOrder, preference.getOrder());
            }
        }

        for (UsbInfo info : mSite.getUsbInfo()) {
            Preference preference = new Preference(getActivity());
            preference.getExtras().putSerializable(EXTRA_USB_INFO, info);
            preference.setIcon(R.drawable.settings_usb);
            preference.setOnPreferenceClickListener(this);
            preference.setOrder(maxPermissionOrder);
            preference.setTitle(info.getName());
            preference.setWidgetLayoutResource(R.layout.usb_permission);
            getPreferenceScreen().addPreference(preference);
            mUsbPermissionCount++;
        }

        // Remove the 'permission is off in Android' message if not needed.
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        SiteSettingsCategory categoryWithWarning = getWarningCategory();
        if (categoryWithWarning == null) {
            getPreferenceScreen().removePreference(
                    preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING));
            getPreferenceScreen().removePreference(
                    preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING_EXTRA));
            getPreferenceScreen().removePreference(
                    preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING_DIVIDER));
        } else {
            Preference osWarning = preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING);
            Preference osWarningExtra =
                    preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING_EXTRA);
            categoryWithWarning.configurePermissionIsOffPreferences(
                    osWarning, osWarningExtra, getActivity(), false);
            if (osWarning.getTitle() == null) {
                getPreferenceScreen().removePreference(
                        preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING));
            } else if (osWarningExtra.getTitle() == null) {
                getPreferenceScreen().removePreference(
                        preferenceScreen.findPreference(PREF_OS_PERMISSIONS_WARNING_EXTRA));
            }
        }

        // Remove categories if no sub-items.
        if (!hasUsagePreferences()) {
            Preference heading = preferenceScreen.findPreference(PREF_USAGE);
            preferenceScreen.removePreference(heading);
        }
        if (!hasPermissionsPreferences()) {
            Preference heading = preferenceScreen.findPreference(PREF_PERMISSIONS);
            preferenceScreen.removePreference(heading);
        }
    }

    private SiteSettingsCategory getWarningCategory() {
        // If more than one per-app permission is disabled in Android, we can pick any category to
        // show the warning, because they will all show the same warning and all take the user to
        // the user to the same location. It is preferrable, however, that we give Geolocation some
        // priority because that category is the only one that potentially shows an additional
        // warning (when Location is turned off globally).
        if (showWarningFor(ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION)) {
            return SiteSettingsCategory.fromContentSettingsType(
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION);
        }
        if (showWarningFor(ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA)) {
            return SiteSettingsCategory.fromContentSettingsType(
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA);
        }
        if (showWarningFor(ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC)) {
            return SiteSettingsCategory.fromContentSettingsType(
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC);
        }
        return null;
    }

    private boolean showWarningFor(int type) {
        ContentSetting setting = null;
        if (type == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION) {
            setting = mSite.getGeolocationPermission();
        } else if (type == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA) {
            setting = mSite.getCameraPermission();
        } else if (type == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC) {
            setting = mSite.getMicrophonePermission();
        }

        if (setting == null) {
            return false;
        }

        SiteSettingsCategory category = SiteSettingsCategory.fromContentSettingsType(type);
        return category.showPermissionBlockedMessage(getActivity());
    }

    private boolean hasUsagePreferences() {
        // New actions under the Usage preference category must be listed here so that the category
        // heading can be removed when no actions are shown.
        return getPreferenceScreen().findPreference(PREF_CLEAR_DATA) != null;
    }

    private boolean hasPermissionsPreferences() {
        if (mUsbPermissionCount > 0) return true;
        PreferenceScreen screen = getPreferenceScreen();
        for (String key : PERMISSION_PREFERENCE_KEYS) {
            if (screen.findPreference(key) != null) return true;
        }
        return false;
    }

    /**
     * Initialize a ListPreference with a certain value.
     * @param preference The ListPreference to initialize.
     * @param value The value to initialize it to.
     */
    private void setUpListPreference(Preference preference, ContentSetting value) {
        if (value == null) {
            getPreferenceScreen().removePreference(preference);
            return;
        }

        ListPreference listPreference = (ListPreference) preference;

        int contentType = getContentSettingsTypeFromPreferenceKey(preference.getKey());
        CharSequence[] keys = new String[2];
        CharSequence[] descriptions = new String[2];
        keys[0] = ContentSetting.ALLOW.toString();
        keys[1] = ContentSetting.BLOCK.toString();
        descriptions[0] = getResources().getString(
                ContentSettingsResources.getSiteSummary(ContentSetting.ALLOW));
        descriptions[1] = getResources().getString(
                ContentSettingsResources.getSiteSummary(ContentSetting.BLOCK));
        listPreference.setEntryValues(keys);
        listPreference.setEntries(descriptions);
        int index = (value == ContentSetting.ALLOW ? 0 : 1);
        listPreference.setValueIndex(index);
        int explanationResourceId = ContentSettingsResources.getExplanation(contentType);
        if (explanationResourceId != 0) {
            listPreference.setTitle(explanationResourceId);
        }

        if (listPreference.isEnabled()) {
            SiteSettingsCategory category =
                    SiteSettingsCategory.fromContentSettingsType(contentType);
            if (category != null && !category.enabledInAndroid(getActivity())) {
                listPreference.setIcon(category.getDisabledInAndroidIcon(getActivity()));
                listPreference.setEnabled(false);
            } else {
                listPreference.setIcon(ContentSettingsResources.getIcon(contentType));
            }
        } else {
            listPreference.setIcon(
                    ContentSettingsResources.getDisabledIcon(contentType, getResources()));
        }

        preference.setSummary("%s");
        listPreference.setOnPreferenceChangeListener(this);
    }

    private void setUpLocationPreference(Preference preference) {
        ContentSetting permission = mSite.getGeolocationPermission();
        Context context = preference.getContext();
        Object locationAllowed = getArguments().getSerializable(EXTRA_LOCATION);
        if (permission == null && hasXGeoLocationPermission(context)) {
            String origin = mSite.getAddress().getOrigin();
            mSite.setGeolocationInfo(new GeolocationInfo(origin, origin, false));
            setUpListPreference(preference, ContentSetting.ALLOW);
            updateLocationPreferenceForXGeo(preference);
        } else if (permission == null && locationAllowed != null) {
            String origin = mSite.getAddress().getOrigin();
            mSite.setGeolocationInfo(new GeolocationInfo(origin, origin, false));
            setUpListPreference(preference, (boolean) locationAllowed
                    ? ContentSetting.ALLOW : ContentSetting.BLOCK);
        } else {
            setUpListPreference(preference, permission);
        }
    }

    /**
     * Returns true if the current host matches the default search engine host and location for the
     * default search engine is being granted via x-geo.
     * @param context The current context.
     */
    private boolean hasXGeoLocationPermission(Context context) {
        String searchUrl = TemplateUrlService.getInstance().getUrlForSearchQuery("foo");
        return mSite.getAddress().matches(searchUrl)
                && GeolocationHeader.isGeoHeaderEnabledForUrl(context, searchUrl, false);
    }

    /**
     * Updates the location preference to indicate that the site has access to location (via X-Geo)
     * for searches that happen from the omnibox.
     * @param preference The Location preference to modify.
     */
    private void updateLocationPreferenceForXGeo(Preference preference) {
        ListPreference listPreference = (ListPreference) preference;
        Resources res = getResources();
        listPreference.setEntries(new String[] {
                res.getString(R.string.website_settings_permissions_allow_dse),
                res.getString(ContentSettingsResources.getSiteSummary(ContentSetting.BLOCK)),
        });
        listPreference.setEntryValues(new String[] {
                ContentSetting.DEFAULT.toString(),
                ContentSetting.BLOCK.toString(),
        });
        listPreference.setValueIndex(0);
    }

    private int getContentSettingsTypeFromPreferenceKey(String preferenceKey) {
        switch (preferenceKey) {
            case PREF_AUTOPLAY_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOPLAY;
            case PREF_BACKGROUND_SYNC_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC;
            case PREF_CAMERA_CAPTURE_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA;
            case PREF_COOKIES_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES;
            case PREF_JAVASCRIPT_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT;
            case PREF_KEYGEN_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_KEYGEN;
            case PREF_LOCATION_ACCESS:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION;
            case PREF_MIC_CAPTURE_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC;
            case PREF_MIDI_SYSEX_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_MIDI_SYSEX;
            case PREF_NOTIFICATIONS_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS;
            case PREF_POPUP_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS;
            case PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER;
            default:
                return 0;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        clearStoredData();
    }

    private void clearStoredData() {
        mSite.clearAllStoredData(
                new Website.StoredDataClearedCallback() {
                    @Override
                    public void onStoredDataCleared() {
                        PreferenceScreen preferenceScreen = getPreferenceScreen();
                        preferenceScreen.removePreference(
                                preferenceScreen.findPreference(PREF_CLEAR_DATA));
                        if (!hasUsagePreferences()) {
                            preferenceScreen.removePreference(
                                    preferenceScreen.findPreference(PREF_USAGE));
                        }
                        popBackIfNoSettings();
                    }
                });
    }

    private void popBackIfNoSettings() {
        if (!hasPermissionsPreferences() && !hasUsagePreferences()) {
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentSetting permission = ContentSetting.fromString((String) newValue);
        if (PREF_AUTOPLAY_PERMISSION.equals(preference.getKey())) {
            mSite.setAutoplayPermission(permission);
        } else if (PREF_BACKGROUND_SYNC_PERMISSION.equals(preference.getKey())) {
            mSite.setBackgroundSyncPermission(permission);
        } else if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preference.getKey())) {
            mSite.setCameraPermission(permission);
        } else if (PREF_COOKIES_PERMISSION.equals(preference.getKey())) {
            mSite.setCookiePermission(permission);
        } else if (PREF_JAVASCRIPT_PERMISSION.equals(preference.getKey())) {
            mSite.setJavaScriptPermission(permission);
        } else if (PREF_KEYGEN_PERMISSION.equals(preference.getKey())) {
            mSite.setKeygenPermission(permission);
        } else if (PREF_LOCATION_ACCESS.equals(preference.getKey())) {
            mSite.setGeolocationPermission(permission);
        } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preference.getKey())) {
            mSite.setMicrophonePermission(permission);
        } else if (PREF_MIDI_SYSEX_PERMISSION.equals(preference.getKey())) {
            mSite.setMidiPermission(permission);
        } else if (PREF_NOTIFICATIONS_PERMISSION.equals(preference.getKey())) {
            mSite.setNotificationPermission(permission);
        } else if (PREF_POPUP_PERMISSION.equals(preference.getKey())) {
            mSite.setPopupPermission(permission);
        } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preference.getKey())) {
            mSite.setProtectedMediaIdentifierPermission(permission);
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Bundle extras = preference.peekExtras();
        if (extras != null) {
            UsbInfo usbInfo = (UsbInfo) extras.getSerializable(EXTRA_USB_INFO);
            if (usbInfo != null) {
                usbInfo.revoke();

                PreferenceScreen preferenceScreen = getPreferenceScreen();
                preferenceScreen.removePreference(preference);
                mUsbPermissionCount--;
                if (!hasPermissionsPreferences()) {
                    Preference heading = preferenceScreen.findPreference(PREF_PERMISSIONS);
                    preferenceScreen.removePreference(heading);
                }
                return true;
            }
        }

        // Handle the Clear & Reset preference click by showing a confirmation.
        new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.website_reset)
                .setMessage(R.string.website_reset_confirmation)
                .setPositiveButton(R.string.website_reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetSite();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return true;
    }

    /**
     * Resets the current site, clearing all permissions and storage used (inc. cookies).
     */
    @VisibleForTesting
    protected void resetSite() {
        if (getActivity() == null) return;
        // Clear the screen.
        // TODO(mvanouwerkerk): Refactor this class so that it does not depend on the screen state
        // for its logic. This class should maintain its own data model, and only update the screen
        // after a change is made.
        PreferenceScreen screen = getPreferenceScreen();
        for (String key : PERMISSION_PREFERENCE_KEYS) {
            Preference preference = screen.findPreference(key);
            if (preference != null) screen.removePreference(preference);
        }

        String origin = mSite.getAddress().getOrigin();
        WebsitePreferenceBridge.nativeClearCookieData(origin);
        WebsitePreferenceBridge.nativeClearBannerData(origin);

        // Clear the permissions.
        mSite.setAutoplayPermission(ContentSetting.DEFAULT);
        mSite.setBackgroundSyncPermission(ContentSetting.DEFAULT);
        mSite.setCameraPermission(ContentSetting.DEFAULT);
        mSite.setCookiePermission(ContentSetting.DEFAULT);
        mSite.setGeolocationPermission(ContentSetting.DEFAULT);
        mSite.setJavaScriptPermission(ContentSetting.DEFAULT);
        mSite.setKeygenPermission(ContentSetting.DEFAULT);
        mSite.setMicrophonePermission(ContentSetting.DEFAULT);
        mSite.setMidiPermission(ContentSetting.DEFAULT);
        mSite.setNotificationPermission(ContentSetting.DEFAULT);
        mSite.setPopupPermission(ContentSetting.DEFAULT);
        mSite.setProtectedMediaIdentifierPermission(ContentSetting.DEFAULT);

        for (UsbInfo info : mSite.getUsbInfo()) info.revoke();

        // Clear the storage and finish the activity if necessary.
        if (mSite.getTotalUsage() > 0) {
            clearStoredData();
        } else {
            // Clearing stored data implies popping back to parent menu if there
            // is nothing left to show. Therefore, we only need to explicitly
            // close the activity if there's no stored data to begin with.
            getActivity().finish();
        }
    }
}
