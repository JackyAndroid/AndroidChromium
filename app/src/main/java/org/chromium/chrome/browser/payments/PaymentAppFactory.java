// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.VisibleForTesting;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds instances of payment apps.
 */
public class PaymentAppFactory {
    /**
     * Can be used to build additional types of payment apps without Chrome knowing about their
     * types.
     */
    private static PaymentAppFactoryAddition sAdditionalFactory;

    /**
     * The interface for additional payment app factories.
     */
    public interface PaymentAppFactoryAddition {
        /**
         * Builds instances of payment apps.
         */
        List<PaymentApp> create(WebContents webContents);
    }

    /**
     * Sets the additional factory that can build instances of payment apps.
     *
     * @param additionalFactory Can build instances of payment apps.
     */
    @VisibleForTesting
    public static void setAdditionalFactory(PaymentAppFactoryAddition additionalFactory) {
        sAdditionalFactory = additionalFactory;
    }

    /**
     * Builds instances of payment apps.
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     */
    public static List<PaymentApp> create(WebContents webContents) {
        List<PaymentApp> result = new ArrayList<>(2);
        result.add(new AutofillPaymentApp(webContents));
        if (sAdditionalFactory != null) result.addAll(sAdditionalFactory.create(webContents));
        return result;
    }
}
