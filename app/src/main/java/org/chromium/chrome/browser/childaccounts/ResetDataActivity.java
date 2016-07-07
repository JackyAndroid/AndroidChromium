// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * An activity that allows whitelisted applications to reset all data in Chrome,
 * as part of the child account setup.
 */
public class ResetDataActivity extends Activity  {

    /**
     * The operation succeeded. Note that this value will only be returned for dry runs, because
     * sucessfully resetting data will kill this process and return
     * {@link Activity#RESULT_CANCELED}.
     */
    private static final int RESULT_OK = Activity.RESULT_OK;

    /**
     * The calling activity is not authorized. This activity is only available to Google-signed
     * applications.
     */
    private static final int RESULT_ERROR_UNAUTHORIZED = Activity.RESULT_FIRST_USER;

    /**
     * Resetting data is not supported.
     */
    private static final int RESULT_ERROR_NOT_SUPPORTED = Activity.RESULT_FIRST_USER + 1;

    /**
     * There was an error resetting data.
     */
    private static final int RESULT_ERROR_COULD_NOT_RESET_DATA = Activity.RESULT_FIRST_USER + 2;

    /**
     * If this is set to true, perform a "dry run", i.e. only check whether there is data to be
     * cleared. This defaults to true, to avoid accidentally resetting data.
     */
    private static final String EXTRA_DRY_RUN = "dry_run";

    /**
     * If a dry run is performed, this key contains a boolean flag that states whether there is data
     * to be cleared.
     */
    private static final String EXTRA_HAS_DATA = "has_data";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!authenticateSender()) {
            returnResult(RESULT_ERROR_UNAUTHORIZED);
            return;
        }

        // If resetting data is not supported, immediately return an error.
        if (!supportsResetData()) {
            returnResult(RESULT_ERROR_NOT_SUPPORTED);
            return;
        }

        boolean dryRun = IntentUtils.safeGetBooleanExtra(getIntent(), EXTRA_DRY_RUN, true);

        if (dryRun) {
            returnHasData(FirstRunStatus.getFirstRunFlowComplete(this));
            return;
        }

        boolean success = resetData();

        // We should only land here if resetting data was not successful, as otherwise the process
        // will be killed.
        assert !success;
        returnResult(RESULT_ERROR_COULD_NOT_RESET_DATA);
    }

    private boolean authenticateSender() {
        return ExternalAuthUtils.getInstance().isGoogleSigned(getPackageManager(),
                getCallingPackage());
    }

    private boolean supportsResetData() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean resetData() {
        assert supportsResetData();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return am.clearApplicationUserData();
    }

    private void returnHasData(boolean hasData) {
        Intent result = new Intent();
        result.putExtra(EXTRA_HAS_DATA, hasData);
        setResult(RESULT_OK, result);
        finish();
    }

    private void returnResult(int resultCode) {
        setResult(resultCode);
        finish();
    }
}
