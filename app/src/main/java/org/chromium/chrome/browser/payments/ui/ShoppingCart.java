// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import java.util.List;

/**
 * The shopping cart contents and total.
 */
public class ShoppingCart {
    private LineItem mTotal;
    private List<LineItem> mContents;

    /**
     * Builds the shopping cart UI data model.
     *
     * @param totalPrice The total price.
     * @param contents The shopping cart contents. The breakdown of the total price. OK to be null.
     */
    public ShoppingCart(LineItem totalPrice, List<LineItem> contents) {
        mTotal = totalPrice;
        mContents = contents;
    }

    /**
     * Returns the total price.
     *
     * @return The total price.
     */
    public LineItem getTotal() {
        return mTotal;
    }

    /**
     * Updates the total price.
     *
     * @param total The total price.
     */
    public void setTotal(LineItem total) {
        mTotal = total;
    }

    /**
     * Returns the shopping cart items.
     *
     * @return The shopping cart items. Can be null.
     */
    public List<LineItem> getContents() {
        return mContents;
    }

    /**
     * Updates the shopping cart items.
     *
     * @param contents The shopping cart items. Can be null.
     */
    public void setContents(List<LineItem> contents) {
        mContents = contents;
    }
}
