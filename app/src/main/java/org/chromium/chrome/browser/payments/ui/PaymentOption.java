// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import android.graphics.drawable.Drawable;

import javax.annotation.Nullable;

/**
 * An option that the user can select, e.g., a shipping option, a shipping address, or a payment
 * method.
 */
public class PaymentOption implements Completable {
    protected boolean mIsComplete;
    protected boolean mIsEditable;
    protected String mEditMessage;
    protected String mEditTitle;
    private String mId;
    private Drawable mIcon;
    private String[] mLabels = {null, null, null};
    private boolean mIsValid = true;

    /** See {@link #PaymentOption(String, String, String, String, int)}. */
    public PaymentOption(String id, @Nullable String label, @Nullable String sublabel,
             @Nullable Drawable icon) {
        this(id, label, sublabel, null, icon);
    }

    /**
     * Constructs a payment option.
     *
     * @param id            The identifier.
     * @param label         The label.
     * @param sublabel      The optional sublabel.
     * @param tertiarylabel The optional tertiary label.
     * @param icon          The drawable icon or null.
     */
    public PaymentOption(String id, @Nullable String label, @Nullable String sublabel,
            @Nullable String tertiarylabel, @Nullable Drawable icon) {
        updateIdentifierLabelsAndIcon(id, label, sublabel, tertiarylabel, icon);
    }

    @Override
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * The non-human readable identifier for this option. For example, "standard_shipping" or the
     * GUID of an autofill card.
     */
    public String getIdentifier() {
        return mId;
    }

    /**
     * The message of required edit of this option. For example, "Billing address required" or
     * "Phone number required".
     */
    @Nullable
    public String getEditMessage() {
        return mEditMessage;
    }

    /**
     * The title of required edit of this option. For example, "Add billing address" or "Add phone
     * number".
     */
    @Nullable
    public String getEditTitle() {
        return mEditTitle;
    }

    /**
     * The primary label of this option. For example, “Visa***1234” or "2-day shipping".
     */
    @Nullable public String getLabel() {
        return mLabels[0];
    }

    /**
     * The optional sublabel of this option. For example, “Expiration date: 12/2025”.
     */
    @Nullable public String getSublabel() {
        return mLabels[1];
    }

    /**
     * The optional tertiary label of this option.  For example, "(555) 867-5309".
     */
    @Nullable public String getTertiaryLabel() {
        return mLabels[2];
    }

    /** See {@link #updateIdentifierAndLabels(String, String, String, String)}. */
    protected void updateIdentifierAndLabels(String id, String label, @Nullable String sublabel) {
        updateIdentifierAndLabels(id, label, sublabel, null);
    }

    /** See {@link #updateIdentifierLabelsAndIcon(String, String, String, String, int)}. */
    protected void updateIdentifierAndLabels(
            String id, String label, @Nullable String sublabel, @Nullable String tertiarylabel) {
        mId = id;
        mLabels[0] = label;
        mLabels[1] = sublabel;
        mLabels[2] = tertiarylabel;
    }

    /**
     * Updates the identifier, labels, and icon of this option. Called after the user has
     * edited this option.
     *
     * @param id            The new id to use. Should not be null.
     * @param label         The new label to use. Should not be null.
     * @param sublabel      The new sublabel to use. Can be null.
     * @param tertiarylabel The new tertiary label to use. Can be null.
     * @param icon          The drawable icon or null.
     */
    protected void updateIdentifierLabelsAndIcon(
            String id, String label, @Nullable String sublabel, @Nullable String tertiarylabel,
            @Nullable Drawable icon) {
        updateIdentifierAndLabels(id, label, sublabel, tertiarylabel);
        mIcon = icon;
    }

    /** @return The drawable icon for this payment option. */
    public Drawable getDrawableIcon() {
        return mIcon;
    }

    /**
     * Marks this option as invalid. For example, this can be a shipping address that's not served
     * by the merchant.
     */
    public void setInvalid() {
        mIsValid = false;
    }

    /** @return True if this option is valid. */
    public boolean isValid() {
        return mIsValid;
    }

    /** @return True if this option is editable by users. */
    public boolean isEditable() {
        return mIsEditable;
    }
}
