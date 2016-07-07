// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;

import javax.annotation.Nullable;

/**
 * Builds a notification using the given inputs.
 */
public interface NotificationBuilder {

    /**
     * Combines all of the options that have been set and returns a new Notification object.
     */
    Notification build();

    /**
     * Sets the title text (first row) of the notification.
     */
    NotificationBuilder setTitle(@Nullable String title);

    /**
     * Sets the body text (second row) of the notification.
     */
    NotificationBuilder setBody(@Nullable String body);

    /**
     * Sets the origin text (bottom row) of the notification.
     */
    NotificationBuilder setOrigin(@Nullable String origin);

    /**
     * Sets the text that is displayed in the status bar when the notification first arrives.
     */
    NotificationBuilder setTicker(@Nullable CharSequence tickerText);

    /**
     * Sets the large icon that is shown in the notification.
     */
    NotificationBuilder setLargeIcon(@Nullable Bitmap icon);

    /**
     * Sets the the small icon that is shown in the notification and in the status bar.
     */
    NotificationBuilder setSmallIcon(int iconId);

    /**
     * Sets the PendingIntent to send when the notification is clicked.
     */
    NotificationBuilder setContentIntent(@Nullable PendingIntent intent);

    /**
     * Sets the PendingIntent to send when the notification is cleared by the user directly from the
     * notification panel.
     */
    NotificationBuilder setDeleteIntent(@Nullable PendingIntent intent);

    /**
     * Adds an action to the notification. Actions are typically displayed as a button adjacent to
     * the notification content.
     */
    NotificationBuilder addAction(
            int iconId, @Nullable CharSequence title, @Nullable PendingIntent intent);

    /**
     * Sets the default notification options that will be used.
     * <p>
     * The value should be one or more of the following fields combined with
     * bitwise-or:
     * {@link Notification#DEFAULT_SOUND}, {@link Notification#DEFAULT_VIBRATE},
     * {@link Notification#DEFAULT_LIGHTS}.
     * <p>
     * For all default values, use {@link Notification#DEFAULT_ALL}.
     */
    NotificationBuilder setDefaults(int defaults);

    /**
     * Sets the vibration pattern to use.
     */
    NotificationBuilder setVibrate(@Nullable long[] pattern);
}
