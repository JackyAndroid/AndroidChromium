// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;

/**
 * A class with utility functions that get the appropriate string and icon resources for the
 * Android UI that allows managing content settings.
 */
public class ContentSettingsResources {
    /**
     * Returns the resource id of the icon for a content type.
     */
    public static int getIcon(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
                return R.drawable.permission_cookie;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
                return R.drawable.permission_fullscreen;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
                return R.drawable.permission_location;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
                return R.drawable.permission_javascript;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
                return R.drawable.permission_camera;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                return R.drawable.permission_mic;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MIDI_SYSEX:
                return R.drawable.permission_midi;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
                return R.drawable.permission_push_notification;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                return R.drawable.permission_popups;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER:
                return R.drawable.permission_protected_media;
            default:
                return 0;
        }
    }

    /**
     * Returns the resource id of the title (short version), shown on the Site Settings page
     * and in the global toggle at the top of a Website Settings page for a content type.
     */
    public static int getTitle(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
                return R.string.cookies_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
                return R.string.website_settings_fullscreen;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
                return R.string.website_settings_device_location;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
                return R.string.javascript_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
                return R.string.website_settings_use_camera;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                return R.string.website_settings_use_mic;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
                return R.string.push_notifications_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                return R.string.popup_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER:
                return org.chromium.chrome.R.string.protected_content;
            default:
                return 0;
        }
    }

    /**
     * Returns the resource id of the title explanation, shown on the Website Details page for
     * a content type.
     */
    public static int getExplanation(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
                return R.string.cookies_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
                return R.string.fullscreen_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
                return R.string.geolocation_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
                return R.string.javascript_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
                return R.string.camera_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                return R.string.mic_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MIDI_SYSEX:
                return R.string.midi_sysex_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
                return R.string.push_notifications_permission_title;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                return R.string.popup_permission_title;
            default:
                return 0;
        }
    }

    /**
     * Returns which ContentSetting the global default is set to, when enabled.
     * Either Ask/Allow. Not required unless this entry describes a settings
     * that appears on the Site Settings page and has a global toggle.
     */
    public static ContentSetting getDefaultEnabledValue(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                return ContentSetting.ALLOW;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER:
                return ContentSetting.ASK;
            default:
                return null;
        }
    }

    /**
     * Returns which ContentSetting the global default is set to, when disabled.
     * Usually Block. Not required unless this entry describes a settings
     * that appears on the Site Settings page and has a global toggle.
     */
    public static ContentSetting getDefaultDisabledValue(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
                return ContentSetting.ASK;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_PROTECTED_MEDIA_IDENTIFIER:
                return ContentSetting.BLOCK;
            default:
                return null;
        }
    }

    /**
     * Returns the string resource id for a given ContentSetting to show with a permission category.
     * @param value The ContentSetting for which we want the resource.
     */
    private static int getCategorySummary(ContentSetting value) {
        switch (value) {
            case ALLOW:
                return R.string.website_settings_category_allowed;
            case BLOCK:
                return R.string.website_settings_category_blocked;
            case ASK:
                return R.string.website_settings_category_ask;
            default:
                return 0;
        }
    }

    /**
     * Returns the string resource id for a content type to show with a permission category.
     * @param enabled Whether the content type is enabled.
     */
    public static int getCategorySummary(int contentType, boolean enabled) {
        return getCategorySummary(enabled ? getDefaultEnabledValue(contentType)
                                          : getDefaultDisabledValue(contentType));
    }

    /**
     * Returns the string resource id for a given ContentSetting to show
     * with a particular website.
     * @param value The ContentSetting for which we want the resource.
     */
    public static int getSiteSummary(ContentSetting value) {
        switch (value) {
            case ALLOW:
                return R.string.website_settings_permissions_allow;
            case BLOCK:
                return R.string.website_settings_permissions_block;
            default:
                return 0; // We never show Ask as an option on individual permissions.
        }
    }

    /**
     * Returns the summary (resource id) to show when the content type is enabled.
     */
    public static int getEnabledSummary(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
                return R.string.website_settings_category_cookie_allowed;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                return R.string.website_settings_category_ask_before_accessing;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
                return R.string.website_settings_category_allowed_recommended;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_NOTIFICATIONS:
                return R.string.website_settings_category_ask_before_sending;
            default:
                return getCategorySummary(getDefaultEnabledValue(contentType));
        }
    }

    /**
     * Returns the summary (resource id) to show when the content type is disabled.
     */
    public static int getDisabledSummary(int contentType) {
        switch (contentType) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_FULLSCREEN:
                return R.string.website_settings_category_ask_first_recommended;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                return R.string.website_settings_category_blocked_recommended;
            default:
                return getCategorySummary(getDefaultDisabledValue(contentType));
        }
    }

    /**
     * Returns the summary for Geolocation content settings when it is set to 'Allow' (by policy).
     */
    public static int getGeolocationAllowedSummary() {
        return R.string.website_settings_category_allowed;
    }

    /**
     * Returns the summary for Cookie content settings when it is allowed
     * except for those from third party sources.
     */
    public static int getCookieAllowedExceptThirdPartySummary() {
        return R.string.website_settings_category_allowed_except_third_party;
    }
}
