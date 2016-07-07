// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import org.chromium.base.ThreadUtils;

import java.util.Locale;

/**
 * Tracks opt out status for document mode
 */
public class DocumentModeManager {

    public static final String OPT_OUT_STATE = "opt_out_state";
    public static final int OPT_OUT_PROMO_DISMISSED = 1;
    public static final int OPTED_OUT_OF_DOCUMENT_MODE = 2;
    public static final String OPT_OUT_SHOWN_COUNT = "opt_out_shown_count";
    public static final String OPT_OUT_CLEAN_UP_PENDING = "opt_out_clean_up_pending";

    private static final int OPT_OUT_STATE_UNSET = -1;
    private static final int OPT_IN_TO_DOCUMENT_MODE = 0;

    // Taken from https://support.google.com/googleplay/answer/1727131?hl=en-GB
    private static final String[] DEFAULT_TABBED_MODE_DEVICES = {
            "GT-I9505G", // Galaxy S4 Google Play Edition
            "SC-04E", // Galaxy S4
            "GT-I9500", // Galaxy S4
            "SCH-I959", // Galaxy S4
            "SHV-E300K", // Galaxy S4
            "SHV-E300L", // Galaxy S4
            "SHV-E300S", // Galaxy S4
            "GT-I9505", // Galaxy S4
            "GT-I9508", // Galaxy S4
            "GT-I9508C", // Galaxy S4
            "SAMSUNG-SGH-I337Z", // Galaxy S4
            "SAMSUNG-SGH-I337", // Galaxy S4
            "SGH-I337M", // Galaxy S4
            "SGH-M919V", // Galaxy S4
            "SCH-R970C", // Galaxy S4
            "SCH-R970X", // Galaxy S4
            "SCH-I545L", // Galaxy S4
            "SPH-L720T", // Galaxy S4
            "SPH-L720", // Galaxy S4
            "SM-S975L", // Galaxy S4
            "SGH-S970G", // Galaxy S4
            "SGH-M919", // Galaxy S4
            "SCH-R970", // Galaxy S4
            "SCH-I545", // Galaxy S4
            "GT-I9507", // Galaxy S4
            "GT-I9507V", // Galaxy S4
            "GT-I9515", // Galaxy S4
            "GT-I9515L", // Galaxy S4
            "GT-I9505X", // Galaxy S4
            "GT-I9506", // Galaxy S4
            "SHV-E330K", // Galaxy S4
            "SHV-E330L", // Galaxy S4
            "GT-I9295", // Galaxy S4 Active
            "SAMSUNG-SGH-I537", // Galaxy S4 Active
            "SGH-I537", // Galaxy S4 Active
            "SHV-E470S", // Galaxy S4 Active
            "GT-I9502", // Galaxy S4 Duos
            "SHV-E330S", // Galaxy S4 LTE-A
            "GT-I9190", // Galaxy S4 Mini
            "GT-I9192", // Galaxy S4 Mini
            "GT-I9195", // Galaxy S4 Mini
            "GT-I9195L", // Galaxy S4 Mini
            "GT-I9195T", // Galaxy S4 Mini
            "GT-I9195X", // Galaxy S4 Mini
            "GT-I9197", // Galaxy S4 Mini
            "SGH-I257M", // Galaxy S4 Mini
            "SHV-E370K", // Galaxy S4 Mini
            "SHV-E370D", // Galaxy S4 Mini
            "SCH-I435L", // Galaxy S4 Mini
            "SPH-L520", // Galaxy S4 Mini
            "SCH-R890", // Galaxy S4 Mini
            "SCH-I435", // Galaxy S4 Mini
            "GT-I9192I", // Galaxy S4 Mini
            "GT-I9195I", // Galaxy S4 Mini
            "SAMSUNG-SGH-I257", // Galaxy S4 Mini
            "SM-C101", // Galaxy S4 Zoom
            "SAMSUNG-SM-C105A", // Galaxy S4 Zoom
            "SM-C105L", // Galaxy S4 Zoom
            "SM-C105S", // Galaxy S4 Zoom
            "SM-C105", // Galaxy S4 Zoom
            "SC-02E", // Galaxy Note2
            "GT-N7100", // Galaxy Note2
            "GT-N7100T", // Galaxy Note2
            "GT-N7100", // Galaxy Note2
            "GT-N7102", // Galaxy Note2
            "GT-N7102i", // Galaxy Note2
            "GT-N7108", // Galaxy Note2
            "SCH-N719", // Galaxy Note2
            "GT-N7102", // Galaxy Note2
            "GT-N7102i", // Galaxy Note2
            "GT-N7105", // Galaxy Note2
            "GT-N7105T", // Galaxy Note2
            "SAMSUNG-SGH-I317", // Galaxy Note2
            "SGH-I317M", // Galaxy Note2
            "SGH-T889V", // Galaxy Note2
            "GT-N7108D", // Galaxy Note2
            "SC-02E", // Galaxy Note2
            "SHV-E250K", // Galaxy Note2
            "SHV-E250L", // Galaxy Note2
            "SHV-E250S", // Galaxy Note2
            "SPH-L900", // Galaxy Note2
            "SGH-T889", // Galaxy Note2
            "SCH-R950", // Galaxy Note2
            "SCH-I605", // Galaxy Note2
            "SAMSUNG-SGH-I317", // Galaxy Note2
            "SC-02F", // Galaxy Note3
            "SCL22", // Galaxy Note3
            "SM-N900", // Galaxy Note3
            "SM-N9000Q", // Galaxy Note3
            "SM-N9005", // Galaxy Note3
            "SM-N9006", // Galaxy Note3
            "SM-N9007", // Galaxy Note3
            "SM-N9008V", // Galaxy Note3
            "SM-N9009", // Galaxy Note3
            "SM-N900U", // Galaxy Note3
            "SAMSUNG-SM-N900A", // Galaxy Note3
            "SM-N900W8", // Galaxy Note3
            "SM-N900K", // Galaxy Note3
            "SM-N900L", // Galaxy Note3
            "SM-N900S", // Galaxy Note3
            "SM-N900P", // Galaxy Note3
            "SM-N900T", // Galaxy Note3
            "SM-N900R4", // Galaxy Note3
            "SM-N900V", // Galaxy Note3
            "SM-N9007", // Galaxy Note3
            "SM-N9002", // Galaxy Note3 Duos
            "SM-N9008", // Galaxy Note3 Duos
            "SM-N750K", // Galaxy Note3 Neo
            "SM-N750L", // Galaxy Note3 Neo
            "SM-N750S", // Galaxy Note3 Neo
            "SM-N750", // Galaxy Note3 Neo
            "SM-N7500Q", // Galaxy Note3 Neo
            "SM-N7502", // Galaxy Note3 Neo
            "SM-N7505", // Galaxy Note3 Neo
            "SM-N7505L", // Galaxy Note3 Neo
            "SM-N7507", // Galaxy Note3 Neo
    };

