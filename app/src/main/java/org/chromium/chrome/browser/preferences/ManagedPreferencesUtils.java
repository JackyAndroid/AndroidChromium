// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;

import org.chromium.chrome.R;
import org.chromium.ui.widget.Toast;

/**
 * Utilities and common methods to handle settings managed by policies.
 */
public class ManagedPreferencesUtils {

    /**
     * Shows a toast indicating that the previous action is managed by the system administrator.
     *
     * This is usually used to explain to the user why a given control is disabled in the settings.
     *
     * @param context The context where the Toast will be shown.
     */
    public static void showManagedByAdministratorToast(Context context) {
        Toast.makeText(context, context.getString(R.string.managed_by_your_administrator),
                Toast.LENGTH_LONG).show();
    }
    /**
     * Shows a toast indicating that the previous action is managed by the parent(s) of the
     * supervised user.
     * This is usually used to explain to the user why a given control is disabled in the settings.
     *
     * @param context The context where the Toast will be shown.
     */
    public static void showManagedByParentToast(Context context) {
        boolean singleParentIsManager =
                PrefServiceBridge.getInstance().getSupervisedUserSecondCustodianName().isEmpty();
        Toast.makeText(context, context.getString(singleParentIsManager
                ? R.string.managed_by_your_parent : R.string.managed_by_your_parents),
                Toast.LENGTH_LONG).show();
    }

    /**
     * @return the resource ID for the Managed By Enterprise icon.
     */
    public static int getManagedByEnterpriseIconId() {
        return R.drawable.controlled_setting_mandatory;
    }
}
