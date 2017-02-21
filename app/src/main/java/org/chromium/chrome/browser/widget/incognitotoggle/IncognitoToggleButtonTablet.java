// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.incognitotoggle;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;

/**
 * A {@link View} that allows a user to toggle between incognito and normal {@link TabModel}s.
 */
public class IncognitoToggleButtonTablet extends ImageButton {
    private TabModelSelector mTabModelSelector;
    private TabModelSelectorObserver mTabModelSelectorObserver;
    private TabModelObserver mTabModelObserver;

    /**
     * Creates an instance of {@link IncognitoToggleButtonTablet}.
     * @param context The {@link Context} to create this {@link View} under.
     * @param attrs An {@link AttributeSet} that contains information on how to build this
     *         {@link View}.
     */
    public IncognitoToggleButtonTablet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        setScaleType(ScaleType.CENTER);
        setVisibility(View.GONE);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTabModelSelector != null) {
                    mTabModelSelector.selectModel(!mTabModelSelector.isIncognitoSelected());
                }
            }
        });
    }

    /**
     * Sets the {@link TabModelSelector} that will be queried for information about the state of
     * the system.
     * @param selector A {@link TabModelSelector} that represents the state of the system.
     */
    public void setTabModelSelector(TabModelSelector selector) {
        mTabModelSelector = selector;
        if (selector != null) {
            updateButtonResource();
            updateButtonVisibility();

            mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
                @Override
                public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                    updateButtonResource();
                }
            };
            mTabModelSelector.addObserver(mTabModelSelectorObserver);

            mTabModelObserver = new EmptyTabModelObserver() {
                @Override
                public void didAddTab(Tab tab, TabLaunchType type) {
                    updateButtonVisibility();
                }

                @Override
                public void willCloseTab(Tab tab, boolean animate) {
                    updateButtonVisibility();
                }

                @Override
                public void tabRemoved(Tab tab) {
                    updateButtonVisibility();
                }
            };
            for (TabModel model : mTabModelSelector.getModels()) {
                model.addObserver(mTabModelObserver);
            }
        }
    }

    private void updateButtonResource() {
        if (mTabModelSelector == null || mTabModelSelector.getCurrentModel() == null) return;

        setContentDescription(getContext().getString(mTabModelSelector.isIncognitoSelected()
                ? R.string.accessibility_tabstrip_btn_incognito_toggle_incognito
                : R.string.accessibility_tabstrip_btn_incognito_toggle_standard));
        setImageResource(mTabModelSelector.isIncognitoSelected()
                ? R.drawable.btn_tabstrip_switch_incognito : R.drawable.btn_tabstrip_switch_normal);
    }

    private void updateButtonVisibility() {
        if (mTabModelSelector == null || mTabModelSelector.getCurrentModel() == null) {
            setVisibility(View.GONE);
            return;
        }

        post(new Runnable() {
            @Override
            public void run() {
                setVisibility(mTabModelSelector.getModel(true).getCount() > 0
                        ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        if (mTabModelSelector != null) {
            mTabModelSelector.addObserver(mTabModelSelectorObserver);
            for (TabModel model : mTabModelSelector.getModels()) {
                model.addObserver(mTabModelObserver);
            }
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mTabModelSelector != null) {
            mTabModelSelector.removeObserver(mTabModelSelectorObserver);
            for (TabModel model : mTabModelSelector.getModels()) {
                model.removeObserver(mTabModelObserver);
            }
        }
        super.onDetachedFromWindow();
    }
}