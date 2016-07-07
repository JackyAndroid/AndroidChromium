// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;

/**
 * A dialog preference for picking a default search engine.
 */
public class SearchEnginePreference extends DialogPreference
        implements SearchEngineAdapter.SelectSearchEngineCallback {

    static final String PREF_SEARCH_ENGINE = "search_engine";

    // The custom search engine adapter for the data to show in the dialog.
    private SearchEngineAdapter mSearchEngineAdapter;

    public SearchEnginePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEnabled(false);
        mSearchEngineAdapter = new SearchEngineAdapter(getContext(), this);
    }

    @VisibleForTesting
    String getValueForTesting() {
        return mSearchEngineAdapter.getValueForTesting();
    }

    @VisibleForTesting
    void setValueForTesting(String value) {
        mSearchEngineAdapter.setValueForTesting(value);
    }

    // DialogPreference:

    /**
     * @see DialogPreference#showDialog
     */
    public void showDialog() {
        super.showDialog(null);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setNegativeButton(null, null)
               .setPositiveButton(R.string.close, null)
               .setSingleChoiceItems(mSearchEngineAdapter, 0, null);
    }

    // SelectSearchEngineAdapter.SelectSearchEngineCallback:

    @Override
    public void currentSearchEngineDetermined(String name) {
        setSummary(name);
        setEnabled(true);
    }

    @Override
    public void onDismissDialog() {
        getDialog().dismiss();
    }
}
