// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import org.chromium.base.metrics.RecordHistogram;

/**
 * Centralizes UMA data collection for the Data Reduction Proxy.
 */
public class DataReductionProxyUma {
    // Represent the possible user actions in the promo and  settings menu. This must
    // remain in sync with DataReductionProxy.UIAction in
    // tools/metrics/histograms/histograms.xml.
    public static final int ACTION_ENABLED = 0;
    // The value of 1 is reserved for an iOS-specific action. Values 2 and 3 are
    // deprecated promo actions.
    public static final int ACTION_DISMISSED = 4;
    public static final int ACTION_OFF_TO_OFF = 5;
    public static final int ACTION_OFF_TO_ON = 6;
    public static final int ACTION_ON_TO_OFF = 7;
    public static final int ACTION_ON_TO_ON = 8;
    public static final int ACTION_FRE_ENABLED = 9;
    public static final int ACTION_FRE_DISABLED = 10;
    public static final int ACTION_INDEX_BOUNDARY = 11;

    // Represent the possible Lo-Fi user actions. This must remain in sync with
    // DataReductionProxy.UIAction.LoFi in tools/metrics/histograms/histograms.xml.
    public static final int ACTION_LOAD_IMAGES_SNACKBAR_SHOWN = 0;
    public static final int ACTION_LOAD_IMAGES_SNACKBAR_CLICKED = 1;
    public static final int ACTION_LOAD_IMAGE_CONTEXT_MENU_SHOWN = 2;
    public static final int ACTION_LOAD_IMAGE_CONTEXT_MENU_CLICKED = 3;
    public static final int ACTION_LOAD_IMAGE_CONTEXT_MENU_CLICKED_ON_PAGE = 4;
    public static final int ACTION_LOAD_IMAGES_CONTEXT_MENU_SHOWN = 5;
    public static final int ACTION_LOAD_IMAGES_CONTEXT_MENU_CLICKED = 6;
    public static final int LOFI_ACTION_INDEX_BOUNDARY = 7;

    /**
     * Record the DataReductionProxy.UIAction histogram.
     * @param action User action at the promo, first run experience, or settings screen
     */
    public static void dataReductionProxyUIAction(int action) {
        assert action >= 0 && action < ACTION_INDEX_BOUNDARY;
        RecordHistogram.recordEnumeratedHistogram(
                "DataReductionProxy.UIAction", action,
                DataReductionProxyUma.ACTION_INDEX_BOUNDARY);
    }

    /**
     * Record the DataReductionProxy.UIAction.LoFi histogram.
     * @param action LoFi user action on the snackbar or context menu
     */
    public static void dataReductionProxyLoFiUIAction(int action) {
        assert action >= 0 && action < LOFI_ACTION_INDEX_BOUNDARY;
        RecordHistogram.recordEnumeratedHistogram(
                "DataReductionProxy.LoFi.UIAction", action,
                DataReductionProxyUma.LOFI_ACTION_INDEX_BOUNDARY);
    }
}