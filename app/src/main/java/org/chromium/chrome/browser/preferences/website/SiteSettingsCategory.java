// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.preference.Preference;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * A base class for dealing with website settings categories.
 */
public class SiteSettingsCategory {
    // Valid values for passing to fromString() in this class.
    public static final String CATEGORY_ALL_SITES = "all_sites";
    public static final String CATEGORY_CAMERA = "camera";
    public static final String CATEGORY_COOKIES = "cookies";
    public static final String CATEGORY_DEVICE_LOCATION = "device_location";
    public static final String CATEGORY_FULLSCREEN = "fullscreen";
    public static final String CATEGORY_JAVASCRIPT = "javascript";
    public static final String CATEGORY_MICROPHONE = "microphone";
    public static final String CATEGORY_POPUPS = "popups";
    public static final String CATEGORY_PROTECTED_MEDIA = "protected_content";
    public static final String CATEGORY_NOTIFICATIONS = "notifications";
    public static final String CATEGORY_USE_STORAGE = "use_storage";

    // The id of this category.
    private String mCategory;

    // The id of a permission in Android M that governs this category. Can be blank if Android has
    // no equivalent permission for the category.
    private String mAndroidPermission;

    // The content settings type that this category represents. Can be -1 if the category has no
    // content settings type (such as All Sites).
    private int mContentSettingsType = -1;

    /**
     * Construct a SiteSettingsCategory.
     * @param category The string id of the category to construct.
     * @param androidPermission A string containing the id of a toggle-able permission in Android
     *        that this category represents (or blank, if Android does not expose that permission).
     * @param contentSettingsType The content settings type that this category represents (or -1
     *        if the category does not have a contentSettingsType, such as All Sites).
     */
    protected SiteSettingsCategory(
            String category, String androidPermission, int contentSettingsType) {
        mCategory = category;
        mAndroidPermission = androidPermission;
        mContentSettingsType = contentSettingsType;
    }

