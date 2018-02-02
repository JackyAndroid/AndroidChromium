// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;

/**
 * A proxy for the Android Notification Manager. This allows tests to be written without having to
 * use the real Notification Manager.
 *
 * @see http://developer.android.com/reference/android/app/NotificationManager.html
 */
public interface NotificationManagerProxy {
    void cancel(int id);
    void cancel(String tag, int id);
    void cancelAll();
    void notify(int id, Notification notification);
    void notify(String tag, int id, Notification notification);
}
