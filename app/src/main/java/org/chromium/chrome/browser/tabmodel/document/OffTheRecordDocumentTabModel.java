// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import android.content.Intent;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.document.IncognitoNotificationManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModel;
import org.chromium.chrome.browser.tabmodel.OffTheRecordTabModel;
import org.chromium.chrome.browser.tabmodel.TabModel;

/**
 * Implements an OffTheRecord version of the DocumentTabModel.  Timing is a little bit different for
 * profile deletion because we don't get all the signals we'd expect when Tabs are closed.  More
 * specifically, Android doesn't fire signals when tasks are swiped away from the Recents menu if
 * the Activity is dead when it occurs.
 */
public class OffTheRecordDocumentTabModel extends OffTheRecordTabModel implements DocumentTabModel {
    private final ActivityDelegate mActivityDelegate;

    public OffTheRecordDocumentTabModel(OffTheRecordTabModelDelegate offTheRecordDelegate,
            ActivityDelegate delegate) {
        super(offTheRecordDelegate);
        mActivityDelegate = delegate;
        if (delegate.getTasksFromRecents(true).size() > 0) {
            ensureTabModelImpl();
        }
    }

    @VisibleForTesting
    public boolean isDocumentTabModelImplCreated() {
        return !(getDelegateModel() instanceof EmptyTabModel);
    }

    @Override
    protected void destroyIncognitoIfNecessary() {
        super.destroyIncognitoIfNecessary();
        if (!mActivityDelegate.isIncognitoDocumentAccessibleToUser()) {
            IncognitoNotificationManager.dismissIncognitoNotification();
        }
    }

    private DocumentTabModel getDelegateDocumentTabModel() {
        TabModel delegate = getDelegateModel();
        return isDocumentTabModelImplCreated() ? (DocumentTabModel) delegate : null;
    }

    @Override
    public void initializeNative() {
        if (!isDocumentTabModelImplCreated()) return;
        getDelegateDocumentTabModel().initializeNative();
    }

    @Override
    public TabState getTabStateForDocument(int tabId) {
        if (!isDocumentTabModelImplCreated()) return null;
        return getDelegateDocumentTabModel().getTabStateForDocument(tabId);
    }

    @Override
    public boolean isRetargetable(int tabId) {
        if (!isDocumentTabModelImplCreated()) return false;
        return getDelegateDocumentTabModel().isRetargetable(tabId);
    }

    @Override
    public void updateRecentlyClosed() {
        if (isDocumentTabModelImplCreated()) getDelegateDocumentTabModel().updateRecentlyClosed();
        destroyIncognitoIfNecessary();
    }

    @Override
    public boolean hasEntryForTabId(int tabId) {
        if (!isDocumentTabModelImplCreated()) return false;
        return getDelegateDocumentTabModel().hasEntryForTabId(tabId);
    }

    @Override
    public void updateEntry(Intent intent, Tab tab) {
        if (!isDocumentTabModelImplCreated()) return;
        getDelegateDocumentTabModel().updateEntry(intent, tab);
    }

    @Override
    public String getCurrentUrlForDocument(int tabId) {
        if (!isDocumentTabModelImplCreated()) return null;
        return getDelegateDocumentTabModel().getCurrentUrlForDocument(tabId);
    }

    @Override
    public boolean isTabStateReady(int tabId) {
        if (!isDocumentTabModelImplCreated()) return false;
        return getDelegateDocumentTabModel().isTabStateReady(tabId);
    }

    @Override
    public String getInitialUrlForDocument(int tabId) {
        if (!isDocumentTabModelImplCreated()) return null;
        return getDelegateDocumentTabModel().getInitialUrlForDocument(tabId);
    }

    @Override
    public void addTab(Intent intent, Tab tab) {
        ensureTabModelImpl();
        getDelegateDocumentTabModel().addTab(intent, tab);
    }

    @Override
    public boolean closeTabAt(int index) {
        boolean success = false;
        if (isDocumentTabModelImplCreated()) {
            success = getDelegateDocumentTabModel().closeTabAt(index);
        }
        destroyIncognitoIfNecessary();
        return success;
    }

    @Override
    public int getCurrentInitializationStage() {
        if (!isDocumentTabModelImplCreated()) return DocumentTabModelImpl.STATE_UNINITIALIZED;
        return getDelegateDocumentTabModel().getCurrentInitializationStage();
    }

    @Override
    public boolean isNativeInitialized() {
        if (!isDocumentTabModelImplCreated()) return false;
        return getDelegateDocumentTabModel().isNativeInitialized();
    }

    @Override
    public void addInitializationObserver(InitializationObserver observer) {
        ensureTabModelImpl();
        getDelegateDocumentTabModel().addInitializationObserver(observer);
    }

    @Override
    public boolean setLastShownId(int id) {
        if (!isDocumentTabModelImplCreated()) return false;
        return getDelegateDocumentTabModel().setLastShownId(id);
    }

    @Override
    public void startTabStateLoad() {
        ensureTabModelImpl();
        getDelegateDocumentTabModel().startTabStateLoad();
    }
}