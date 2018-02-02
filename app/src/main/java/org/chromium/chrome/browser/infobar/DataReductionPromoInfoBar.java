// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.StrictMode;

import org.chromium.base.CommandLine;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.omaha.VersionNumberGetter;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoUtils;
import org.chromium.content_public.browser.WebContents;
import org.chromium.net.GURLUtils;

import java.net.HttpURLConnection;
import java.sql.Date;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Generates an infobar to promote the data reduction proxy to non-users of it. The infobar contains
 * a message and a button to enable the proxy. The infobar is displayed when it has not been shown
 * previously, the page is HTTP pages, and certain milestone restrictions are met. Once the proxy is
 * enabled from the infobar a confirmation toast message is shown.
 */
public class DataReductionPromoInfoBar extends ConfirmInfoBar {
    private static final String M48_STABLE_RELEASE_DATE = "2016-01-26";
    private static final String ENABLE_INFOBAR_SWITCH = "enable-data-reduction-promo-infobar";
    private static final int NO_INSTALL_TIME = 0;

    private static Bitmap sIcon;
    private static String sTitle;
    private static String sText;
    private static String sPrimaryButtonText;
    private static String sSecondaryButtonText;

    /**
     * Launch the data reduction infobar promo, if it needs to be displayed.
     *
     * @param context An Android context.
     * @param webContents The WebContents of the tab on which the infobar should show.
     * @param url The URL of the page on which the infobar should show.
     * @param isFragmentNavigation Whether the main frame navigation did not cause changes to the
     *            document (for example scrolling to a named anchor PopState).
     * @param statusCode The HTTP status code of the navigation.
     * @return boolean Whether the promo was launched.
     */
    public static boolean maybeLaunchPromoInfoBar(Context context,
            WebContents webContents, String url, boolean isErrorPage, boolean isFragmentNavigation,
            int statusCode) {
        ThreadUtils.assertOnUiThread();
        if (webContents.isIncognito()) return false;
        if (isErrorPage) return false;
        if (isFragmentNavigation) return false;
        if (statusCode != HttpURLConnection.HTTP_OK) return false;
        if (!DataReductionPromoUtils.canShowPromos()) return false;

        // Don't show the infobar promo if neither the first run experience or second run promo has
        // been shown.
        if (!DataReductionPromoUtils.getDisplayedFreOrSecondRunPromo()) return false;

        // Don't show the promo if the user opted out on the first run experience promo.
        if (DataReductionPromoUtils.getOptedOutOnFrePromo()) return false;

        // Don't show the promo if the user has seen this infobar promo before.
        if (DataReductionPromoUtils.getDisplayedInfoBarPromo()) return false;

        // Only show the promo on HTTP pages.
        if (!GURLUtils.getScheme(url).concat("://").equals(UrlConstants.HTTP_SCHEME)) return false;

        int currentMilestone = VersionNumberGetter.getMilestoneFromVersionNumber(
                PrefServiceBridge.getInstance().getAboutVersionStrings().getApplicationVersion());
        String freOrSecondRunVersion =
                DataReductionPromoUtils.getDisplayedFreOrSecondRunPromoVersion();

        // Temporarily allowing disk access. TODO: Fix. See http://crbug.com/577185
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            Calendar releaseDateOfM48Stable = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            releaseDateOfM48Stable.setTime(Date.valueOf(M48_STABLE_RELEASE_DATE));
            long packageInstallTime = getPackageInstallTime(context);

            // The boolean pref that stores whether user opted out on the first run experience was
            // added in M51. If the last promo was shown before M51, then |freOrSecondRunVersion|
            // will be empty. If Chrome was installed after the FRE promo was added in M48 and
            // beforeM51,assume the user opted out from the FRE and don't show the infobar.
            if (freOrSecondRunVersion.isEmpty()
                    && packageInstallTime > releaseDateOfM48Stable.getTimeInMillis()) {
                return false;
            }

            // Only show the promo if the current version is at least two milestones after the last
            // promo was displayed or the command line switch is on. If the last promo was shown
            // before M51 then |freOrSecondRunVersion| will be empty and it is safe to show the
            // infobar promo.
            if (!CommandLine.getInstance().hasSwitch(ENABLE_INFOBAR_SWITCH)
                    && !freOrSecondRunVersion.isEmpty()
                    && currentMilestone < VersionNumberGetter
                            .getMilestoneFromVersionNumber(freOrSecondRunVersion) + 2) {
                return false;
            }

            DataReductionPromoInfoBar.launch(webContents,
                    BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.infobar_chrome),
                    context.getString(R.string.data_reduction_promo_infobar_title),
                    context.getString(R.string.data_reduction_promo_infobar_text),
                    context.getString(R.string.data_reduction_promo_infobar_button),
                    context.getString(R.string.no_thanks));

            return true;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Gets the time at which this app was first installed in milliseconds since January 1, 1970
     * 00:00:00.0 UTC.
     *
     * @param context An Android context.
     * @return The time at which this app was first installed in milliseconds since the epoch or
     *         zero if the time cannot be retrieved.
     */
    private static long getPackageInstallTime(Context context) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            packageInfo = null;
        }
        return packageInfo == null ? NO_INSTALL_TIME : packageInfo.firstInstallTime;
    }

    /**
     * Launch a data reduction proxy {@link InfoBar} with the specified title and link
     * text. Clicking the link will open the specified settings page.
     *
     * @param webContents The {@link WebContents} in which to open the {@link InfoBar}.
     * @param icon Bitmap to use for the {@link InfoBar} icon.
     * @param title The title to display in the {@link InfoBar}.
     * @param text The text to display in the {@link InfoBar}.
     * @param primaryButtonText The text to display on the primary button.
     * @param secondaryButtonText The text to display on the secondary button.
     */
    private static void launch(WebContents webContents,
            Bitmap icon,
            String title,
            String text,
            String primaryButtonText,
            String secondaryButtonText) {
        sTitle = title;
        sText = text;
        sPrimaryButtonText = primaryButtonText;
        sSecondaryButtonText = secondaryButtonText;
        sIcon = icon;
        DataReductionPromoInfoBarDelegate.launch(webContents);
        DataReductionPromoUtils.saveInfoBarPromoDisplayed();
    }

    DataReductionPromoInfoBar() {
        super(0, sIcon, sTitle, null, sPrimaryButtonText, sSecondaryButtonText);
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        InfoBarControlLayout control = layout.addControlLayout();
        control.addDescription(sText);
    }
}
