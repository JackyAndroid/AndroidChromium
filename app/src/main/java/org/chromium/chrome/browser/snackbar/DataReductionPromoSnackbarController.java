// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.content.Context;
import android.content.Intent;

import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPreferences;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoUtils;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionProxyUma;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * The controller for the Data Reduction Proxy promo that lets users of the proxy know when Chrome
 * has saved a given amount of data.
 */
public class DataReductionPromoSnackbarController implements SnackbarManager.SnackbarController {
    /**
     * A semi-colon delimited list of data savings values in MB that the promo should be shown
     * for.
     */
    public static final String PROMO_PARAM_NAME = "snackbar_promo_data_savings_in_megabytes";

    public static final String FROM_PROMO = "FromPromo";
    public static final String PROMO_FIELD_TRIAL_NAME = "DataCompressionProxyPromoVisibility";
    private static final String ENABLE_DATA_REDUCTION_PROXY_SAVINGS_PROMO_SWITCH =
            "enable-data-reduction-proxy-savings-promo";
    private static final String CLEAR_DATA_REDUCTION_PROXY_DATA_SAVINGS_SWITCH =
            "clear-data-reduction-proxy-data-savings";
    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;
    private static final long BYTES_PER_GIGABYTE = 1024 * 1024 * 1024;

    private final SnackbarManager mSnackbarManager;
    private final Context mContext;
    private final int[] mPromoDataSavingsMB;

    /**
     * Creates an instance of a {@link DataReductionPromoSnackbarController}.
     *
     * @param context The {@link Context} in which snackbar is shown.
     * @param snackbarManager The manager that helps to show the snackbar.
     */
    public DataReductionPromoSnackbarController(Context context, SnackbarManager snackbarManager) {
        mSnackbarManager = snackbarManager;
        mContext = context;

        String variationParamValue = VariationsAssociatedData
                .getVariationParamValue(PROMO_FIELD_TRIAL_NAME, PROMO_PARAM_NAME);

        if (variationParamValue.isEmpty()) {
            if (CommandLine.getInstance()
                    .hasSwitch(ENABLE_DATA_REDUCTION_PROXY_SAVINGS_PROMO_SWITCH)) {
                mPromoDataSavingsMB = new int[1];
                mPromoDataSavingsMB[0] = 1;
            } else {
                mPromoDataSavingsMB = new int[0];
            }
        } else {
            variationParamValue = variationParamValue.replace(" ", "");
            String[] promoDataSavingStrings = variationParamValue.split(";");

            if (CommandLine.getInstance()
                    .hasSwitch(ENABLE_DATA_REDUCTION_PROXY_SAVINGS_PROMO_SWITCH)) {
                mPromoDataSavingsMB = new int[promoDataSavingStrings.length + 1];
                mPromoDataSavingsMB[promoDataSavingStrings.length] = 1;
            } else {
                mPromoDataSavingsMB = new int[promoDataSavingStrings.length];
            }

            for (int i = 0; i < promoDataSavingStrings.length; i++) {
                try {
                    mPromoDataSavingsMB[i] = Integer.parseInt(promoDataSavingStrings[i]);
                } catch (NumberFormatException e) {
                    mPromoDataSavingsMB[i] = -1;
                }
            }
        }

        if (CommandLine.getInstance().hasSwitch(CLEAR_DATA_REDUCTION_PROXY_DATA_SAVINGS_SWITCH)) {
            DataReductionPromoUtils.saveSnackbarPromoDisplayed(0);
        }
    }

    /**
     * Shows the Data Reduction Proxy promo snackbar if the current data savings are over
     * specific thresholds set by finch and the snackbar has not been shown for that
     *
     * @param dataSavingsInBytes The amount of data the Data Reduction Proxy has saved in bytes.
     */
    public void maybeShowDataReductionPromoSnackbar(long dataSavingsInBytes) {
        // Prevents users who upgrade and have already saved mPromoDataSavingsInMB from seeing the
        // promo.
        if (!DataReductionPromoUtils.hasSnackbarPromoBeenInitWithStartingSavedBytes()) {
            DataReductionPromoUtils.saveSnackbarPromoDisplayed(dataSavingsInBytes);
            return;
        }

        for (int promoDataSavingsMB : mPromoDataSavingsMB) {
            long promoDataSavingsBytes = promoDataSavingsMB * BYTES_PER_MEGABYTE;
            if (promoDataSavingsMB > 0 && dataSavingsInBytes >= promoDataSavingsBytes
                    && DataReductionPromoUtils
                            .getDisplayedSnackbarPromoSavedBytes() < promoDataSavingsBytes) {
                mSnackbarManager.showSnackbar(Snackbar
                        .make(getStringForBytes(promoDataSavingsBytes),
                                this,
                                Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DATA_REDUCTION_PROMO)
                        .setAction(
                                mContext.getString(R.string.data_reduction_promo_snackbar_button),
                                null));
                DataReductionProxyUma.dataReductionProxySnackbarPromo(promoDataSavingsMB);
                DataReductionPromoUtils.saveSnackbarPromoDisplayed(dataSavingsInBytes);
                break;
            }
        }
    }

    private String getStringForBytes(long bytes) {
        int resourceId;
        int bytesInCorrectUnits;

        if (bytes < BYTES_PER_GIGABYTE) {
            resourceId =  R.string.data_reduction_promo_snackbar_text_mb;
            bytesInCorrectUnits = (int) (bytes / BYTES_PER_MEGABYTE);
        } else {
            resourceId =  R.string.data_reduction_promo_snackbar_text_gb;
            bytesInCorrectUnits = (int) (bytes / BYTES_PER_GIGABYTE);
        }

        return mContext.getResources().getString(resourceId, bytesInCorrectUnits);
    }

    @Override
    public void onAction(Object actionData) {
        assert mContext != null;
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                mContext, DataReductionPreferences.class.getName());
        intent.putExtra(FROM_PROMO, true);
        mContext.startActivity(intent);
    }

    @Override
    public void onDismissNoAction(Object actionData) {
        DataReductionProxyUma.dataReductionProxyUIAction(
                DataReductionProxyUma.ACTION_SNACKBAR_DISMISSED);
    }
}
