// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.accessibility;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.widget.accessibility.AccessibilityTabModelAdapter.AccessibilityTabModelAdapterListener;

/**
 * A wrapper around the Android views in the Accessibility tab switcher. This
 * will show two {@link ListView}s, one for each
 * {@link org.chromium.chrome.browser.tabmodel.TabModel} to
 * represent.
 */
public class AccessibilityTabModelWrapper extends LinearLayout {
    private AccessibilityTabModelListView mAccessibilityView;
    private LinearLayout mStackButtonWrapper;
    private ImageButton mStandardButton;
    private ImageButton mIncognitoButton;

    private TabModelSelector mTabModelSelector;
    private TabModelSelectorObserver mTabModelSelectorObserver =
            new EmptyTabModelSelectorObserver() {
        @Override
        public void onChange() {
            getAdapter().notifyDataSetChanged();
        }

        @Override
        public void onNewTabCreated(Tab tab) {
            getAdapter().notifyDataSetChanged();
        }
    };

    // TODO(bauerb): Use View#isAttachedToWindow() as soon as we are guaranteed
    // to run against API version 19.
    private boolean mIsAttachedToWindow;

    private class ButtonOnClickListener implements View.OnClickListener {
        private final boolean mIncognito;

        public ButtonOnClickListener(boolean incognito) {
            mIncognito = incognito;
        }

        @Override
        public void onClick(View v) {
            if (mTabModelSelector != null) {
                if (mIncognito != mTabModelSelector.isIncognitoSelected()) {
                    mTabModelSelector.commitAllTabClosures();
                    mTabModelSelector.selectModel(mIncognito);
                    setStateBasedOnModel();

                    int stackAnnouncementId = mIncognito
                            ? R.string.accessibility_tab_switcher_incognito_stack_selected
                            : R.string.accessibility_tab_switcher_standard_stack_selected;
                    AccessibilityTabModelWrapper.this.announceForAccessibility(
                            getResources().getString(stackAnnouncementId));
                }
            }
        }
    }

    public AccessibilityTabModelWrapper(Context context) {
        super(context);
    }

    public AccessibilityTabModelWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccessibilityTabModelWrapper(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Initialize android views after creation.
     *
     * @param listener A {@link AccessibilityTabModelAdapterListener} to pass tab events back to the
     *                 parent.
     */
    public void setup(AccessibilityTabModelAdapterListener listener) {
        mStackButtonWrapper = (LinearLayout) findViewById(R.id.button_wrapper);

        mStandardButton = (ImageButton) findViewById(R.id.standard_tabs_button);
        mStandardButton.setOnClickListener(new ButtonOnClickListener(false));

        mIncognitoButton = (ImageButton) findViewById(R.id.incognito_tabs_button);
        mIncognitoButton.setOnClickListener(new ButtonOnClickListener(true));

        mAccessibilityView = (AccessibilityTabModelListView) findViewById(R.id.list_view);

        AccessibilityTabModelAdapter adapter = getAdapter();

        adapter.setListener(listener);
    }

    /**
     * @param modelSelector A {@link TabModelSelector} to provide information
     *            about open tabs.
     */
    public void setTabModelSelector(TabModelSelector modelSelector) {
        if (mIsAttachedToWindow) {
            mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        }
        mTabModelSelector = modelSelector;
        if (mIsAttachedToWindow) {
            modelSelector.addObserver(mTabModelSelectorObserver);
        }
        setStateBasedOnModel();
    }

    /**
     * Set the bottom model selector buttons and list view contents based on the
     * TabModelSelector.
     */
    public void setStateBasedOnModel() {
        if (mTabModelSelector == null) return;

        boolean incognitoEnabled =
                mTabModelSelector.getModel(true).getComprehensiveModel().getCount() > 0;

        boolean incognitoSelected = mTabModelSelector.isIncognitoSelected();

        if (incognitoEnabled) {
            mStackButtonWrapper.setVisibility(View.VISIBLE);
        } else {
            mStackButtonWrapper.setVisibility(View.GONE);
        }

        if (incognitoSelected) {
            mIncognitoButton.setBackgroundResource(R.drawable.btn_bg_holo_active);
            mStandardButton.setBackgroundResource(R.drawable.btn_bg_holo);
            mAccessibilityView.setContentDescription(getContext().getString(
                    R.string.accessibility_tab_switcher_incognito_stack));
        } else {
            mIncognitoButton.setBackgroundResource(R.drawable.btn_bg_holo);
            mStandardButton.setBackgroundResource(R.drawable.btn_bg_holo_active);
            mAccessibilityView.setContentDescription(getContext().getString(
                    R.string.accessibility_tab_switcher_standard_stack));
        }

        getAdapter().setTabModel(mTabModelSelector.getModel(incognitoSelected));
    }

    private AccessibilityTabModelAdapter getAdapter() {
        return (AccessibilityTabModelAdapter) mAccessibilityView.getAdapter();
    }

    @Override
    protected void onAttachedToWindow() {
        mTabModelSelector.addObserver(mTabModelSelectorObserver);
        mIsAttachedToWindow = true;
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        mIsAttachedToWindow = false;
        super.onDetachedFromWindow();
    }
}
