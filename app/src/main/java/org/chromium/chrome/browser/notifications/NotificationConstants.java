// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

/**
 * Constants used in more than a single Notification class, e.g. intents and extra names.
 */
public class NotificationConstants {
    // These actions have to be synchronized with the receiver defined in AndroidManifest.xml.
    public static final String ACTION_CLICK_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLICK_NOTIFICATION";
    public static final String ACTION_CLOSE_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLOSE_NOTIFICATION";

    /**
     * Name of the Intent extra set by the framework when a notification preferences intent has
     * been triggered from there, which could be one of the setting gears in system UI.
     */
    public static final String EXTRA_NOTIFICATION_TAG = "notification_tag";

    /**
     * Names of the Intent extras used for Intents related to notifications. These intents are set
     * and owned by Chromium.
     */
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_NOTIFICATION_INFO_ORIGIN = "notification_info_origin";
    public static final String EXTRA_NOTIFICATION_INFO_PROFILE_ID = "notification_info_profile_id";
    public static final String EXTRA_NOTIFICATION_INFO_PROFILE_INCOGNITO =
            "notification_info_profile_incognito";
    public static final String EXTRA_NOTIFICATION_INFO_TAG = "notification_info_tag";
    public static final String EXTRA_NOTIFICATION_INFO_ACTION_INDEX =
            "notification_info_action_index";
    public static final String EXTRA_NOTIFICATION_INFO_WEBAPK_PACKAGE =
            "notification_info_webapk_package";

    /**
     * Unique identifier for a single sync notification. Since the notification ID is reused,
     * old notifications will be overwritten.
     */
    public static final int NOTIFICATION_ID_SYNC = 1;
    /**
     * Unique identifier for the "Signed in to Chrome" notification.
     */
    public static final int NOTIFICATION_ID_SIGNED_IN = 2;
    /**
     * Unique identifier for the Physical Web notification.
     */
    public static final int NOTIFICATION_ID_PHYSICAL_WEB = 3;

    /**
     * Separator used to separate the notification origin from additional data such as the
     * developer specified tag.
     */
    public static final String NOTIFICATION_TAG_SEPARATOR = ";";

    /**
     * Key for retrieving the results of user input from notification text action intents.
     */
    static final String KEY_TEXT_REPLY = "key_text_reply";
}
