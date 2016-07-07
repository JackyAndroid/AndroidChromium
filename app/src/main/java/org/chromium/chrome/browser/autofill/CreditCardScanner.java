// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.content.Context;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.base.WindowAndroid;

/**
 * Helper for detecting whether the device supports scanning credit cards and for scanning credit
 * cards. The default implementation cannot scan cards. An implementing subclass must provide a
 * factory that builds its instances.
 */
@JNINamespace("autofill")
public class CreditCardScanner {
    /**
     * Can be used to build subclasses of the scanner without the user of the class knowing about
     * the subclass name.
     */
    static Factory sFactory;

    /**
     * Pointer to the native object that receives scanning callbacks.
     */
    protected long mNativeScanner;

    /**
     * Application context.
     */
    protected Context mContext;

    /**
     * The window that's requesting a scan.
     */
    protected WindowAndroid mWindow;

    /**
     * Builds instances of credit card scanners.
     */
    public interface Factory {
        /**
         * Builds an instance of credit card scanner.
         * @param nativeScanner Pointer to the native object that receives scanning callbacks.
         * @param context Application context.
         * @param window The window that's requesting a scan.
         * @return An object that can scan a credit card.
         */
        CreditCardScanner create(long nativeScanner, Context context, WindowAndroid window);
    }

    /**
     * Sets the factory that can build instances of credit card scanners.
     * @param factory Can build instances of credit card scanners.
     */
    public static void setFactory(Factory factory) {
        sFactory = factory;
    }

    /**
     * Called by the native object to create an instance of a credit card scanner.
     * @param nativeScanner Pointer to the native object that receives scanning callbacks.
     * @param context Application context.
     * @param window The window that's requesting a scan.
     * @return An object that can scan a credit card.
     */
    @CalledByNative
    private static CreditCardScanner create(long nativeScanner, Context context,
            WindowAndroid window) {
        return sFactory != null ? sFactory.create(nativeScanner, context, window)
                                : new CreditCardScanner(nativeScanner, context, window);
    }

    /**
     * Constructor for the credit card scanner.
     * @param nativeScanner Pointer to the native object that receives scanning callbacks.
     * @param context Application context.
     * @param window The window that's requesting a scan.
     */
    protected CreditCardScanner(long nativeScanner, Context context, WindowAndroid window) {
        mNativeScanner = nativeScanner;
        mContext = context;
        mWindow = window;
    }

    /**
     * Returns true if this instance has the ability to scan credit cards.
     * @return True if has ability to scan credit cards.
     */
    @CalledByNative
    protected boolean canScan() {
        return false;
    }

    /**
     * Scans a credit card. Will invoke a native callback with the result.
     */
    @CalledByNative
    protected void scan() {
        nativeScanCancelled(mNativeScanner);
    }

    /**
     * Notifies the native object that scanning was cancelled.
     * @param nativeCreditCardScannerViewAndroid Pointer to the native object.
     */
    protected native void nativeScanCancelled(long nativeCreditCardScannerViewAndroid);

    /**
     * Notifies the native object that scanning was successful.
     * @param nativeCreditCardScannerViewAndroid Pointer to the native object.
     * @param cardNumber Credit card number.
     * @param expirationMonth Expiration month in the range [1, 12].
     * @param expirationYear Expiration year, e.g. 2000.
     */
    protected native void nativeScanCompleted(long nativeCreditCardScannerViewAndroid,
            String cardNumber, int expirationMonth, int expirationYear);
}
