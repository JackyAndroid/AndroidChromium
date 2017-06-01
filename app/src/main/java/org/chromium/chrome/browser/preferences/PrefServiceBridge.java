// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.ContentSettingException;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;

import java.util.ArrayList;
import java.util.List;

/**
 * PrefServiceBridge is a singleton which provides access to some native preferences. Ideally
 * preferences should be grouped with their relevant functionality but this is a grab-bag for other
 * preferences.
 */
public final class PrefServiceBridge {
    // These values must match the native enum values in
    // SupervisedUserURLFilter::FilteringBehavior
    public static final int SUPERVISED_USER_FILTERING_ALLOW = 0;
    public static final int SUPERVISED_USER_FILTERING_WARN = 1;
    public static final int SUPERVISED_USER_FILTERING_BLOCK = 2;

    private static final String MIGRATION_PREF_KEY = "PrefMigrationVersion";
    private static final int MIGRATION_CURRENT_VERSION = 4;

    private static final String HTTPS_SCHEME = "https";

    // Object to notify when "clear browsing data" completes.
    private OnClearBrowsingDataListener mClearBrowsingDataListener;
    private static final String LOG_TAG = "PrefServiceBridge";

    // Constants related to the Contextual Search preference.
    private static final String CONTEXTUAL_SEARCH_DISABLED = "false";
    private static final String CONTEXTUAL_SEARCH_ENABLED = "true";

    // The key to store whether the Location Permission was automatically added for the search
    // engine set as default.
    public static final String LOCATION_AUTO_ALLOWED = "search_engine_location_auto_allowed";

    /**
     * Structure that holds all the version information about the current Chrome browser.
     */
    public static class AboutVersionStrings {
        private final String mApplicationVersion;
        private final String mOSVersion;

        private AboutVersionStrings(String applicationVersion, String osVersion) {
            mApplicationVersion = applicationVersion;
            mOSVersion = osVersion;
        }

        public String getApplicationVersion() {
            return mApplicationVersion;
        }

        public String getOSVersion() {
            return mOSVersion;
        }
    }

    /**
     * Interface for a class that is listening to clear browser data events.
     */
    public interface OnClearBrowsingDataListener {
        public abstract void onBrowsingDataCleared();
    }

    /**
     * Interface for a class that is fetching important site information.
     */
    public interface ImportantSitesCallback {
        /**
         * Called when the list of important registerable domains has been fetched from cpp.
         * See net/base/registry_controlled_domains/registry_controlled_domain.h for more details on
         * registrable domains and the current list of effective eTLDs.
         * @param domains Important registerable domains.
         * @param exampleOrigins Example origins for each domain. These can be used to retrieve
         *                       favicons.
         * @param importantReasons Bitfield of reasons why this domain was selected. Pass this back
         *                         to clearBrowinsgData so we can record metrics.
         */
        @CalledByNative("ImportantSitesCallback")
        void onImportantRegisterableDomainsReady(
                String[] domains, String[] exampleOrigins, int[] importantReasons);
    }

    /**
     * Interface to a class that receives callbacks instructing it to inform the user about other
     * forms of browsing history.
     */
    public interface OtherFormsOfBrowsingHistoryListener {
        /**
         * Called by the web history service when it discovers that other forms of browsing history
         * exist.
         */
        @CalledByNative("OtherFormsOfBrowsingHistoryListener")
        public abstract void enableDialogAboutOtherFormsOfBrowsingHistory();

        /**
         * Called by the web history service when the conditions for showing the dialog about
         * other forms of browsing history are met.
         */
        @CalledByNative("OtherFormsOfBrowsingHistoryListener")
        public abstract void showNoticeAboutOtherFormsOfBrowsingHistory();
    }

    @CalledByNative
    private static AboutVersionStrings createAboutVersionStrings(String applicationVersion,
            String osVersion) {
        return new AboutVersionStrings(applicationVersion, osVersion);
    }

    private PrefServiceBridge() {
        TemplateUrlService.getInstance().load();
    }

    private static PrefServiceBridge sInstance;