    private static DocumentModeManager sManager;

    private final SharedPreferences mSharedPreferences;

    private DocumentModeManager(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Get the static instance of DocumentModeManager if it exists, else create it.
     * @param context The current Android context
     * @return the DocumentModeManager singleton
     */
    public static DocumentModeManager getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sManager == null) {
            sManager = new DocumentModeManager(context);
        }
        return sManager;
    }

    /**
     * @return Whether the user set a preference to not use the document mode.
     */
    public boolean isOptedOutOfDocumentMode() {
        return getOptOutState() == OPTED_OUT_OF_DOCUMENT_MODE;
    }

    /**
     * @return Whether the user dismissed the opt out promo.
     */
    public boolean isOptOutPromoDismissed() {
        return getOptOutState() == OPT_OUT_PROMO_DISMISSED;
    }

    /**
     * Sets the opt out preference.
     * @param state One of OPTED_OUT_OF_DOCUMENT_MODE or OPT_OUT_PROMO_DISMISSED.
     */
    public void setOptedOutState(int state) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(OPT_OUT_STATE, state);
        sharedPreferencesEditor.apply();
    }

    /**
     * Increments a preference that keeps track of how many times the opt out message has been
     * shown on home screen.
     */
    public void incrementOptOutShownCount() {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putLong(OPT_OUT_SHOWN_COUNT, getOptOutShownCount() + 1);
        sharedPreferencesEditor.apply();
    }

    /**
     * @return The number of times the opt out message has been shown so far.
     */
    public long getOptOutShownCount() {
        return mSharedPreferences.getLong(OPT_OUT_SHOWN_COUNT, 0);
    }

    /**
     * @return Whether we need to clean up old document activity tasks from Recents.
     */
    public boolean isOptOutCleanUpPending() {
        return mSharedPreferences.getBoolean(OPT_OUT_CLEAN_UP_PENDING, false);
    }

    /**
     * Mark that we need to clean up old documents from Recents or reset it after the task
     * is done.
     * @param pending Whether we need to clean up.
     */
    public void setOptOutCleanUpPending(boolean pending) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(OPT_OUT_CLEAN_UP_PENDING, pending);
        sharedPreferencesEditor.apply();
    }

    /**
     * @return Whether Chrome should default to Tabbed mode despite Document mode being supported
     *         at the platform level.  A device will default to Tabbed mode if accessing the
     *         platform Overview screen is deemed too difficult to make Document mode user friendly.
     */
    public static boolean isDeviceTabbedModeByDefault() {
        String model = Build.MODEL.toUpperCase(Locale.US);
        for (String device : DEFAULT_TABBED_MODE_DEVICES) {
            if (model.contains(device)) return true;
        }
        return false;
    }

    private int getOptOutState() {
        int optOutState = mSharedPreferences.getInt(OPT_OUT_STATE, OPT_OUT_STATE_UNSET);
        if (optOutState == OPT_OUT_STATE_UNSET) {
            boolean hasMigrated = mSharedPreferences.getBoolean(
                    ChromePreferenceManager.MIGRATION_ON_UPGRADE_ATTEMPTED, false);
            if (isDeviceTabbedModeByDefault() && !hasMigrated) {
                optOutState = OPTED_OUT_OF_DOCUMENT_MODE;
            } else {
                optOutState = OPT_IN_TO_DOCUMENT_MODE;
            }
            optOutState = OPTED_OUT_OF_DOCUMENT_MODE;
            setOptedOutState(optOutState);
        }
        return optOutState;
    }
}
