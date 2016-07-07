// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.chromium.base.ThreadUtils;
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
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
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
        // Step 2 intentionally skipped.
        if (currentVersion < 3) {
            nativeMigrateLocationPreference();
            nativeMigrateProtectedMediaPreference();
        }
        if (currentVersion < 4) {
            // For a brief period (M44 Beta), it was possible for users to disable images via Site
            // Settings. Now that this option has been removed, ensure that users are not stuck with
            // images disabled.
            setContentSettingEnabled(ContentSettingsType.CONTENT_SETTINGS_TYPE_IMAGES, true);
        }
        preferences.edit().putInt(MIGRATION_PREF_KEY, MIGRATION_CURRENT_VERSION).commit();
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
                    PreferenceManager.getDefaultSharedPreferences(context);
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
     * @return whether a particular content setting type is managed by policy.
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
        switch(contentSettingType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
                return android.Manifest.permission.ACCESS_FINE_LOCATION;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                return android.Manifest.permission.RECORD_AUDIO;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
                return android.Manifest.permission.CAMERA;
            default:
                return null;
        }
    }

    public boolean isAcceptCookiesEnabled() {
        return nativeGetAcceptCookiesEnabled();
    }

    /**
     * @return whether cookies acceptance is configured by policy
     */
    public boolean isAcceptCookiesManaged() {
        return nativeGetAcceptCookiesManaged();
    }

    public boolean isBlockThirdPartyCookiesEnabled() {
        return nativeGetBlockThirdPartyCookiesEnabled();
    }

    /**
     * @return whether third-party cookie blocking is configured by policy
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
     * @return whether password storage is configured by policy
     */
    public boolean isRememberPasswordsManaged() {
        return nativeGetRememberPasswordsManaged();
    }

    public boolean isPasswordManagerAutoSigninManaged() {
        return nativeGetPasswordManagerAutoSigninManaged();
    }

    /**
     * @return whether push notifications are enabled.
     */
    public boolean isPushNotificationsEnabled() {
        return nativeGetPushNotificationsEnabled();
    }

    /**
     * @return whether geolocation information can be shared with content.
     */
    public boolean isAllowLocationEnabled() {
        return nativeGetAllowLocationEnabled();
    }

    /**
     * @return whether geolocation information access is set to be shared with all sites, by policy.
     */
    public boolean isLocationAllowedByPolicy() {
        return nativeGetLocationAllowedByPolicy();
    }

    /**
     * @return whether the location preference is modifiable by the user.
     */
    public boolean isAllowLocationUserModifiable() {
        return nativeGetAllowLocationUserModifiable();
    }

    /**
     * @return whether the location preference is
     * being managed by the custodian of the supervised account.
     */
    public boolean isAllowLocationManagedByCustodian() {
        return nativeGetAllowLocationManagedByCustodian();
    }

    /**
     * @return whether Do Not Track is enabled
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
     * @return whether JavaScript is managed by policy.
     */
    public boolean javaScriptManaged() {
        return isContentSettingManaged(ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT);
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
     * Sets the preference that controls automatic detection of character encoding.
     */
    public void setAutoDetectEncodingEnabled(boolean enabled) {
        nativeSetAutoDetectEncodingEnabled(enabled);
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
     * @return the last account username associated with sync.
     */
    public String getSyncLastAccountName() {
        return nativeGetSyncLastAccountName();
    }

    /**
     * @return whether usage and crash report is managed.
     */
    public boolean isCrashReportManaged() {
        return nativeGetCrashReportManaged();
    }

    /**
     * Enable or disable crashes_ui.
     */
    public void setCrashReporting(boolean reporting) {
        nativeSetCrashReporting(reporting);
    }

    /**
     * Allow fullscreen without asking user for permission.
     *
     * @param allowed Whether to allow fullscreen without user permission.
     */
    public void setFullscreenAllowed(boolean allowed) {
        nativeSetFullscreenAllowed(allowed);
    }

    /**
     * @return whether Search Suggest is enabled.
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
     * @return whether Search Suggest is configured by policy.
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
     * @return whether the Contextual Search feature was disabled by the user explicitly.
     */
    public boolean isContextualSearchDisabled() {
        return getContextualSearchPreference().equals(CONTEXTUAL_SEARCH_DISABLED);
    }

    /**
     * @return whether the Contextual Search feature is disabled by policy.
     */
    public boolean isContextualSearchDisabledByPolicy() {
        return nativeGetContextualSearchPreferenceIsManaged()
                && isContextualSearchDisabled();
    }

    /**
     * @return whether the Contextual Search feature is uninitialized (preference unset by the
     *         user).
     */
    public boolean isContextualSearchUninitialized() {
        return getContextualSearchPreference().isEmpty();
    }

    /**
     * @param whether Contextual Search should be enabled.
     */
    public void setContextualSearchState(boolean enabled) {
        setContextualSearchPreference(enabled
                ? CONTEXTUAL_SEARCH_ENABLED : CONTEXTUAL_SEARCH_DISABLED);
    }

    /**
     * @return whether Safe Browsing Extended Reporting is currently enabled.
     */
    public boolean isSafeBrowsingExtendedReportingEnabled() {
        return nativeGetSafeBrowsingExtendedReportingEnabled();
    }

    /**
     * @param whether Safe Browsing Extended Reporting should be enabled.
     */
    public void setSafeBrowsingExtendedReportingEnabled(boolean enabled) {
        nativeSetSafeBrowsingExtendedReportingEnabled(enabled);
    }

    /**
     * @return whether Safe Browsing Extended Reporting is managed
     */
    public boolean isSafeBrowsingExtendedReportingManaged() {
        return nativeGetSafeBrowsingExtendedReportingManaged();
    }

    /**
     * @return whether Safe Browsing is currently enabled.
     */
    public boolean isSafeBrowsingEnabled() {
        return nativeGetSafeBrowsingEnabled();
    }

    /**
     * @param whether Safe Browsing should be enabled.
     */
    public void setSafeBrowsingEnabled(boolean enabled) {
        nativeSetSafeBrowsingEnabled(enabled);
    }

    /**
     * @return whether Safe Browsing is managed
     */
    public boolean isSafeBrowsingManaged() {
        return nativeGetSafeBrowsingManaged();
    }

    /**
     * @return Network predictions preference.
     */
    public NetworkPredictionOptions getNetworkPredictionOptions() {
        return NetworkPredictionOptions.intToEnum(nativeGetNetworkPredictionOptions());
    }

    /**
     * Sets network predictions preference.
     */
    public void setNetworkPredictionOptions(NetworkPredictionOptions option) {
        nativeSetNetworkPredictionOptions(option.enumToInt());
    }

    /**
     * @return whether Network Predictions is configured by policy.
     */
    public boolean isNetworkPredictionManaged() {
        return nativeGetNetworkPredictionManaged();
    }

    /**
     * Checks whether network predictions are allowed given preferences and current network
     * connection type.
     * @return Whether network predictions are allowed.
     */
    public boolean canPredictNetworkActions() {
        return nativeCanPredictNetworkActions();
    }

    /**
     * @return whether fullscreen is configured by policy.
     */
    public boolean isFullscreenManaged() {
        return nativeGetFullscreenManaged();
    }

    /**
     * @return whether fullscreen is allowed.
     */
    public boolean isFullscreenAllowed() {
        return nativeGetFullscreenAllowed();
    }

    /**
     * @return whether the web service to resolve navigation error is enabled.
     */
    public boolean isResolveNavigationErrorEnabled() {
        return nativeGetResolveNavigationErrorEnabled();
    }

    /**
     * @return whether the web service to resolve navigation error is configured by policy.
     */
    public boolean isResolveNavigationErrorManaged() {
        return nativeGetResolveNavigationErrorManaged();
    }

    /**
     * @return whether or not the protected media identifier is enabled.
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
     * @return whether translate is configured by policy
     */
    public boolean isTranslateManaged() {
        return nativeGetTranslateManaged();
    }

    /**
     * @return true if automatic detection of character encoding is enabled, false otherwise.
     */
    public boolean isAutoDetectEncodingEnabled() {
        return nativeGetAutoDetectEncodingEnabled();
    }

    /**
     * Sets whether the web service to resolve navigation error should be enabled.
     */
    public void setResolveNavigationErrorEnabled(boolean enabled) {
        nativeSetResolveNavigationErrorEnabled(enabled);
    }

    /**
     * Interface for a class that is listening to clear browser data events.
     */
    public interface OnClearBrowsingDataListener {
        public abstract void onBrowsingDataCleared();
    }

    /**
     * Clear the specified types of browsing data asynchronously.
     * |listener| is an object to be notified when clearing completes.
     * It can be null, but many operations (e.g. navigation) are
     * ill-advised while browsing data is being cleared.
     */
    public void clearBrowsingData(OnClearBrowsingDataListener listener,
            boolean history, boolean cache, boolean cookiesAndSiteData,
            boolean passwords, boolean formData) {
        assert mClearBrowsingDataListener == null;
        mClearBrowsingDataListener = listener;
        nativeClearBrowsingData(history, cache, cookiesAndSiteData, passwords, formData);
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

    public void setAllowCookiesEnabled(boolean allow) {
        nativeSetAllowCookiesEnabled(allow);
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

    public void setPushNotificationsEnabled(boolean allow) {
        nativeSetPushNotificationsEnabled(allow);
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
     * Sets whether JavaScript is allowed to run on the given website/domain.
     *
     * @param pattern A pattern that matches one or multiple domains. For
     *        details see examples in content_settings_pattern.h.
     * @param allow Whether to allow JavaScript on the given site/domain.
     */
    public void setJavaScriptAllowed(String pattern, boolean allow) {
        nativeSetJavaScriptAllowed(
                pattern, allow ? ContentSetting.ALLOW.toInt() : ContentSetting.BLOCK.toInt());
    }

    /**
     * Adds/Edit a popup exception
     *
     * @param pattern attribute for the popup exception pattern
     * @param allow attribute to specify whether to allow or block pattern
     */
    public void setPopupException(String pattern, boolean allow) {
        nativeSetPopupException(pattern, allow
                ? ContentSetting.ALLOW.toInt()
                : ContentSetting.BLOCK.toInt());
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
     * @return whether ForceGoogleSafeSearch is set
     */
    public boolean isForceGoogleSafeSearch() {
        return nativeGetForceGoogleSafeSearch();
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
      * @return whether Metrics reporting is enabled.
      */
    public boolean isMetricsReportingEnabled() {
        return nativeGetMetricsReportingEnabled();
    }

    /**
     * Sets whether the metrics reporting should be enabled.
     */
    public void setMetricsReportingEnabled(boolean enabled) {
        nativeSetMetricsReportingEnabled(enabled);
    }

    /**
     * @return whether the metrics reporting preference has been set by user.
     */
    public boolean hasSetMetricsReporting() {
        return nativeHasSetMetricsReporting();
    }

    private native boolean nativeGetAcceptCookiesEnabled();
    private native boolean nativeGetAcceptCookiesManaged();
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
    private native boolean nativeGetFullscreenAllowed();
    private native boolean nativeGetFullscreenManaged();
    private native boolean nativeGetTranslateEnabled();
    private native boolean nativeGetTranslateManaged();
    private native boolean nativeGetAutoDetectEncodingEnabled();
    private native boolean nativeGetResolveNavigationErrorEnabled();
    private native boolean nativeGetResolveNavigationErrorManaged();
    private native boolean nativeGetProtectedMediaIdentifierEnabled();
    private native boolean nativeGetCrashReportManaged();
    private native boolean nativeGetIncognitoModeEnabled();
    private native boolean nativeGetIncognitoModeManaged();
    private native boolean nativeGetPrintingEnabled();
    private native boolean nativeGetPrintingManaged();
    private native boolean nativeGetForceGoogleSafeSearch();
    private native void nativeSetTranslateEnabled(boolean enabled);
    private native void nativeSetAutoDetectEncodingEnabled(boolean enabled);
    private native void nativeResetTranslateDefaults();
    private native void nativeMigrateJavascriptPreference();
    private native void nativeMigrateLocationPreference();
    private native void nativeMigrateProtectedMediaPreference();
    private native void nativeSetJavaScriptAllowed(String pattern, int setting);
    private native void nativeClearBrowsingData(boolean history, boolean cache,
            boolean cookiesAndSiteData, boolean passwords, boolean formData);
    private native boolean nativeCanDeleteBrowsingHistory();
    private native void nativeSetAllowCookiesEnabled(boolean allow);
    private native void nativeSetBlockThirdPartyCookiesEnabled(boolean enabled);
    private native void nativeSetDoNotTrackEnabled(boolean enabled);
    private native void nativeSetFullscreenAllowed(boolean allowed);
    private native void nativeSetRememberPasswordsEnabled(boolean allow);
    private native void nativeSetPasswordManagerAutoSigninEnabled(boolean enabled);
    private native void nativeSetProtectedMediaIdentifierEnabled(boolean enabled);
    private native boolean nativeGetAllowLocationEnabled();
    private native boolean nativeGetPushNotificationsEnabled();
    private native void nativeSetAllowLocationEnabled(boolean allow);
    private native void nativeSetPushNotificationsEnabled(boolean allow);
    private native void nativeSetPasswordEchoEnabled(boolean enabled);
    private native void nativeSetPopupException(String pattern, int setting);
    private native void nativeSetCrashReporting(boolean reporting);
    private native boolean nativeCanPredictNetworkActions();
    private native AboutVersionStrings nativeGetAboutVersionStrings();
    private native void nativeSetContextualSearchPreference(String preference);
    private native String nativeGetContextualSearchPreference();
    private native boolean nativeGetContextualSearchPreferenceIsManaged();
    private native boolean nativeGetSearchSuggestEnabled();
    private native void nativeSetSearchSuggestEnabled(boolean enabled);
    private native boolean nativeGetSearchSuggestManaged();
    private native boolean nativeGetSafeBrowsingExtendedReportingEnabled();
    private native void nativeSetSafeBrowsingExtendedReportingEnabled(boolean enabled);
    private native boolean nativeGetSafeBrowsingExtendedReportingManaged();
    private native boolean nativeGetSafeBrowsingEnabled();
    private native void nativeSetSafeBrowsingEnabled(boolean enabled);
    private native boolean nativeGetSafeBrowsingManaged();
    private native boolean nativeGetNetworkPredictionManaged();
    private native int nativeGetNetworkPredictionOptions();
    private native void nativeSetNetworkPredictionOptions(int option);
    private native void nativeSetResolveNavigationErrorEnabled(boolean enabled);
    private native void nativeSetEulaAccepted();
    private native void nativeResetAcceptLanguages(String defaultLocale);
    private native String nativeGetSyncLastAccountName();
    private native String nativeGetSupervisedUserCustodianName();
    private native String nativeGetSupervisedUserCustodianEmail();
    private native String nativeGetSupervisedUserCustodianProfileImageURL();
    private native int nativeGetDefaultSupervisedUserFilteringBehavior();
    private native String nativeGetSupervisedUserSecondCustodianName();
    private native String nativeGetSupervisedUserSecondCustodianEmail();
    private native String nativeGetSupervisedUserSecondCustodianProfileImageURL();
    private native boolean nativeGetMetricsReportingEnabled();
    private native void nativeSetMetricsReportingEnabled(boolean enabled);
    private native boolean nativeHasSetMetricsReporting();
}
