// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.DocumentMigrationHelper;
import org.chromium.chrome.browser.document.DocumentUtils;

import java.util.List;

/**
 * A preference to control whether user's tabs are appearing in the tab switcher.
 */
public class DocumentModePreference extends PreferenceFragment {

    private static final String PREF_DOCUMENT_MODE_SWITCH = "document_mode_switch";
    private SwitchPreference mDocumentModeSwitch;
    private DocumentModeManager mDocumentModeManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.document_mode_preferences);
        getActivity().setTitle(R.string.tabs_and_apps_title);

        mDocumentModeManager = DocumentModeManager.getInstance(getActivity());

        mDocumentModeSwitch = (SwitchPreference) findPreference(PREF_DOCUMENT_MODE_SWITCH);

        boolean isdocumentModeEnabled = !mDocumentModeManager.isOptedOutOfDocumentMode();
        /**
         * I want use only document mode, otherwise there is a bug in Android 5.0 what the tab switch button
         * is not work.
         */
        mDocumentModeSwitch.setChecked(false);
        RecordUserAction.record("DocumentActivity_UserOptIn");
        mDocumentModeManager.setOptedOutState(DocumentModeManager.OPTED_OUT_OF_DOCUMENT_MODE);
        mDocumentModeManager.setOptOutCleanUpPending(true);
        DocumentMigrationHelper.migrateTabs(false, getActivity(), isRestartNeeded(false));

//        mDocumentModeSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                if ((boolean) newValue == !mDocumentModeManager.isOptedOutOfDocumentMode()) {
//                    return true;
//                }
//                createOptOutAlertDialog((boolean) newValue).show();
//                return true;
//            }
//        });
    }

    private AlertDialog createOptOutAlertDialog(final boolean optOut) {
        final boolean isSwitchEnabled = !mDocumentModeManager.isOptedOutOfDocumentMode();

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(optOut ? R.string.tabs_and_apps_turn_on_title
                        : R.string.tabs_and_apps_turn_off_title)
                .setMessage(optOut ? R.string.tabs_and_apps_opt_in_confirmation
                        : R.string.tabs_and_apps_opt_out_confirmation)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mDocumentModeSwitch.setChecked(isSwitchEnabled);
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (optOut) {
                            RecordUserAction.record("DocumentActivity_UserOptOut");
                        } else {
                            RecordUserAction.record("DocumentActivity_UserOptIn");
                        }
                        mDocumentModeManager.setOptedOutState(optOut
                                ? DocumentModeManager.OPT_OUT_PROMO_DISMISSED
                                : DocumentModeManager.OPTED_OUT_OF_DOCUMENT_MODE);
                        mDocumentModeManager.setOptOutCleanUpPending(true);
                        DocumentMigrationHelper.migrateTabs(
                                optOut, getActivity(), isRestartNeeded(optOut));
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mDocumentModeSwitch.setChecked(isSwitchEnabled);
                    }
                })
                .create();

        return dialog;
    }

    /**
     * Figure out whether we need to restart the application after the tab migration is complete.
     * We don't need to restart if this is being accessed from FRE and no document activities have
     * been created yet.
     *
     * @param optOut This is true when we are starting out in opted-out mode.
     * @return Whether to restart the application.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean isRestartNeeded(boolean optOut) {
        if (optOut) return true;
        boolean isFromFre = getActivity().getIntent() != null
                && getActivity().getIntent().getBooleanExtra(
                IntentHandler.EXTRA_INVOKED_FROM_FRE, false);
        if (!isFromFre) return true;

        ActivityManager am = (ActivityManager) getActivity().getSystemService(
                Context.ACTIVITY_SERVICE);
        PackageManager pm = getActivity().getPackageManager();
        List<AppTask> taskList = am.getAppTasks();

        for (int i = 0; i < taskList.size(); i++) {
            String className = DocumentUtils.getTaskClassName(taskList.get(i), pm);
            if (className == null) continue;
            if (DocumentActivity.isDocumentActivity(className)) return true;
        }
        return false;
    }

}
