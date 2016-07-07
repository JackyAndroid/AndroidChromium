// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.gsa.GSAContextDisplaySelection;
import org.chromium.content.browser.ContentViewCore;

import java.net.URL;

import javax.annotation.Nullable;


/**
 * Provides observers for the Contextual Search Manager.
 */
public class ContextualSearchObservable {

    protected final ContextualSearchPolicy mPolicy;

    private final ObserverList<ContextualSearchObserver> mObservers =
            new ObserverList<ContextualSearchObserver>();


    ContextualSearchObservable(ChromeActivity activity) {
        mPolicy = ContextualSearchPolicy.getInstance(activity);
    }

    /**
     * @param observer The observer to notify when the user performs a contextual search.
     */
    public void addObserver(ContextualSearchObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * @param observer The observer to no longer notify when the user performs a contextual search.
     */
    public void removeObserver(ContextualSearchObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Notifies all Contextual Search observers that a search has occurred.
     * @param selectionContext The selection and context that triggered the search.
     * @param baseContentViewUrl The {@link ContentViewCore} of the base page.
     */
    protected void notifyShowContextualSearch(GSAContextDisplaySelection selectionContext,
            @Nullable URL baseContentViewUrl) {
        if (!mPolicy.canSendSurroundings(baseContentViewUrl)) selectionContext = null;

        for (ContextualSearchObserver observer : mObservers) {
            observer.onShowContextualSearch(selectionContext);
        }
    }

    /**
     * Notifies all Contextual Search observers that a search ended and is no longer in effect.
     */
    protected void notifyHideContextualSearch() {
        for (ContextualSearchObserver observer : mObservers) {
            observer.onHideContextualSearch();
        }
    }
}
