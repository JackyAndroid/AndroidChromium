// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.datausage;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.EmbedContentViewActivity;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.sessions.SessionTabHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;

/**
 * Entry point to manage all UI details for measuring data use within a Tab.
 */
public class DataUseTabUIManager {

    private static final String SHARED_PREF_DATA_USE_DIALOG_OPT_OUT = "data_use_dialog_opt_out";
    private static final String DATA_USE_FIELD_TRIAL = "ExternalDataUseObserver";

    /**
     * Data use started UI snackbar will not be shown if {@link DISABLE_DATA_USE_STARTED_UI_PARAM}
     * fieldtrial parameter is set to {@value DISABLE_DATA_USE_UI_PARAM_VALUE}.
     */
    private static final String DISABLE_DATA_USE_STARTED_UI_PARAM = "disable_data_use_started_ui";

    /**
     * Data use ended UI snackbar/dialog will not be shown if {@link
     * DISABLE_DATA_USE_ENDED_UI_PARAM} fieldtrial parameter is set to
     * {@value DISABLE_DATA_USE_UI_PARAM_VALUE}.
     */
    private static final String DISABLE_DATA_USE_ENDED_UI_PARAM = "disable_data_use_ended_ui";

    /**
     * Data use ended dialog will not be shown if {@link DISABLE_DATA_USE_ENDED_DIALOG_PARAM}
     * fieldtrial parameter is set to {@value DISABLE_DATA_USE_UI_PARAM_VALUE}.
     */
    private static final String DISABLE_DATA_USE_ENDED_DIALOG_PARAM =
            "disable_data_use_ended_dialog";
    private static final String DISABLE_DATA_USE_UI_PARAM_VALUE = "true";

    /**
     * Represents the possible user actions with the data use snackbars and dialog. This must
     * remain in sync with DataUsage.UIAction in tools/metrics/histograms/histograms.xml.
     */
    public static class DataUsageUIAction {
        public static final int STARTED_SNACKBAR_SHOWN = 0;
        public static final int STARTED_SNACKBAR_MORE_CLICKED = 1;
        public static final int ENDED_SNACKBAR_SHOWN = 2;
        public static final int ENDED_SNACKBAR_MORE_CLICKED = 3;
        public static final int DIALOG_SHOWN = 4;
        public static final int DIALOG_CONTINUE_CLICKED = 5;
        public static final int DIALOG_CANCEL_CLICKED = 6;
        public static final int DIALOG_LEARN_MORE_CLICKED = 7;
        public static final int DIALOG_OPTED_OUT = 8;
        public static final int INDEX_BOUNDARY = 9;
    }

    /**
     * Returns true if data use tracking has started within a Tab. When data use tracking has
     * started, returns true only once to signify the started event.
     *
     * @param tab The tab that may have started tracking data use.
     * @return true If data use tracking has indeed started.
     */
    public static boolean checkAndResetDataUseTrackingStarted(Tab tab) {
        return nativeCheckAndResetDataUseTrackingStarted(
                SessionTabHelper.sessionIdForTab(tab.getWebContents()), tab.getProfile());
    }

    /**
     * Notifies that the user clicked "Continue" when the dialog box warning about exiting data use
     * was shown.
     *
     * @param tab The tab on which the dialog box was shown.
     */
    public static void userClickedContinueOnDialogBox(Tab tab) {
        nativeUserClickedContinueOnDialogBox(
                SessionTabHelper.sessionIdForTab(tab.getWebContents()), tab.getProfile());
    }

    /**
     * Returns true if data use tracking is currently active on {@link tab} but will stop if the
     * navigation continues. Should only be called before the navigation starts.
     *
     * @param tab The tab that is being queried for data use tracking.
     * @param pageTransitionType transition type of the navigation
     * @param packageName package name of the app package that started this navigation.
     * @return true If {@link tab} is currently tracked but would stop if the navigation were to
     * continue.
     */
    public static boolean wouldDataUseTrackingEnd(Tab tab, String url, int pageTransitionType) {
        return nativeWouldDataUseTrackingEnd(tab.getWebContents(),
                SessionTabHelper.sessionIdForTab(tab.getWebContents()), url, pageTransitionType,
                tab.getProfile());
    }

    /**
     * Returns true if data use tracking has ended within a Tab. When data use tracking has
     * ended, returns true only once to signify the ended event.
     *
     * @param tab The tab that may have ended tracking data use.
     * @return true If data use tracking has indeed ended.
     */
    public static boolean checkAndResetDataUseTrackingEnded(Tab tab) {
        return nativeCheckAndResetDataUseTrackingEnded(
                SessionTabHelper.sessionIdForTab(tab.getWebContents()), tab.getProfile());
    }

