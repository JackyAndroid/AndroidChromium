// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.emptybackground;

import android.app.Activity;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewStub;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.appmenu.AppMenuHandler;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;

import java.util.List;

/**
 * Handler for {@link EmptyBackgroundViewTablet}.
 */
public class EmptyBackgroundViewWrapper {
    private final Activity mActivity;
    private final TabModelSelector mTabModelSelector;
    private final TabCreator mTabCreator;
    private final TabModelObserver mTabModelObserver;
    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private final OverviewModeBehavior mOverviewModeBehavior;

    private EmptyBackgroundViewTablet mBackgroundView;
    private final AppMenuHandler mMenuHandler;

    /**
     * Creates a {@link EmptyBackgroundViewWrapper} instance that will lazily inflate.
     * @param selector             A {@link TabModelSelector} that will be used to query system
     *                             state.
     * @param tabCreator           A {@link TabCreator} that will be used to open the New Tab Page.
     * @param activity             An {@link Activity} that represents a parent of the
     *                             {@link android.view.ViewStub}.
     * @param menuHandler          A {@link AppMenuHandler} to handle menu touch events.
     * @param overviewModeBehavior A {@link OverviewModeBehavior} instance to detect when the app
     *                             is in overview mode.
     */
    public EmptyBackgroundViewWrapper(TabModelSelector selector, TabCreator tabCreator,
            Activity activity, AppMenuHandler menuHandler,
            OverviewModeBehavior overviewModeBehavior) {
        mActivity = activity;
        mMenuHandler = menuHandler;
        mTabModelSelector = selector;
        mTabCreator = tabCreator;
        mOverviewModeBehavior = overviewModeBehavior;
        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                updateEmptyContainerState();
            }

            @Override
            public void tabClosureUndone(Tab tab) {
                updateEmptyContainerState();
            }

            @Override
            public void didCloseTab(Tab tab) {
                updateEmptyContainerState();
            }

            @Override
            public void tabPendingClosure(Tab tab) {
                updateEmptyContainerState();
            }

            @Override
            public void allTabsPendingClosure(List<Integer> tabIds) {
                updateEmptyContainerState();
            }
        };
        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                updateEmptyContainerState();
            }
        };
    }

    /**
     * Initialize the wrapper to listen for the proper notifications.
     */
    public void initialize() {
        for (TabModel model : mTabModelSelector.getModels()) model.addObserver(mTabModelObserver);
        mTabModelSelector.addObserver(mTabModelSelectorObserver);
    }

    /**
     * Unregister all dependencies and listeners.
     */
    public void uninitialize() {
        for (TabModel model : mTabModelSelector.getModels()) {
            model.removeObserver(mTabModelObserver);
        }
        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
    }

    private void inflateViewIfNecessary() {
        if (mBackgroundView != null) return;

        mBackgroundView = (EmptyBackgroundViewTablet) ((ViewStub) mActivity.findViewById(
                R.id.empty_container_stub)).inflate();
        mBackgroundView.setTabModelSelector(mTabModelSelector);
        mBackgroundView.setTabCreator(mTabCreator);
        mBackgroundView.setMenuOnTouchListener(mMenuHandler);
        mBackgroundView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewDetachedFromWindow(View v) {
                uninitialize();
            }

            @Override
            public void onViewAttachedToWindow(View v) {
            }
        });
    }

    private void updateEmptyContainerState() {
        boolean showEmptyBackground = shouldShowEmptyContainer();
        if (showEmptyBackground) {
            inflateViewIfNecessary();
        }

        if (mBackgroundView != null) {
            mBackgroundView.setEmptyContainerState(showEmptyBackground);
        }
    }

    private boolean shouldShowEmptyContainer() {
        TabModel model = mTabModelSelector.getModel(false);
        if (model == null) {
            return false;
        }
        boolean isIncognitoEmpty = mTabModelSelector.getModel(true).getCount() == 0;
        boolean incognitoSelected = mTabModelSelector.isIncognitoSelected();

        // Only show the empty container if:
        // 1. There are no tabs in the normal TabModel AND
        // 2. Overview mode is not showing AND
        // 3. We're in the normal TabModel OR there are no tabs present in either model
        return model.getCount() == 0 && !mOverviewModeBehavior.overviewVisible()
                && (!incognitoSelected || isIncognitoEmpty);
    }
}