    /**
     * @return The singleton preferences object.
     */
    public static PrefServiceBridge getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) sInstance = new PrefServiceBridge();
        return sInstance;
    }

    /**
     * @return Whether the preferences have been initialized.
     */
    public static boolean isInitialized() {
        return sInstance != null;
    }

    /**
     * Migrates (synchronously) the preferences to the most recent version.
     */
    public void migratePreferences(Context context) {
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        int currentVersion = preferences.getInt(MIGRATION_PREF_KEY, 0);
        if (currentVersion == MIGRATION_CURRENT_VERSION) return;
        if (currentVersion > MIGRATION_CURRENT_VERSION) {
            Log.e(LOG_TAG, "Saved preferences version is newer than supported.  Attempting to "
                    + "run an older version of Chrome without clearing data is unsupported and "
                    + "the results may be unpredictable.");
        }

        if (currentVersion < 1) {
            nativeMigrateJavascriptPreference();
        }
        // Steps 2,3,4 intentionally skipped.
        preferences.edit().putInt(MIGRATION_PREF_KEY, MIGRATION_CURRENT_VERSION).apply();
    }

    /**
     * Add a permission entry for Location for the default search engine.
     * @param allowed Whether to create an Allowed permission or a Denied permission.
     * @param context The current context to use.
     */
    public static void maybeCreatePermissionForDefaultSearchEngine(
            boolean allowed, Context context) {
        TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        String url = templateUrlService.getSearchEngineUrlFromTemplateUrl(
                templateUrlService.getDefaultSearchEngineIndex());
        if (allowed && !url.startsWith("https:")) return;
        GeolocationInfo locationSettings = new GeolocationInfo(url, null, false);
        ContentSetting locationPermission = locationSettings.getContentSetting();
        if (locationPermission == null || locationPermission == ContentSetting.ASK) {
            WebsitePreferenceBridge.nativeSetGeolocationSettingForOrigin(url, url,
                    allowed ? ContentSetting.ALLOW.toInt() : ContentSetting.BLOCK.toInt(), false);
            SharedPreferences sharedPreferences =
                    ContextUtils.getAppSharedPreferences();
            sharedPreferences.edit().putBoolean(LOCATION_AUTO_ALLOWED, true).apply();
        }
    }

    /**
     * Returns whether a particular content setting type is enabled.
     * @param contentSettingsType The content setting type to check.
     */
    public boolean isContentSettingEnabled(int contentSettingsType) {
        return nativeIsContentSettingEnabled(contentSettingsType);
    }

    /**
     * @return Whether a particular content setting type is managed by policy.
     * @param contentSettingsType The content setting type to check.
     */
    public boolean isContentSettingManaged(int contentSettingsType) {
        return nativeIsContentSettingManaged(contentSettingsType);
    }

    /**
     * Sets a default value for content setting type.
     * @param contentSettingsType The content setting type to check.
     * @param enabled Whether the default value should be disabled or enabled.
     */
    public void setContentSettingEnabled(int contentSettingsType, boolean enabled) {
        nativeSetContentSettingEnabled(contentSettingsType, enabled);
    }

    /**
     * Returns all the currently saved exceptions for a given content settings type.
     * @param contentSettingsType The type to fetch exceptions for.
     */
    public List<ContentSettingException> getContentSettingsExceptions(int contentSettingsType) {
        List<ContentSettingException> list = new ArrayList<ContentSettingException>();
        nativeGetContentSettingsExceptions(contentSettingsType, list);
        return list;
    }

    @CalledByNative
    private static void addContentSettingExceptionToList(
            ArrayList<ContentSettingException> list,
            int contentSettingsType,
            String pattern,
            int contentSetting,
            String source) {
        ContentSettingException exception = new ContentSettingException(
                contentSettingsType, pattern, ContentSetting.fromInt(contentSetting), source);
        list.add(exception);
    }

    /**
     * Return the android permission string for a given {@link ContentSettingsType}.  If there
     * is no corresponding permission, then null will be returned.
     *
     * @param contentSettingType The content setting to get the android permission for.
     * @return The android permission for the given content setting.
     */
    @CalledByNative
    public static String getAndroidPermissionForContentSetting(int contentSettingType) {
        if (contentSettingType == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION) {
            return android.Manifest.permission.ACCESS_FINE_LOCATION;
        }
        if (contentSettingType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC) {
            return android.Manifest.permission.RECORD_AUDIO;
        }
        if (contentSettingType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA) {
            return android.Manifest.permission.CAMERA;
        }
        return null;
    }

    /**
     * @return Whether autoplay is enabled.
     */
    public boolean isAutoplayEnabled() {
        return nativeGetAutoplayEnabled();
    }

    public boolean isAcceptCookiesEnabled() {
        return nativeGetAcceptCookiesEnabled();
    }

    /**
     * @return Whether cookies acceptance is configured by policy
     */
    public boolean isAcceptCookiesManaged() {
        return nativeGetAcceptCookiesManaged();
    }

    public boolean isBlockThirdPartyCookiesEnabled() {
        return nativeGetBlockThirdPartyCookiesEnabled();
    }

    /**
     * @return Whether third-party cookie blocking is configured by policy
     */
    public boolean isBlockThirdPartyCookiesManaged() {
        return nativeGetBlockThirdPartyCookiesManaged();
    }

    public boolean isRememberPasswordsEnabled() {
        return nativeGetRememberPasswordsEnabled();
    }

    public boolean isPasswordManagerAutoSigninEnabled() {
        return nativeGetPasswordManagerAutoSigninEnabled();
    }

    /**
     * @return Whether password storage is configured by policy
     */
    public boolean isRememberPasswordsManaged() {
        return nativeGetRememberPasswordsManaged();
    }

    public boolean isPasswordManagerAutoSigninManaged() {
        return nativeGetPasswordManagerAutoSigninManaged();
    }

    /**
     * @return Whether notifications are enabled.
     */
    public boolean isNotificationsEnabled() {
        return nativeGetNotificationsEnabled();
    }

    /**
     * @return Whether vibration is enabled for notifications.
     */
    public boolean isNotificationsVibrateEnabled() {
        return nativeGetNotificationsVibrateEnabled();
    }

    /**
     * @return Whether geolocation information can be shared with content.
     */
    public boolean isAllowLocationEnabled() {
        return nativeGetAllowLocationEnabled();
    }

    /**
     * @return Whether geolocation information access is set to be shared with all sites, by policy.
     */
    public boolean isLocationAllowedByPolicy() {
        return nativeGetLocationAllowedByPolicy();
    }

    /**
     * @return Whether the location preference is modifiable by the user.
     */
    public boolean isAllowLocationUserModifiable() {
        return nativeGetAllowLocationUserModifiable();
    }

    /**
     * @return Whether the location preference is
     * being managed by the custodian of the supervised account.
     */
    public boolean isAllowLocationManagedByCustodian() {
        return nativeGetAllowLocationManagedByCustodian();
    }

    /**
     * @return Whether Do Not Track is enabled
     */
    public boolean isDoNotTrackEnabled() {
        return nativeGetDoNotTrackEnabled();
    }

    public boolean getPasswordEchoEnabled() {
        return nativeGetPasswordEchoEnabled();
    }

    /**
     * @return Whether EULA has been accepted by the user.
     */
    public boolean isFirstRunEulaAccepted() {
        return nativeGetFirstRunEulaAccepted();
    }

    /**
     * @return true if JavaScript is enabled. It may return the temporary value set by
     * {@link #setJavaScriptEnabled}. The default is true.
     */
    public boolean javaScriptEnabled() {
        return isContentSettingEnabled(ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT);
    }

    /**
     * @return Whether JavaScript is managed by policy.
     */
    public boolean javaScriptManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT);
    }

    /**
     * @return true if background sync is managed by policy.
     */
    public boolean isBackgroundSyncManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC);
    }

    /**
     * @return true if background sync is enabled.
     */
    public boolean isBackgroundSyncAllowed() {
        return nativeGetBackgroundSyncEnabled();
    }

    /**
     * Sets the preference that controls protected media identifier.
     */
    public void setProtectedMediaIdentifierEnabled(boolean enabled) {
        nativeSetProtectedMediaIdentifierEnabled(enabled);
    }

    /**
     * Sets the preference that controls translate
     */
    public void setTranslateEnabled(boolean enabled) {
        nativeSetTranslateEnabled(enabled);
    }

    /**
     * Sets the preference that signals when the user has accepted the EULA.
     */
    public void setEulaAccepted() {
        nativeSetEulaAccepted();
    }

    /**
     * Resets translate defaults if needed
     */
    public void resetTranslateDefaults() {
        nativeResetTranslateDefaults();
    }

    /**
     * Enable or disable JavaScript.
     */
    public void setJavaScriptEnabled(boolean enabled) {
        setContentSettingEnabled(ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT, enabled);
    }

    /**
     * Enable or disable background sync.
     */
    public void setBackgroundSyncEnabled(boolean enabled) {
        nativeSetBackgroundSyncEnabled(enabled);
    }

    /**
     * @return the last account id associated with sync.
     */
    public String getSyncLastAccountId() {
        return nativeGetSyncLastAccountId();
    }

    /**
     * @return the last account username associated with sync.
     */
    public String getSyncLastAccountName() {
        return nativeGetSyncLastAccountName();
    }

    /**
     * @return Whether Search Suggest is enabled.
     */
    public boolean isSearchSuggestEnabled() {
        return nativeGetSearchSuggestEnabled();
    }

    /**
     * Sets whether search suggest should be enabled.
     */
    public void setSearchSuggestEnabled(boolean enabled) {
        nativeSetSearchSuggestEnabled(enabled);
    }

    /**
     * @return Whether Search Suggest is configured by policy.
     */
    public boolean isSearchSuggestManaged() {
        return nativeGetSearchSuggestManaged();
    }

    /**
     * @return the Contextual Search preference.
     */
    public String getContextualSearchPreference() {
        return nativeGetContextualSearchPreference();
    }

    /**
     * Sets the Contextual Search preference.
     * @param prefValue one of "", CONTEXTUAL_SEARCH_ENABLED or CONTEXTUAL_SEARCH_DISABLED.
     */
    public void setContextualSearchPreference(String prefValue) {
        nativeSetContextualSearchPreference(prefValue);
    }

    /**
     * @return Whether the Contextual Search feature was disabled by the user explicitly.
     */
    public boolean isContextualSearchDisabled() {
        return getContextualSearchPreference().equals(CONTEXTUAL_SEARCH_DISABLED);
    }

    /**
     * @return Whether the Contextual Search feature is disabled by policy.
     */
    public boolean isContextualSearchDisabledByPolicy() {
        return nativeGetContextualSearchPreferenceIsManaged()
                && isContextualSearchDisabled();
    }

    /**
     * @return Whether the Contextual Search feature is uninitialized (preference unset by the
     *         user).
     */
    public boolean isContextualSearchUninitialized() {
        return getContextualSearchPreference().isEmpty();
    }

    /**
     * @param Whether Contextual Search should be enabled.
     */
    public void setContextualSearchState(boolean enabled) {
        setContextualSearchPreference(enabled
                ? CONTEXTUAL_SEARCH_ENABLED : CONTEXTUAL_SEARCH_DISABLED);
    }

    /**
     * @return Whether the active Safe Browsing Extended Reporting pref is the new Scout pref.
     */
    public boolean isSafeBrowsingScoutReportingActive() {
        return nativeIsScoutExtendedReportingActive();
    }

    /**
     * @return Whether Safe Browsing Extended Reporting is currently enabled.
     */
    public boolean isSafeBrowsingExtendedReportingEnabled() {
        return nativeGetSafeBrowsingExtendedReportingEnabled();
    }

    /**
     * @param Whether Safe Browsing Extended Reporting should be enabled.
     */
    public void setSafeBrowsingExtendedReportingEnabled(boolean enabled) {
        nativeSetSafeBrowsingExtendedReportingEnabled(enabled);
    }

    /**
     * @return Whether Safe Browsing Extended Reporting is managed
     */
    public boolean isSafeBrowsingExtendedReportingManaged() {
        return nativeGetSafeBrowsingExtendedReportingManaged();
    }

    /**
     * @return Whether Safe Browsing is currently enabled.
     */
    public boolean isSafeBrowsingEnabled() {
        return nativeGetSafeBrowsingEnabled();
    }

    /**
     * @param Whether Safe Browsing should be enabled.
     */
    public void setSafeBrowsingEnabled(boolean enabled) {
        nativeSetSafeBrowsingEnabled(enabled);
    }

    /**
     * @return Whether Safe Browsing is managed
     */
    public boolean isSafeBrowsingManaged() {
        return nativeGetSafeBrowsingManaged();
    }

    /**
     * @return Whether there is a user set value for kNetworkPredictionOptions.  This should only be
     * used for preference migration. See http://crbug.com/334602
     */
    public boolean obsoleteNetworkPredictionOptionsHasUserSetting() {
        return nativeObsoleteNetworkPredictionOptionsHasUserSetting();
    }

    /**
     * @return Network predictions preference.
     */
    public boolean getNetworkPredictionEnabled() {
        return nativeGetNetworkPredictionEnabled();
    }

    /**
     * Sets network predictions preference.
     */
    public void setNetworkPredictionEnabled(boolean enabled) {
        nativeSetNetworkPredictionEnabled(enabled);
    }

    /**
     * @return Whether Network Predictions is configured by policy.
     */
    public boolean isNetworkPredictionManaged() {
        return nativeGetNetworkPredictionManaged();
    }

    /**
     * Checks whether network predictions are allowed given preferences and current network
     * connection type.
     * @return Whether network predictions are allowed.
     */
    public boolean canPrefetchAndPrerender() {
        return nativeCanPrefetchAndPrerender();
    }

    /**
     * @return Whether the web service to resolve navigation error is enabled.
     */
    public boolean isResolveNavigationErrorEnabled() {
        return nativeGetResolveNavigationErrorEnabled();
    }

    /**
     * @return Whether the web service to resolve navigation error is configured by policy.
     */
    public boolean isResolveNavigationErrorManaged() {
        return nativeGetResolveNavigationErrorManaged();
    }

    /**
     * @return Whether or not the protected media identifier is enabled.
     */
    public boolean isProtectedMediaIdentifierEnabled() {
        return nativeGetProtectedMediaIdentifierEnabled();
    }

    /**
     * @return true if translate is enabled, false otherwise.
     */
    public boolean isTranslateEnabled() {
        return nativeGetTranslateEnabled();
    }

    /**
     * @return Whether translate is configured by policy
     */
    public boolean isTranslateManaged() {
        return nativeGetTranslateManaged();
    }

    /**
     * Sets whether the web service to resolve navigation error should be enabled.
     */
    public void setResolveNavigationErrorEnabled(boolean enabled) {
        nativeSetResolveNavigationErrorEnabled(enabled);
    }

    /**
     * Checks the state of deletion preference for a certain browsing data type.
     * @param dataType The requested browsing data type (from the shared enum
     *      {@link org.chromium.chrome.browser.browsing_data.BrowsingDataType}).
     * @return The state of the corresponding deletion preference.
     */
    public boolean getBrowsingDataDeletionPreference(int dataType) {
        return nativeGetBrowsingDataDeletionPreference(dataType);
    }

    /**
     * Sets the state of deletion preference for a certain browsing data type.
     * @param dataType The requested browsing data type (from the shared enum
     *      {@link org.chromium.chrome.browser.browsing_data.BrowsingDataType}).
     * @param value The state to be set.
     */
    public void setBrowsingDataDeletionPreference(int dataType, boolean value) {
        nativeSetBrowsingDataDeletionPreference(dataType, value);
    }

    /**
     * Gets the time period for which browsing data will be deleted.
     * @return The currently selected browsing data deletion time period (from the shared enum
     *      {@link org.chromium.chrome.browser.browsing_data.TimePeriod}).
     */
    public int getBrowsingDataDeletionTimePeriod() {
        return nativeGetBrowsingDataDeletionTimePeriod();
    }

    /**
     * Sets the time period for which browsing data will be deleted.
     * @param timePeriod The selected browsing data deletion time period (from the shared enum
     *      {@link org.chromium.chrome.browser.browsing_data.TimePeriod}).
     */
    public void setBrowsingDataDeletionTimePeriod(int timePeriod) {
        nativeSetBrowsingDataDeletionTimePeriod(timePeriod);
    }

    /**
     * Clear the specified types of browsing data asynchronously.
     * |listener| is an object to be notified when clearing completes.
     * It can be null, but many operations (e.g. navigation) are
     * ill-advised while browsing data is being cleared.
     * @param listener A listener to call back when the clearing is finished.
     * @param dataTypes An array of browsing data types to delete, represented as values from
     *      the shared enum {@link org.chromium.chrome.browser.browsing_data.BrowsingDataType}.
     * @param timePeriod The time period for which to delete the data, represented as a value from
     *      the shared enum {@link org.chromium.chrome.browser.browsing_data.TimePeriod}.
     */
    public void clearBrowsingData(
            OnClearBrowsingDataListener listener, int[] dataTypes, int timePeriod) {
        clearBrowsingDataExcludingDomains(listener, dataTypes, timePeriod, new String[0],
                new int[0], new String[0], new int[0]);
    }

    /**
     * Same as above, but now we can specify a list of domains to exclude from clearing browsing
     * data.
     * Do not use this method unless caller knows what they're doing. Not all backends are supported
     * yet, and more data than expected could be deleted. See crbug.com/113621.
     * @param listener A listener to call back when the clearing is finished.
     * @param dataTypes An array of browsing data types to delete, represented as values from
     *      the shared enum {@link org.chromium.chrome.browser.browsing_data.BrowsingDataType}.
     * @param timePeriod The time period for which to delete the data, represented as a value from
     *      the shared enum {@link org.chromium.chrome.browser.browsing_data.TimePeriod}.
     * @param blacklistDomains A list of registerable domains that we don't clear data for.
     * @param blacklistedDomainReasons A list of the reason metadata for the blacklisted domains.
     * @param ignoredDomains A list of ignored domains that the user chose to not blacklist. We use
     *                       these to remove important site entries if the user ignores them enough.
     * @param ignoredDomainReasons A list of reason metadata for the ignored domains.
     */
    public void clearBrowsingDataExcludingDomains(OnClearBrowsingDataListener listener,
            int[] dataTypes, int timePeriod, String[] blacklistDomains,
            int[] blacklistedDomainReasons, String[] ignoredDomains, int[] ignoredDomainReasons) {
        assert mClearBrowsingDataListener == null;
        mClearBrowsingDataListener = listener;
        nativeClearBrowsingData(dataTypes, timePeriod, blacklistDomains, blacklistedDomainReasons,
                ignoredDomains, ignoredDomainReasons);
    }

    /*
     * Whether browser history can be deleted by the user.
     */
    public boolean canDeleteBrowsingHistory() {
        return nativeCanDeleteBrowsingHistory();
    }

    @CalledByNative
    private void browsingDataCleared() {
        if (mClearBrowsingDataListener != null) {
            mClearBrowsingDataListener.onBrowsingDataCleared();
            mClearBrowsingDataListener = null;
        }
    }

    /**
     * This fetches sites (registerable domains) that we consider important. This combines many
     * pieces of information, including site engagement and permissions. The callback is called
     * with the list of important registerable domains.
     *
     * See net/base/registry_controlled_domains/registry_controlled_domain.h for more details on
     * registrable domains and the current list of effective eTLDs.
     * @param callback The callback that will be used to set the list of important sites.
     */
    public static void fetchImportantSites(ImportantSitesCallback callback) {
        nativeFetchImportantSites(callback);
    }

    /**
     * @return The maximum number of important sites that will be returned from the call above.
     *         This is a constant that won't change.
     */
    public static int getMaxImportantSites() {
        return nativeGetMaxImportantSites();
    }

    /** This lets us mark an origin as important for testing. */
    @VisibleForTesting
    public static void markOriginAsImportantForTesting(String origin) {
        nativeMarkOriginAsImportantForTesting(origin);
    }

    /**
     * Requests that the web history service finds out if we should inform the user about the
     * existence of other forms of browsing history. The response will be asynchronous, through
     * {@link OtherFormsOfBrowsingHistoryListener}.
     */
    public void requestInfoAboutOtherFormsOfBrowsingHistory(
            OtherFormsOfBrowsingHistoryListener listener) {
        nativeRequestInfoAboutOtherFormsOfBrowsingHistory(listener);
    }

    public void setAllowCookiesEnabled(boolean allow) {
        nativeSetAllowCookiesEnabled(allow);
    }

    public void setAutoplayEnabled(boolean allow) {
        nativeSetAutoplayEnabled(allow);
    }

    public void setBlockThirdPartyCookiesEnabled(boolean enabled) {
        nativeSetBlockThirdPartyCookiesEnabled(enabled);
    }

    public void setDoNotTrackEnabled(boolean enabled) {
        nativeSetDoNotTrackEnabled(enabled);
    }

    public void setRememberPasswordsEnabled(boolean allow) {
        nativeSetRememberPasswordsEnabled(allow);
    }

    public void setPasswordManagerAutoSigninEnabled(boolean enabled) {
        nativeSetPasswordManagerAutoSigninEnabled(enabled);
    }

    public void setNotificationsEnabled(boolean allow) {
        nativeSetNotificationsEnabled(allow);
    }

    public void setNotificationsVibrateEnabled(boolean enabled) {
        nativeSetNotificationsVibrateEnabled(enabled);
    }

    public void setAllowLocationEnabled(boolean allow) {
        nativeSetAllowLocationEnabled(allow);
    }

    public void setPasswordEchoEnabled(boolean enabled) {
        nativeSetPasswordEchoEnabled(enabled);
    }

    /**
     * @return The setting if popups are enabled
     */
    public boolean popupsEnabled() {
        return isContentSettingEnabled(ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS);
    }

    /**
     * @return Whether the setting to allow popups is configured by policy
     */
    public boolean isPopupsManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS);
    }

    /**
     * Sets the preferences on whether to enable/disable popups
     *
     * @param allow attribute to enable/disable popups
     */
    public void setAllowPopupsEnabled(boolean allow) {
        setContentSettingEnabled(ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS, allow);
    }

    /**
     * @return Whether the camera permission is enabled.
     */
    public boolean isCameraEnabled() {
        return nativeGetCameraEnabled();
    }

    /**
     * Sets the preferences on whether to enable/disable camera.
     */
    public void setCameraEnabled(boolean enabled) {
        nativeSetCameraEnabled(enabled);
    }

    /**
     * @return Whether the camera/microphone permission is managed
     * by the custodian of the supervised account.
     */
    public boolean isCameraManagedByCustodian() {
        return nativeGetCameraManagedByCustodian();
    }

    /**
     * @return Whether the camera permission is editable by the user.
     */
    public boolean isCameraUserModifiable() {
        return nativeGetCameraUserModifiable();
    }

    /**
     * @return Whether the microphone permission is enabled.
     */
    public boolean isMicEnabled() {
        return nativeGetMicEnabled();
    }

    /**
     * Sets the preferences on whether to enable/disable microphone.
     */
    public void setMicEnabled(boolean enabled) {
        nativeSetMicEnabled(enabled);
    }

    /**
     * @return Whether the microphone permission is managed by the custodian of
     * the supervised account.
     */
    public boolean isMicManagedByCustodian() {
        return nativeGetMicManagedByCustodian();
    }

    /**
     * @return Whether the microphone permission is editable by the user.
     */
    public boolean isMicUserModifiable() {
        return nativeGetMicUserModifiable();
    }

    /**
     * @return true if incognito mode is enabled.
     */
    public boolean isIncognitoModeEnabled() {
        return nativeGetIncognitoModeEnabled();
    }

    /**
     * @return true if incognito mode is managed by policy.
     */
    public boolean isIncognitoModeManaged() {
        return nativeGetIncognitoModeManaged();
    }

    /**
     * @return Whether printing is enabled.
     */
    public boolean isPrintingEnabled() {
        return nativeGetPrintingEnabled();
    }

    /**
     * @return Whether printing is managed by policy.
     */
    public boolean isPrintingManaged() {
        return nativeGetPrintingManaged();
    }

    /**
     * Get all the version strings from native.
     * @return AboutVersionStrings about version strings.
     */
    public AboutVersionStrings getAboutVersionStrings() {
        return nativeGetAboutVersionStrings();
    }

    /**
     * Reset accept-languages to its default value.
     *
     * @param defaultLocale A fall-back value such as en_US, de_DE, zh_CN, etc.
     */
    public void resetAcceptLanguages(String defaultLocale) {
        nativeResetAcceptLanguages(defaultLocale);
    }

    /**
     * @return Whether SafeSites for supervised users is enabled.
     */
    public boolean isSupervisedUserSafeSitesEnabled() {
        return nativeGetSupervisedUserSafeSitesEnabled();
    }

    /**
     * @return the default supervised user filtering behavior
     */
    public int getDefaultSupervisedUserFilteringBehavior() {
        return nativeGetDefaultSupervisedUserFilteringBehavior();
    }

    public String getSupervisedUserCustodianName() {
        return nativeGetSupervisedUserCustodianName();
    }

    public String getSupervisedUserCustodianEmail() {
        return nativeGetSupervisedUserCustodianEmail();
    }

    public String getSupervisedUserCustodianProfileImageURL() {
        return nativeGetSupervisedUserCustodianProfileImageURL();
    }

    public String getSupervisedUserSecondCustodianName() {
        return nativeGetSupervisedUserSecondCustodianName();
    }

    public String getSupervisedUserSecondCustodianEmail() {
        return nativeGetSupervisedUserSecondCustodianEmail();
    }

    public String getSupervisedUserSecondCustodianProfileImageURL() {
        return nativeGetSupervisedUserSecondCustodianProfileImageURL();
    }

    private native boolean nativeIsContentSettingEnabled(int contentSettingType);
    private native boolean nativeIsContentSettingManaged(int contentSettingType);
    private native void nativeSetContentSettingEnabled(int contentSettingType, boolean allow);
    private native void nativeGetContentSettingsExceptions(
            int contentSettingsType, List<ContentSettingException> list);
    public native void nativeSetContentSettingForPattern(
            int contentSettingType, String pattern, int setting);

    /**
      * @return Whether usage and crash reporting pref is enabled.
      */
    public boolean isMetricsReportingEnabled() {
        return nativeIsMetricsReportingEnabled();
    }

    /**
     * Sets whether the usage and crash reporting pref should be enabled.
     */
    public void setMetricsReportingEnabled(boolean enabled) {
        nativeSetMetricsReportingEnabled(enabled);
    }

    /**
     * @return Whether usage and crash report pref is managed.
     */
    public boolean isMetricsReportingManaged() {
        return nativeIsMetricsReportingManaged();
    }

    /**
     * @param clicked Whether the update menu item was clicked. The preference is stored to
     *                facilitate logging whether Chrome was updated after a click on the menu item.
     */
    public void setClickedUpdateMenuItem(boolean clicked) {
        nativeSetClickedUpdateMenuItem(clicked);
    }

    /**
     * @return Whether the update menu item was clicked.
     */
    public boolean getClickedUpdateMenuItem() {
        return nativeGetClickedUpdateMenuItem();
    }

    /**
     * @param version The latest version of Chrome available when the update menu item
     *                was clicked.
     */
    public void setLatestVersionWhenClickedUpdateMenuItem(String version) {
        nativeSetLatestVersionWhenClickedUpdateMenuItem(version);
    }

    /**
     * @return The latest version of Chrome available when the update menu item was clicked.
     */
    public String getLatestVersionWhenClickedUpdateMenuItem() {
        return nativeGetLatestVersionWhenClickedUpdateMenuItem();
    }

    @VisibleForTesting
    public void setSupervisedUserId(String supervisedUserId) {
        nativeSetSupervisedUserId(supervisedUserId);
    }

    private native boolean nativeGetAcceptCookiesEnabled();
    private native boolean nativeGetAcceptCookiesManaged();
    private native boolean nativeGetAutoplayEnabled();
    private native boolean nativeGetBackgroundSyncEnabled();
    private native boolean nativeGetBlockThirdPartyCookiesEnabled();
    private native boolean nativeGetBlockThirdPartyCookiesManaged();
    private native boolean nativeGetRememberPasswordsEnabled();
    private native boolean nativeGetPasswordManagerAutoSigninEnabled();
    private native boolean nativeGetRememberPasswordsManaged();
    private native boolean nativeGetPasswordManagerAutoSigninManaged();
    private native boolean nativeGetAllowLocationUserModifiable();
    private native boolean nativeGetLocationAllowedByPolicy();
    private native boolean nativeGetAllowLocationManagedByCustodian();
    private native boolean nativeGetDoNotTrackEnabled();
    private native boolean nativeGetPasswordEchoEnabled();
    private native boolean nativeGetFirstRunEulaAccepted();
    private native boolean nativeGetCameraEnabled();
    private native void nativeSetCameraEnabled(boolean allow);
    private native boolean nativeGetCameraUserModifiable();
    private native boolean nativeGetCameraManagedByCustodian();
    private native boolean nativeGetMicEnabled();
    private native void nativeSetMicEnabled(boolean allow);
    private native boolean nativeGetMicUserModifiable();
    private native boolean nativeGetMicManagedByCustodian();
    private native boolean nativeGetTranslateEnabled();
    private native boolean nativeGetTranslateManaged();
    private native boolean nativeGetResolveNavigationErrorEnabled();
    private native boolean nativeGetResolveNavigationErrorManaged();
    private native boolean nativeGetProtectedMediaIdentifierEnabled();
    private native boolean nativeGetIncognitoModeEnabled();
    private native boolean nativeGetIncognitoModeManaged();
    private native boolean nativeGetPrintingEnabled();
    private native boolean nativeGetPrintingManaged();
    private native boolean nativeGetSupervisedUserSafeSitesEnabled();
    private native void nativeSetTranslateEnabled(boolean enabled);
    private native void nativeResetTranslateDefaults();
    private native void nativeMigrateJavascriptPreference();
    private native boolean nativeGetBrowsingDataDeletionPreference(int dataType);
    private native void nativeSetBrowsingDataDeletionPreference(int dataType, boolean value);
    private native int nativeGetBrowsingDataDeletionTimePeriod();
    private native void nativeSetBrowsingDataDeletionTimePeriod(int timePeriod);
    private native void nativeClearBrowsingData(int[] dataTypes, int timePeriod,
            String[] blacklistDomains, int[] blacklistedDomainReasons, String[] ignoredDomains,
            int[] ignoredDomainReasons);
    private native void nativeRequestInfoAboutOtherFormsOfBrowsingHistory(
            OtherFormsOfBrowsingHistoryListener listener);
    private native boolean nativeCanDeleteBrowsingHistory();
    private static native void nativeFetchImportantSites(ImportantSitesCallback callback);
    private static native int nativeGetMaxImportantSites();
    private static native void nativeMarkOriginAsImportantForTesting(String origin);
    private native void nativeSetAutoplayEnabled(boolean allow);
    private native void nativeSetAllowCookiesEnabled(boolean allow);
    private native void nativeSetBackgroundSyncEnabled(boolean allow);
    private native void nativeSetBlockThirdPartyCookiesEnabled(boolean enabled);
    private native void nativeSetDoNotTrackEnabled(boolean enabled);
    private native void nativeSetRememberPasswordsEnabled(boolean allow);
    private native void nativeSetPasswordManagerAutoSigninEnabled(boolean enabled);
    private native void nativeSetProtectedMediaIdentifierEnabled(boolean enabled);
    private native boolean nativeGetAllowLocationEnabled();
    private native boolean nativeGetNotificationsEnabled();
    private native boolean nativeGetNotificationsVibrateEnabled();
    private native void nativeSetAllowLocationEnabled(boolean allow);
    private native void nativeSetNotificationsEnabled(boolean allow);
    private native void nativeSetNotificationsVibrateEnabled(boolean enabled);
    private native void nativeSetPasswordEchoEnabled(boolean enabled);
    private native boolean nativeCanPrefetchAndPrerender();
    private native AboutVersionStrings nativeGetAboutVersionStrings();
    private native void nativeSetContextualSearchPreference(String preference);
    private native String nativeGetContextualSearchPreference();
    private native boolean nativeGetContextualSearchPreferenceIsManaged();
    private native boolean nativeGetSearchSuggestEnabled();
    private native void nativeSetSearchSuggestEnabled(boolean enabled);
    private native boolean nativeGetSearchSuggestManaged();
    private native boolean nativeGetSafeBrowsingExtendedReportingEnabled();
    private native boolean nativeIsScoutExtendedReportingActive();
    private native void nativeSetSafeBrowsingExtendedReportingEnabled(boolean enabled);
    private native boolean nativeGetSafeBrowsingExtendedReportingManaged();
    private native boolean nativeGetSafeBrowsingEnabled();
    private native void nativeSetSafeBrowsingEnabled(boolean enabled);
    private native boolean nativeGetSafeBrowsingManaged();
    private native boolean nativeGetNetworkPredictionManaged();
    private native boolean nativeObsoleteNetworkPredictionOptionsHasUserSetting();
    private native boolean nativeGetNetworkPredictionEnabled();
    private native void nativeSetNetworkPredictionEnabled(boolean enabled);
    private native void nativeSetResolveNavigationErrorEnabled(boolean enabled);
    private native void nativeSetEulaAccepted();
    private native void nativeResetAcceptLanguages(String defaultLocale);
    private native String nativeGetSyncLastAccountId();
    private native String nativeGetSyncLastAccountName();
    private native String nativeGetSupervisedUserCustodianName();
    private native String nativeGetSupervisedUserCustodianEmail();
    private native String nativeGetSupervisedUserCustodianProfileImageURL();
    private native int nativeGetDefaultSupervisedUserFilteringBehavior();
    private native String nativeGetSupervisedUserSecondCustodianName();
    private native String nativeGetSupervisedUserSecondCustodianEmail();
    private native String nativeGetSupervisedUserSecondCustodianProfileImageURL();
    private native boolean nativeIsMetricsReportingEnabled();
    private native void nativeSetMetricsReportingEnabled(boolean enabled);
    private native boolean nativeIsMetricsReportingManaged();
    private native void nativeSetClickedUpdateMenuItem(boolean clicked);
    private native boolean nativeGetClickedUpdateMenuItem();
    private native void nativeSetLatestVersionWhenClickedUpdateMenuItem(String version);
    private native String nativeGetLatestVersionWhenClickedUpdateMenuItem();
    private native void nativeSetSupervisedUserId(String supervisedUserId);
}
