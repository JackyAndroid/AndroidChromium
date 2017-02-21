// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.ListView;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;

/**
* A preference fragment for selecting a default search engine.
*/
public class SearchEnginePreference extends PreferenceFragment
        implements View.OnClickListener, SearchEngineAdapter.SelectSearchEngineCallback,
                   OnLayoutChangeListener {
    private ListView mListView;
    private View mCancelButton;
    private View mSaveButton;
    private View mDivider;

    private SearchEngineAdapter mSearchEngineAdapter;
    private int mSelectedIndex;

    @VisibleForTesting
    String getValueForTesting() {
        return mSearchEngineAdapter.getValueForTesting();
    }

    @VisibleForTesting
    void setValueForTesting(String value) {
        mSearchEngineAdapter.setValueForTesting(value);
        TemplateUrlService.getInstance().setSearchEngine(mSelectedIndex);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_search_engine);
        mSearchEngineAdapter = new SearchEngineAdapter(getActivity(), this);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.search_engine_layout, container, false);
        mListView = (ListView) view.findViewById(android.R.id.list);
        mListView.setAdapter(mSearchEngineAdapter);
        mListView.setDivider(null);
        mListView.addOnLayoutChangeListener(this);
        mCancelButton = view.findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(this);
        mSaveButton = view.findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(this);
        mDivider = view.findViewById(R.id.bottom_shadow);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onClick(View v) {
        if (v == mCancelButton) {
            getActivity().finish();
        } else if (v == mSaveButton) {
            TemplateUrlService.getInstance().setSearchEngine(mSelectedIndex);
            // If the user has manually set the default search engine, disable auto switching.
            boolean manualSwitch = mSelectedIndex != mSearchEngineAdapter
                    .getInitialSearchEnginePosition();
            if (manualSwitch) {
                RecordUserAction.record("SearchEngine_ManualChange");
                LocaleManager.getInstance().setSearchEngineAutoSwitch(false);
            }
            getActivity().finish();
        }
    }

    @Override
    public void currentSearchEngineDetermined(int selectedIndex) {
        mSelectedIndex = selectedIndex;
    }

    /**
     * Displays the divider if the Listview is longer than its viewport.
     */
    public void updateBottombarDivider() {
        if (mListView.getLastVisiblePosition() == mSearchEngineAdapter.getCount() - 1) {
            mDivider.setVisibility(View.INVISIBLE);
        } else {
            mDivider.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        if (v == mListView) {
            updateBottombarDivider();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        /**
         * Handle UI update when location setting for a search engine is changed.
         */
        mSearchEngineAdapter.notifyDataSetChanged();
    }
}
