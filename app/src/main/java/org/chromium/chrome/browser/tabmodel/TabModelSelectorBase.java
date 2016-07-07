// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implement methods shared across the different model implementations.
 */
public abstract class TabModelSelectorBase implements TabModelSelector {
    public static final int NORMAL_TAB_MODEL_INDEX = 0;
    public static final int INCOGNITO_TAB_MODEL_INDEX = 1;

    private List<TabModel> mTabModels = Collections.emptyList();
    private int mActiveModelIndex = NORMAL_TAB_MODEL_INDEX;
    private final ObserverList<TabModelSelectorObserver> mObservers =
            new ObserverList<TabModelSelectorObserver>();
    private boolean mTabStateInitialized;

    protected final void initialize(boolean startIncognito, TabModel... models) {
        // Only normal and incognito supported for now.
        assert mTabModels.isEmpty();
        assert models.length > 0;
        if (startIncognito) {
            assert models.length > INCOGNITO_TAB_MODEL_INDEX;
        }

        List<TabModel> tabModels = new ArrayList<TabModel>();
        for (int i = 0; i < models.length; i++) {
            tabModels.add(models[i]);
        }
        mActiveModelIndex = startIncognito ? INCOGNITO_TAB_MODEL_INDEX : NORMAL_TAB_MODEL_INDEX;
        mTabModels = Collections.unmodifiableList(tabModels);

        TabModelObserver tabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didAddTab(Tab tab, TabLaunchType type) {
                notifyChanged();
                notifyNewTabCreated(tab);
            }

            @Override
            public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
                notifyChanged();
            }

            @Override
            public void didMoveTab(Tab tab, int newIndex, int curIndex) {
                notifyChanged();
            }
        };
        for (TabModel model : models) {
            model.addObserver(tabModelObserver);
        }
        notifyChanged();
    }

    @Override
    public void selectModel(boolean incognito) {
        TabModel previousModel = getCurrentModel();
        mActiveModelIndex = incognito ? INCOGNITO_TAB_MODEL_INDEX : NORMAL_TAB_MODEL_INDEX;
        TabModel newModel = getCurrentModel();

        if (previousModel != newModel) {
            for (TabModelSelectorObserver listener : mObservers) {
                listener.onTabModelSelected(newModel, previousModel);
            }
        }
    }

    @Override
    public TabModel getModelAt(int index) {
        assert (index < mTabModels.size() && index >= 0) :
            "requested index " + index + " size " + mTabModels.size();
        return mTabModels.get(index);
    }

    @Override
    public Tab getCurrentTab() {
        return getCurrentModel() == null ? null : TabModelUtils.getCurrentTab(getCurrentModel());
    }

    @Override
    public int getCurrentTabId() {
        Tab tab = getCurrentTab();
        return tab != null ? tab.getId() : Tab.INVALID_TAB_ID;
    }

    @Override
    public TabModel getModelForTabId(int id) {
        for (int i = 0; i < mTabModels.size(); i++) {
            TabModel model = mTabModels.get(i);
            if (TabModelUtils.getTabById(model, id) != null || model.isClosurePending(id)) {
                return model;
            }
        }
        return null;
    }

    @Override
    public TabModel getCurrentModel() {
        return getModelAt(mActiveModelIndex);
    }

    @Override
    public int getCurrentModelIndex() {
        return mActiveModelIndex;
    }

    @Override
    public TabModel getModel(boolean incognito) {
        int index = incognito ? INCOGNITO_TAB_MODEL_INDEX : NORMAL_TAB_MODEL_INDEX;
        return getModelAt(index);
    }

    @Override
    public boolean isIncognitoSelected() {
        return mActiveModelIndex == INCOGNITO_TAB_MODEL_INDEX;
    }

    @Override
    public List<TabModel> getModels() {
        return mTabModels;
    }

    @Override
    public boolean closeTab(Tab tab) {
        for (int i = 0; i < getModels().size(); i++) {
            TabModel model = getModelAt(i);
            if (model.indexOf(tab) >= 0) {
                return model.closeTab(tab);
            }
        }
        assert false : "Tried to close a tab that is not in any model!";
        return false;
    }

    @Override
    public void commitAllTabClosures() {
        for (int i = 0; i < mTabModels.size(); i++) {
            mTabModels.get(i).commitAllTabClosures();
        }
    }

    @Override
    public Tab getTabById(int id) {
        for (int i = 0; i < getModels().size(); i++) {
            Tab tab = TabModelUtils.getTabById(getModelAt(i), id);
            if (tab != null) return tab;
        }
        return null;
    }

    @Override
    public void closeAllTabs() {
        closeAllTabs(false);
    }

    @Override
    public void closeAllTabs(boolean uponExit) {
        for (int i = 0; i < getModels().size(); i++) {
            getModelAt(i).closeAllTabs(!uponExit, uponExit);
        }
    }

    @Override
    public int getTotalTabCount() {
        int count = 0;
        for (int i = 0; i < getModels().size(); i++)  {
            count += getModelAt(i).getCount();
        }
        return count;
    }

    @Override
    public void addObserver(TabModelSelectorObserver observer) {
        if (!mObservers.hasObserver(observer)) mObservers.addObserver(observer);
    }

    @Override
    public void removeObserver(TabModelSelectorObserver observer) {
        mObservers.removeObserver(observer);
    }

    @Override
    public void setCloseAllTabsDelegate(CloseAllTabsDelegate delegate) { }

    /**
     * Marks the task state being initialized and notifies observers.
     */
    protected void markTabStateInitialized() {
        mTabStateInitialized = true;
        for (TabModelSelectorObserver listener : mObservers) listener.onTabStateInitialized();
    }

    @Override
    public boolean isTabStateInitialized() {
        return mTabStateInitialized;
    }

    @Override
    public void destroy() {
        for (int i = 0; i < getModels().size(); i++) getModelAt(i).destroy();
    }

    /**
     * Notifies all the listeners that the {@link TabModelSelector} or its {@link TabModel} has
     * changed.
     */
    protected void notifyChanged() {
        for (TabModelSelectorObserver listener : mObservers) {
            listener.onChange();
        }
    }

    /**
     * Notifies all the listeners that a new tab has been created.
     * @param tab The tab that has been created.
     */
    private void notifyNewTabCreated(Tab tab) {
        for (TabModelSelectorObserver listener : mObservers) {
            listener.onNewTabCreated(tab);
        }
    }
}
