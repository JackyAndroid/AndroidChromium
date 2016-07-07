// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;

/**
 * Builds a notification using the given inputs. Relies on NotificationCompat and
 * NotificationCompat.BigTextStyle to provide a standard layout.
 */
public class StandardNotificationBuilder implements NotificationBuilder {
    private final NotificationCompat.Builder mBuilder;

    public StandardNotificationBuilder(Context context) {
        mBuilder = new NotificationCompat.Builder(context);
    }

    @Override
    public Notification build() {
        return mBuilder.build();
    }

    @Override
    public NotificationBuilder setTitle(String title) {
        mBuilder.setContentTitle(title);
        return this;
    }

    @Override
    public NotificationBuilder setBody(String body) {
        mBuilder.setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        return this;
    }

    @Override
    public NotificationBuilder setOrigin(String origin) {
        mBuilder.setSubText(origin);
        return this;
    }

    @Override
    public NotificationBuilder setTicker(CharSequence tickerText) {
        mBuilder.setTicker(tickerText);
        return this;
    }

    @Override
    public NotificationBuilder setLargeIcon(Bitmap icon) {
        mBuilder.setLargeIcon(icon);
        return this;
    }

    @Override
    public NotificationBuilder setSmallIcon(int iconId) {
        mBuilder.setSmallIcon(iconId);
        return this;
    }

    @Override
    public NotificationBuilder setContentIntent(PendingIntent intent) {
        mBuilder.setContentIntent(intent);
        return this;
    }

    @Override
    public NotificationBuilder setDeleteIntent(PendingIntent intent) {
        mBuilder.setDeleteIntent(intent);
        return this;
    }

    @Override
    public NotificationBuilder addAction(int iconId, CharSequence title, PendingIntent intent) {
        mBuilder.addAction(iconId, title, intent);
        return this;
    }

    @Override
    public NotificationBuilder setDefaults(int defaults) {
        mBuilder.setDefaults(defaults);
        return this;
    }

    @Override
    public NotificationBuilder setVibrate(long[] pattern) {
        mBuilder.setVibrate(pattern);
        return this;
    }
}
