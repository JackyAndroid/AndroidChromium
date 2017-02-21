// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.payments.ui.EditorView;
import org.chromium.chrome.browser.payments.ui.PaymentOption;

import javax.annotation.Nullable;

/**
 * The base class for an editor controller.
 */
abstract class EditorBase<T extends PaymentOption> {
    @Nullable protected EditorView mEditorView;
    @Nullable protected Context mContext;

    /**
     * Sets the user interface to be used for editing contact information.
     *
     * @param editorView The user interface to be used.
     */
    public void setEditorView(EditorView editorView) {
        assert editorView != null;
        mEditorView = editorView;
        mContext = mEditorView.getContext();
    }

    /**
     * Shows the user interface for editing the given information.
     *
     * @param toEdit   The information to edit. Can be null if the user is adding new information
     *                 instead of editing an existing one.
     * @param callback The callback to invoke with the complete and valid information. Can be
     *                 invoked with null if the user clicked Cancel.
     */
    protected void edit(@Nullable T toEdit, Callback<T> callback) {
        assert callback != null;
        assert mEditorView != null;
        assert mContext != null;
    }
}
