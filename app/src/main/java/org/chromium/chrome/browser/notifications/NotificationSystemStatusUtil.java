// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility for determining whether the user has disabled all of Chrome's notifications using the
 * system's per-application settings.
 *
 * Enabling developers to show notifications with their own content creates a significant product
 * risk: one spammy notification too many and the user might disable notifications for all of
 * Chrome, which is obviously very bad. While we have a strong focus on providing clear attribution
 * and ways of revoking notifications for a particular website, measuring this is still important.
 */
public class NotificationSystemStatusUtil {
    /** Status codes returned by {@link determineAppNotificationsEnabled}. **/
    public static final int APP_NOTIFICATIONS_STATUS_UNDETERMINABLE = 0;
    public static final int APP_NOTIFICATIONS_STATUS_EXCEPTION = 1;
    public static final int APP_NOTIFICATIONS_STATUS_ENABLED = 2;
    public static final int APP_NOTIFICATIONS_STATUS_DISABLED = 3;

    /** Must be set to the maximum value of the above values, plus one. **/
    public static final int APP_NOTIFICATIONS_STATUS_BOUNDARY = 4;

    /** Method name on the AppOpsManager class to check for a setting's value. **/
    private static final String CHECK_OP_NO_THROW = "checkOpNoThrow";

    /** The POST_NOTIFICATION operation understood by the AppOpsManager. **/
    private static final String OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION";

    /**
     * Determines whether notifications are enabled for the app represented by |context|.
     * Notifications may be disabled because either the user, or a management tool, has explicitly
     * disallowed the Chrome App to display notifications.
     *
     * This check requires Android KitKat or later. Earlier versions will return an INDETERMINABLE
     * status. When an exception occurs, an EXCEPTION status will be returned instead.
     *
     * @param context The context to check of whether it can show notifications.
     * @return One of the APP_NOTIFICATION_STATUS_* constants defined in this class.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    static int determineAppNotificationStatus(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return APP_NOTIFICATIONS_STATUS_UNDETERMINABLE;
        }

        final String packageName = context.getPackageName();
        final int uid = context.getApplicationInfo().uid;
        final AppOpsManager appOpsManager =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        try {
            Class appOpsManagerClass = Class.forName(AppOpsManager.class.getName());

            @SuppressWarnings("unchecked")
            final Method checkOpNoThrowMethod =
                    appOpsManagerClass.getMethod(CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                                                 String.class);

            final Field opPostNotificationField =
                    appOpsManagerClass.getDeclaredField(OP_POST_NOTIFICATION);

            int value = (int) opPostNotificationField.get(Integer.class);
            int status = (int) checkOpNoThrowMethod.invoke(appOpsManager, value, uid, packageName);

            return status == AppOpsManager.MODE_ALLOWED
                    ? APP_NOTIFICATIONS_STATUS_ENABLED : APP_NOTIFICATIONS_STATUS_DISABLED;

        } catch (RuntimeException e) {
        } catch (Exception e) {
            // Silently fail here, since this is just collecting statistics. The histogram includes
            // a count for thrown exceptions, if that proves to be significant we can revisit.
        }

        return APP_NOTIFICATIONS_STATUS_EXCEPTION;
    }
}