    /**
     * Tells native code that a custom tab is navigating to a url from the given client app package.
     *
     * @param tab The custom tab that is navigating.
     * @param packageName The client app package for the custom tab loading a url.
     * @param url URL that is being loaded in the custom tab.
     */
    public static void onCustomTabInitialNavigation(Tab tab, String packageName, String url) {
        nativeOnCustomTabInitialNavigation(SessionTabHelper.sessionIdForTab(tab.getWebContents()),
                packageName, url, tab.getProfile());
    }

    /**
     * Returns whether a navigation should be paused to show a dialog telling the user that data use
     * tracking has ended within a Tab. If the navigation should be paused, shows a dialog with the
     * option to cancel the navigation or continue.
     *
     * @param activity Current activity.
     * @param tab The tab to see if tracking has ended in.
     * @param url URL that is pending.
     * @param pageTransitionType The type of transition. see
     *            {@link org.chromium.content.browser.PageTransition} for valid values.
     * @param referrerUrl URL for the referrer.
     * @return true If the URL loading should be overriden.
     */
    public static boolean shouldOverrideUrlLoading(Activity activity,
            final Tab tab, final String url, final int pageTransitionType,
            final String referrerUrl) {
        if (shouldShowDataUseEndedUI() && !shouldShowDataUseEndedSnackbar(activity)
                && wouldDataUseTrackingEnd(tab, url, pageTransitionType)) {
            startDataUseDialog(activity, tab, url, pageTransitionType, referrerUrl);
            return true;
        }
        return false;
    }

