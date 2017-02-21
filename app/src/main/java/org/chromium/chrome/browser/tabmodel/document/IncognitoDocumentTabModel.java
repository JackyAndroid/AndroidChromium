// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel.document;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.incognito.IncognitoNotificationManager;
import org.chromium.chrome.browser.tabmodel.EmptyTabModel;
import org.chromium.chrome.browser.tabmodel.IncognitoTabModel;
import org.chromium.chrome.browser.tabmodel.TabModel;

/**
 * Implements an Incognito version of the DocumentTabModel.  Timing is a little bit different for
 * profile deletion because we don't get all the signals we'd expect when Tabs are closed.  More
 * specifically, Android doesn't fire signals when tasks are swiped away from the Recents menu if
 * the Activity is dead when it occurs.
 */
public class IncognitoDocumentTabModel extends IncognitoTabModel implements DocumentTabModel {
    private final ActivityDelegate mActivityDelegate;

    public IncognitoDocumentTabModel(IncognitoTabModelDelegate offTheRecordDelegate,
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
    public String getInitialUrlForDocument(int tabId) {
        if (!isDocumentTabModelImplCreated()) return null;
        return getDelegateDocumentTabModel().getInitialUrlForDocument(tabId);
    }
}
