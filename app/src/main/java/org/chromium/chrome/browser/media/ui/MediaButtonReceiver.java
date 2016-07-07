// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * MediaButtonReceiver is a basic BroadcastReceiver class that receives
 * ACTION_MEDIA_BUTTON from a MediaSessionCompat. It then forward these intents
 * to the service listening to them.
 * This is there for backward compatibility with JB_MR0 and JB_MR1.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String LISTENER_SERVICE_CLASS_NAME =
            "org.chromium.chrome.browser.media.ui"
            + "MediaNotificationManager$ListenerService";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent queryIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        queryIntent.setPackage(context.getPackageName());

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentServices(queryIntent, 0);
        assert infos.size() == 1;

        ResolveInfo info = infos.get(0);
        ComponentName component = new ComponentName(info.serviceInfo.packageName,
                info.serviceInfo.name);
        assert LISTENER_SERVICE_CLASS_NAME.equals(component.getClassName());

        intent.setComponent(component);
        context.startService(intent);
    }
}