    /**
     * Shows a dialog with the option to cancel the navigation or continue. Also allows the user to
     * opt out of seeing this dialog again.
     *
     * @param activity Current activity.
     * @param tab The tab loading the url.
     * @param url URL that is pending.
     * @param pageTransitionType The type of transition. see
     *            {@link org.chromium.content.browser.PageTransition} for valid values.
     * @param referrerUrl URL for the referrer.
     */
    private static void startDataUseDialog(final Activity activity, final Tab tab,
            final String url, final int pageTransitionType, final String referrerUrl) {
        View dataUseDialogView = View.inflate(activity, R.layout.data_use_dialog, null);
        final TextView textView = (TextView) dataUseDialogView.findViewById(R.id.data_use_message);
        textView.setText(getDataUseUIString(DataUseUIMessage.DATA_USE_TRACKING_ENDED_MESSAGE));
        final CheckBox checkBox = (CheckBox) dataUseDialogView.findViewById(R.id.data_use_checkbox);
        checkBox.setText(
                getDataUseUIString(DataUseUIMessage.DATA_USE_TRACKING_ENDED_CHECKBOX_MESSAGE));
        View learnMore = dataUseDialogView.findViewById(R.id.learn_more);
        learnMore.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EmbedContentViewActivity.show(activity,
                        getDataUseUIString(DataUseUIMessage.DATA_USE_LEARN_MORE_TITLE),
                        getDataUseUIString(DataUseUIMessage.DATA_USE_LEARN_MORE_LINK_URL));
                recordDataUseUIAction(DataUsageUIAction.DIALOG_LEARN_MORE_CLICKED);
            }
        });
        new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
                .setTitle(getDataUseUIString(DataUseUIMessage.DATA_USE_TRACKING_ENDED_TITLE))
                .setView(dataUseDialogView)
                .setPositiveButton(
                        getDataUseUIString(DataUseUIMessage.DATA_USE_TRACKING_ENDED_CONTINUE),
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setOptedOutOfDataUseDialog(activity, checkBox.isChecked());
                                LoadUrlParams loadUrlParams = new LoadUrlParams(url,
                                        pageTransitionType);
                                if (!TextUtils.isEmpty(referrerUrl)) {
                                    Referrer referrer = new Referrer(referrerUrl,
                                            Referrer.REFERRER_POLICY_ALWAYS);
                                    loadUrlParams.setReferrer(referrer);
                                }
                                tab.loadUrl(loadUrlParams);
                                recordDataUseUIAction(DataUsageUIAction.DIALOG_CONTINUE_CLICKED);
                                userClickedContinueOnDialogBox(tab);
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setOptedOutOfDataUseDialog(activity, checkBox.isChecked());
                                recordDataUseUIAction(DataUsageUIAction.DIALOG_CANCEL_CLICKED);
                            }
                        })
                .show();
        recordDataUseUIAction(DataUsageUIAction.DIALOG_SHOWN);
    }

    /**
     * @return true if the data use tracking started UI (snackbar) should be shown.
     */
    public static boolean shouldShowDataUseStartedUI() {
        // UI should be shown only when field trial is active, not disabled in Finch and in
        // non-roaming-cellular connection.
        return FieldTrialList.trialExists(DATA_USE_FIELD_TRIAL)
                && !DISABLE_DATA_USE_UI_PARAM_VALUE.equals(
                           VariationsAssociatedData.getVariationParamValue(
                                   DATA_USE_FIELD_TRIAL, DISABLE_DATA_USE_STARTED_UI_PARAM))
                && nativeIsNonRoamingCellularConnection();
    }

    /**
     * @return true if the data use tracking ended UI (snackbar or interstitial) should be shown.
     */
    public static boolean shouldShowDataUseEndedUI() {
        // UI should be shown only when field trial is active, not disabled in Finch and in
        // non-roaming-cellular connection.
        return FieldTrialList.trialExists(DATA_USE_FIELD_TRIAL)
                && !DISABLE_DATA_USE_UI_PARAM_VALUE.equals(
                           VariationsAssociatedData.getVariationParamValue(
                                   DATA_USE_FIELD_TRIAL, DISABLE_DATA_USE_ENDED_UI_PARAM))
                && nativeIsNonRoamingCellularConnection();
    }

    /**
     * Returns true if the data use ended snackbar should be shown instead of the dialog. The
     * snackbar will be shown if the user has opted out of seeing the data use ended dialog or if
     * the dialog is diabled by the fieldtrial.
     *
     * @param context An Android context.
     * @return true If the data use ended snackbar should be shown.
     */
    public static boolean shouldShowDataUseEndedSnackbar(Context context) {
        assert shouldShowDataUseEndedUI();
        return ContextUtils.getAppSharedPreferences().getBoolean(
                       SHARED_PREF_DATA_USE_DIALOG_OPT_OUT, false)
                || DISABLE_DATA_USE_UI_PARAM_VALUE.equals(
                           VariationsAssociatedData.getVariationParamValue(
                                   DATA_USE_FIELD_TRIAL, DISABLE_DATA_USE_ENDED_DIALOG_PARAM));
    }

    /**
     * Sets whether the user has opted out of seeing the data use dialog.
     *
     * @param context An Android context.
     * @param optedOut Whether the user has opted out of seeing the data use dialog.
     */
    private static void setOptedOutOfDataUseDialog(Context context, boolean optedOut) {
        ContextUtils.getAppSharedPreferences().edit()
                .putBoolean(SHARED_PREF_DATA_USE_DIALOG_OPT_OUT, optedOut)
                .apply();
        if (optedOut) {
            recordDataUseUIAction(DataUsageUIAction.DIALOG_OPTED_OUT);
        }
    }

    /**
     * Record the DataUsage.UIAction histogram.
     * @param action Action with the data use tracking snackbar or dialog.
     */
    public static void recordDataUseUIAction(int action) {
        assert action >= 0 && action < DataUsageUIAction.INDEX_BOUNDARY;
        RecordHistogram.recordEnumeratedHistogram(
                "DataUsage.UIAction", action,
                DataUsageUIAction.INDEX_BOUNDARY);
    }

    /**
     * Gets native strings which may be overridden by Finch.
     */
    public static String getDataUseUIString(int messageID) {
        assert messageID >= 0 && messageID < DataUseUIMessage.DATA_USE_UI_MESSAGE_MAX;
        return nativeGetDataUseUIString(messageID);
    }

    private static native boolean nativeCheckAndResetDataUseTrackingStarted(
            int tabId, Profile profile);
    private static native boolean nativeCheckAndResetDataUseTrackingEnded(
            int tabId, Profile profile);
    private static native void nativeUserClickedContinueOnDialogBox(int tabId, Profile profile);
    private static native boolean nativeWouldDataUseTrackingEnd(WebContents webContents, int tabId,
            String url, int pageTransitionType, Profile jprofile);
    private static native void nativeOnCustomTabInitialNavigation(int tabID, String packageName,
            String url, Profile profile);
    private static native String nativeGetDataUseUIString(int messageID);
    private static native boolean nativeIsNonRoamingCellularConnection();
}
