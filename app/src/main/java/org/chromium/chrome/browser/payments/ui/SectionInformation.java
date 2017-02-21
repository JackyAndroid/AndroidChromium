// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * The data to show in a single section where the user can select something, e.g., their
 * shipping address or payment method.
 */
public class SectionInformation {
    /**
     * This value indicates that the user has not made a selection in this section.
     */
    public static final int NO_SELECTION = -1;

    /**
     * This value indicates that user selection is invalid in this section.
     */
    public static final int INVALID_SELECTION = -2;

    @PaymentRequestUI.DataType private final int mDataType;
    private ArrayList<PaymentOption> mItems;
    private int mSelectedItem;

    /**
     * Builds an empty section without selection.
     */
    public SectionInformation(@PaymentRequestUI.DataType int sectionType) {
        this(sectionType, null);
    }

    /**
     * Builds a section with a single option, which is selected.
     *
     * @param defaultItem The only item. It is selected by default.
     */
    public SectionInformation(@PaymentRequestUI.DataType int sectionType,
            @Nullable PaymentOption defaultItem) {
        this(sectionType, 0, defaultItem == null ? null : Arrays.asList(defaultItem));
    }

    /**
     * Builds a section.
     *
     * @param sectionType    Type of data being stored.
     * @param selection      The index of the currently selected item.
     * @param itemCollection The items in the section.
     */
    public SectionInformation(@PaymentRequestUI.DataType int sectionType, int selection,
            Collection<? extends PaymentOption> itemCollection) {
        mDataType = sectionType;

        if (itemCollection == null || itemCollection.isEmpty()) {
            mSelectedItem = NO_SELECTION;
            mItems = null;
        } else {
            mSelectedItem = selection;
            mItems = new ArrayList<PaymentOption>(itemCollection.size());
            mItems.addAll(itemCollection);
        }
    }

    /**
     * Returns whether the section is empty.
     *
     * @return Whether the section is empty.
     */
    public boolean isEmpty() {
        return mItems == null || mItems.isEmpty();
    }

    /**
     * Returns the number of items in this section. For example, the number of shipping addresses or
     * payment methods.
     *
     * @return The number of items in this section.
     */
    public int getSize() {
        return mItems == null ? 0 : mItems.size();
    }

    /**
     * Returns the item in the given position.
     *
     * @param position The index of the item to return.
     * @return The item in the given position or null.
     */
    public PaymentOption getItem(int position) {
        if (mItems == null || mItems.isEmpty() || position < 0 || position >= mItems.size()) {
            return null;
        }

        return mItems.get(position);
    }

    /**
     * Sets the currently selected item by index.
     *
     * @param index The index of the currently selected item, NO_SELECTION if a selection has not
     *              yet been made, or INVALID_SELECTION if an invalid selection has been made.
     */
    public void setSelectedItemIndex(int index) {
        mSelectedItem = index;
    }

    /**
     * Sets the currently selected item.
     *
     * @param selectedItem The currently selected item, or null of a selection has not yet been
     *                     made.
     */
    public void setSelectedItem(PaymentOption selectedItem) {
        if (mItems == null) return;
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i) == selectedItem) {
                mSelectedItem = i;
                return;
            }
        }
    }

    /**
     * Returns the index of the selected item.
     *
     * @return The index of the currently selected item, NO_SELECTION if a selection has not yet
     *         been made, or INVALID_SELECTION if an invalid selection has been made.
     */
    public int getSelectedItemIndex() {
        return mSelectedItem;
    }

    /**
     * Returns the selected item, if any.
     *
     * @return The selected item or null if none selected.
     */
    public PaymentOption getSelectedItem() {
        return getItem(getSelectedItemIndex());
    }

    /**
     * Adds the given item at the head of the list and selects it.
     *
     * @param item The item to add.
     */
    public void addAndSelectItem(PaymentOption item) {
        if (mItems == null) mItems = new ArrayList<>();
        mItems.add(0, item);
        mSelectedItem = 0;
    }

    /**
     * Returns the resource ID for the string telling users that they can add a new option.
     *
     * @return ID if the user can add a new option, or 0 if they can't.
     */
    public int getAddStringId() {
        if (mDataType == PaymentRequestUI.TYPE_SHIPPING_ADDRESSES) {
            return R.string.autofill_create_profile;
        } else if (mDataType == PaymentRequestUI.TYPE_CONTACT_DETAILS) {
            return R.string.payments_add_contact;
        } else if (mDataType == PaymentRequestUI.TYPE_PAYMENT_METHODS) {
            return R.string.payments_create_card;
        }
        return 0;
    }
}
