// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.view.accessibility.AccessibilityManager;

import org.chromium.base.PackageUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;

import java.util.List;

/**
 * Exposes information about the current accessibility state
 */
public class AccessibilityUtil {
    // Whether we've already shown an alert that they have an old version of TalkBack running.
    private static boolean sOldTalkBackVersionAlertShown = false;

    // The link to download or update TalkBack from the Play Store.
    private static final String TALKBACK_MARKET_LINK =
            "market://search?q=pname:com.google.android.marvin.talkback";

    // The package name for TalkBack, an Android accessibility service.
    private static final String TALKBACK_PACKAGE_NAME =
            "com.google.android.marvin.talkback";

    // The minimum TalkBack version that we support. This is the version that shipped with
    // KitKat, from fall 2013. Versions older than that should be updated.
    private static final int MIN_TALKBACK_VERSION = 105;

    private AccessibilityUtil() { }

    /**
     * Checks to see that this device has accessibility and touch exploration enabled.
     * @param context A {@link Context} instance.
     * @return        Whether or not accessibility and touch exploration are enabled.
     */
    @CalledByNative
    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
    }

    /**
     * Checks to see if an old version of TalkBack is running that Chrome doesn't support,
     * and if so, shows an alert dialog prompting the user to update the app.
     * @param context A {@link Context} instance.
     * @return        True if the dialog was shown.
     */
    public static boolean showWarningIfOldTalkbackRunning(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;

        boolean isTalkbackRunning = false;
        try {
            List<AccessibilityServiceInfo> services =
                    manager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_SPOKEN);
            for (AccessibilityServiceInfo service : services) {
                if (service.getId().contains(TALKBACK_PACKAGE_NAME)) isTalkbackRunning = true;
            }
        } catch (NullPointerException e) {
            // getEnabledAccessibilityServiceList() can throw an NPE due to a bad
            // AccessibilityService.
        }
        if (!isTalkbackRunning) return false;

        if (PackageUtils.getPackageVersion(context, TALKBACK_PACKAGE_NAME) < MIN_TALKBACK_VERSION
                && !sOldTalkBackVersionAlertShown) {
            showOldTalkbackVersionAlertOnce(context);
            return true;
        }

        return false;
    }

    private static void showOldTalkbackVersionAlertOnce(final Context context) {
        if (sOldTalkBackVersionAlertShown) return;
        sOldTalkBackVersionAlertShown = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.old_talkback_title)
                .setPositiveButton(R.string.update_from_market,
                        new DialogInterface.OnClickListener() {
                        @Override
                            public void onClick(DialogInterface dialog, int id) {
                                Uri marketUri = Uri.parse(TALKBACK_MARKET_LINK);
                                Intent marketIntent = new Intent(
                                        Intent.ACTION_VIEW, marketUri);
                                context.startActivity(marketIntent);
                            }
                        })
                .setNegativeButton(R.string.cancel_talkback_alert,
                        new DialogInterface.OnClickListener() {
                        @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // Do nothing, this alert is only shown once either way.
                            }
                        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