    /**
     * Construct a SiteSettingsCategory from a string.
     * @param category The string id of the category to construct. See valid values above.
     */
    public static SiteSettingsCategory fromString(String category) {
        assert !category.isEmpty();
        if (CATEGORY_ALL_SITES.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_ALL_SITES, "", -1);
        }
        if (CATEGORY_CAMERA.equals(category)) {
            return new SiteSettingsCategory(
                    SiteSettingsCategory.CATEGORY_CAMERA,
                    android.Manifest.permission.CAMERA,
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA);
        }
        if (CATEGORY_COOKIES.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_COOKIES, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES);
        }
        if (CATEGORY_JAVASCRIPT.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_JAVASCRIPT, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT);
        }
        if (CATEGORY_DEVICE_LOCATION.equals(category)) {
            return new LocationCategory();
        }
        if (CATEGORY_FULLSCREEN.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_FULLSCREEN, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN);
        }
        if (CATEGORY_MICROPHONE.equals(category)) {
            return new SiteSettingsCategory(
                    SiteSettingsCategory.CATEGORY_MICROPHONE,
                    android.Manifest.permission.RECORD_AUDIO,
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC);
        }
        if (CATEGORY_POPUPS.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_POPUPS, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS);
        }
        if (CATEGORY_PROTECTED_MEDIA.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_PROTECTED_MEDIA, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER);
        }
        if (CATEGORY_NOTIFICATIONS.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_NOTIFICATIONS, "",
                    ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS);
        }
        if (CATEGORY_USE_STORAGE.equals(category)) {
            return new SiteSettingsCategory(CATEGORY_USE_STORAGE, "", -1);
        }

        return null;
    }

    /**
     * Construct a SiteSettingsCategory from a content settings type. Note that not all categories
     * are associated with a content settings type (e.g. All Sites). Such categories must be created
     * fromString().
     */
    public static SiteSettingsCategory fromContentSettingsType(int contentSettingsType) {
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA) {
            return fromString(CATEGORY_CAMERA);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES) {
            return fromString(CATEGORY_COOKIES);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT) {
            return fromString(CATEGORY_JAVASCRIPT);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION) {
            return fromString(CATEGORY_DEVICE_LOCATION);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN) {
            return fromString(CATEGORY_FULLSCREEN);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC) {
            return fromString(CATEGORY_MICROPHONE);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS) {
            return fromString(CATEGORY_POPUPS);
        }
        if (contentSettingsType
                == ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER) {
            return fromString(CATEGORY_PROTECTED_MEDIA);
        }
        if (contentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS) {
            return fromString(CATEGORY_NOTIFICATIONS);
        }

        return null;
    }

    /**
     * Returns the content settings type for this category, or -1 if no such type exists.
     */
    public int toContentSettingsType() {
        return mContentSettingsType;
    }

    /**
     * Returns whether this category is the All Sites category.
     */
    public boolean showAllSites() {
        return CATEGORY_ALL_SITES.equals(mCategory);
    }

    /**
     * Returns whether this category is the Cookies category.
     */
    public boolean showCookiesSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES;
    }

    /**
     * Returns whether this category is the Camera category.
     */
    public boolean showCameraSites() {
        return mContentSettingsType
                == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA;
    }

    /**
     * Returns whether this category is the Fullscreen category.
     */
    public boolean showFullscreenSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN;
    }

    /**
     * Returns whether this category is the Geolocation category.
     */
    public boolean showGeolocationSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION;
    }

    /**
     * Returns whether this category is the JavaScript category.
     */
    public boolean showJavaScriptSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT;
    }

    /**
     * Returns whether this category is the Microphone category.
     */
    public boolean showMicrophoneSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC;
    }

    /**
     * Returns whether this category is the Popup category.
     */
    public boolean showPopupSites() {
        return mContentSettingsType == ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS;
    }

    /**
     * Returns whether this category is the Notifications category.
     */
    public boolean showNotificationsSites() {
        return mContentSettingsType
                == ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS;
    }

    /**
     * Returns whether this category is the Protected Media category.
     */
    public boolean showProtectedMediaSites() {
        return mContentSettingsType
                == ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER;
    }

    /**
     * Returns whether this category is the Storage category.
     */
    public boolean showStorageSites() {
        return CATEGORY_USE_STORAGE.equals(mCategory);
    }

    /**
     * Returns whether the current category is managed either by enterprise policy or by the
     * custodian of a supervised account.
     */
    public boolean isManaged() {
        PrefServiceBridge prefs = PrefServiceBridge.getInstance();
        if (showCameraSites()) return !prefs.isCameraUserModifiable();
        if (showCookiesSites()) return prefs.isAcceptCookiesManaged();
        if (showFullscreenSites()) return prefs.isFullscreenManaged();
        if (showGeolocationSites()) {
            return !prefs.isAllowLocationUserModifiable();
        }
        if (showJavaScriptSites()) return prefs.javaScriptManaged();
        if (showMicrophoneSites()) return !prefs.isMicUserModifiable();
        if (showPopupSites()) return prefs.isPopupsManaged();
        return false;
    }

    /**
     * Returns whether the current category is managed by the custodian (e.g. parent, not an
     * enterprise admin) of the account if the account is supervised.
     */
    public boolean isManagedByCustodian() {
        PrefServiceBridge prefs = PrefServiceBridge.getInstance();
        if (showGeolocationSites()) {
            return prefs.isAllowLocationManagedByCustodian();
        }
        if (showCameraSites()) {
            return prefs.isCameraManagedByCustodian();
        }
        if (showMicrophoneSites()) {
            return prefs.isMicManagedByCustodian();
        }
        return false;
    }

    /**
     * Configure a preference to show when when the Android permission for this category is
     * disabled.
     * @param osWarning A preference to hold the first permission warning. After calling this
     *                  method, if osWarning has no title, the preference should not be added to the
     *                  preference screen.
     * @param osWarningExtra A preference to hold any additional permission warning (if any). After
     *                       calling this method, if osWarningExtra has no title, the preference
     *                       should not be added to the preference screen.
     * @param activity The current activity.
     * @param category The category associated with the warnings.
     * @param specificCategory Whether the warnings refer to a single category or is an aggregate
     *                         for many permissions.
     */
    public void configurePermissionIsOffPreferences(Preference osWarning, Preference osWarningExtra,
            Activity activity, boolean specificCategory) {
        Intent perAppIntent = getIntentToEnableOsPerAppPermission(activity);
        Intent globalIntent = getIntentToEnableOsGlobalPermission(activity);
        String perAppMessage = getMessageForEnablingOsPerAppPermission(activity, !specificCategory);
        String globalMessage = getMessageForEnablingOsGlobalPermission(activity);

        Resources resources = activity.getResources();
        int color = ApiCompatibilityUtils.getColor(resources, R.color.pref_accent_color);
        ForegroundColorSpan linkSpan = new ForegroundColorSpan(color);

        if (perAppIntent != null) {
            SpannableString messageWithLink = SpanApplier.applySpans(
                    perAppMessage, new SpanInfo("<link>", "</link>", linkSpan));
            osWarning.setTitle(messageWithLink);
            osWarning.setIntent(perAppIntent);

            if (!specificCategory) {
                osWarning.setIcon(getDisabledInAndroidIcon(activity));
            }
        }

        if (globalIntent != null) {
            SpannableString messageWithLink = SpanApplier.applySpans(
                    globalMessage, new SpanInfo("<link>", "</link>", linkSpan));
            osWarningExtra.setTitle(messageWithLink);
            osWarningExtra.setIntent(globalIntent);

            if (!specificCategory) {
                if (perAppIntent == null) {
                    osWarningExtra.setIcon(getDisabledInAndroidIcon(activity));
                } else {
                    Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
                    osWarningExtra.setIcon(transparent);
                }
            }
        }
    }

    /**
     * Returns the icon for permissions that have been disabled by Android.
     */
    Drawable getDisabledInAndroidIcon(Activity activity) {
        Drawable icon = ApiCompatibilityUtils.getDrawable(activity.getResources(),
                R.drawable.exclamation_triangle);
        icon.mutate();
        int disabledColor = ApiCompatibilityUtils.getColor(activity.getResources(),
                R.color.pref_accent_color);
        icon.setColorFilter(disabledColor, PorterDuff.Mode.SRC_IN);
        return icon;
    }

    /**
     * Returns whether the permission is enabled in Android, both globally and per-app. If the
     * permission does not have a per-app setting or a global setting, true is assumed for either
     * that is missing (or both).
     */
    boolean enabledInAndroid(Context context) {
        return enabledGlobally() && enabledForChrome(context);
    }

    /**
     * Returns whether a permission is enabled across Android. Not all permissions can be disabled
     * globally, so the default is true, but can be overwritten in sub-classes.
     */
    protected boolean enabledGlobally() {
        return true;
    }

    /**
     * Returns whether a permission is enabled for Chrome specifically.
     */
    protected boolean enabledForChrome(Context context) {
        if (mAndroidPermission.isEmpty()) return true;
        return permissionOnInAndroid(mAndroidPermission, context);
    }

    /**
     * Returns whether to show the 'permission blocked' message. Majority of the time, that is
     * warranted when the permission is either blocked per app or globally. But there are exceptions
     * to this, so the sub-classes can overwrite.
     */
    boolean showPermissionBlockedMessage(Context context) {
        return !enabledForChrome(context) || !enabledGlobally();
    }

    /**
     * Returns the OS Intent to use to enable a per-app permission, or null if the permission is
     * already enabled. Android M and above provides two ways of doing this for some permissions,
     * most notably Location, one that is per-app and another that is global.
     */
    private Intent getIntentToEnableOsPerAppPermission(Context context) {
        if (enabledForChrome(context)) return null;
        return getAppInfoIntent(context);
    }

    /**
     * Returns the OS Intent to use to enable a permission globally, or null if there is no global
     * permission. Android M and above provides two ways of doing this for some permissions, most
     * notably Location, one that is per-app and another that is global.
     */
    protected Intent getIntentToEnableOsGlobalPermission(Context context) {
        return null;
    }

    /**
     * Returns the message to display when per-app permission is blocked.
     * @param plural Whether it applies to one per-app permission or multiple.
     */
    protected String getMessageForEnablingOsPerAppPermission(Activity activity, boolean plural) {
        return activity.getResources().getString(plural
                ? R.string.android_permission_off_plural
                : R.string.android_permission_off);
    }

    /**
     * Returns the message to display when per-app permission is blocked.
     */
    protected String getMessageForEnablingOsGlobalPermission(Activity activity) {
        return null;
    }

    /**
     * Returns an Intent to show the App Info page for the current app.
     */
    private Intent getAppInfoIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(
                new Uri.Builder().scheme("package").opaquePart(context.getPackageName()).build());
        return intent;
    }

    /**
     * Returns whether a per-app permission is enabled.
     * @param permission The string of the permission to check.
     */
    private boolean permissionOnInAndroid(String permission, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        return PackageManager.PERMISSION_GRANTED == context.getPackageManager().checkPermission(
                permission, context.getPackageName());
    }
}
