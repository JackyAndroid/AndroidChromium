// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.app.Activity;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;

/**
 * Manages the activation and gesture listeners for ContextualSearch on a given tab.
 */
public class ContextualSearchTabHelper extends EmptyTabObserver {
    /**
     * Notification handler for Contextual Search events.
     */
    private final TemplateUrlServiceObserver mTemplateUrlObserver =
            new TemplateUrlServiceObserver() {
                @Override
                public void onTemplateURLServiceChanged() {
                    onContextualSearchPrefChanged();
                }
            };

    /**
     * The current ContentViewCore for the Tab which this helper is monitoring.
     */
    private ContentViewCore mBaseContentViewCore;

    /**
     * The GestureListener used for handling events from the current ContentViewCore.
     */
    private GestureStateListener mGestureStateListener;

    private long mNativeHelper = 0;

    private final Tab mTab;

    /**
     * Creates a contextual search tab helper for the given tab.
     * @param tab The tab whose contextual search actions will be handled by this helper.
     */
    public static void createForTab(Tab tab) {
        new ContextualSearchTabHelper(tab);
    }

    private ContextualSearchTabHelper(Tab tab) {
        mTab = tab;
        tab.addObserver(this);
    }

    @Override
    public void onPageLoadStarted(Tab tab, String url) {
        if (tab.getContentViewCore() == null) {
            // Nothing to do yet.
            return;
        }

        mBaseContentViewCore = tab.getContentViewCore();
        // Add Contextual Search here in case it couldn't get added in onContentChanged() due to
        // being too early in initialization of Chrome (ContextualSearchManager being null).
        setContextualSearchHooks(mBaseContentViewCore);
    }

    @Override
    public void onContentChanged(Tab tab) {
        // Native initialization happens after a page loads or content is changed to ensure profile
        // is initialized.
        if (mNativeHelper == 0) {
            mNativeHelper = nativeInit(tab.getProfile());
        }
        updateHooksForNewContentViewCore(tab);
    }

    @Override
    public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
        updateHooksForNewContentViewCore(tab);
    }

    @Override
    public void onDestroyed(Tab tab) {
        if (mNativeHelper != 0) {
            nativeDestroy(mNativeHelper);
            mNativeHelper = 0;
        }
        removeContextualSearchHooks(mBaseContentViewCore);
        mBaseContentViewCore = null;
    }

    @Override
    public void onToggleFullscreenMode(Tab tab, boolean enable) {
        ContentViewCore cvc = tab.getContentViewCore();
        if (cvc != null) {
            ContextualSearchManager manager = getContextualSearchManager(cvc);
            if (manager != null) {
                manager.hideContextualSearch(StateChangeReason.UNKNOWN);
            }
        }
    }

    /**
     * Should be called whenever the Tab's ContentViewCore changes. Removes hooks from the
     * existing ContentViewCore, if necessary and then adds hooks for the new ContentViewCore.
     * @param tab
     */
    private void updateHooksForNewContentViewCore(Tab tab) {
        removeContextualSearchHooks(mBaseContentViewCore);
        mBaseContentViewCore = tab.getContentViewCore();
        setContextualSearchHooks(mBaseContentViewCore);
    }

    /**
     * Sets up the Contextual Search hooks, adding or removing them depending on whether it is
     * currently active.
     * @param cvc The content view core to attach the gesture state listener to.
     */
    private void setContextualSearchHooks(ContentViewCore cvc) {
        if (cvc == null) return;

        if (isContextualSearchActive(cvc)) {
            addContextualSearchHooks(cvc);
        } else {
            removeContextualSearchHooks(cvc);
        }
    }

    /**
     * Adds Contextual Search hooks for its client and listener to the given content view core.
     * @param cvc The content view core to attach the gesture state listener to.
     */
    private void addContextualSearchHooks(ContentViewCore cvc) {
        if (mGestureStateListener == null) {
            mGestureStateListener = getContextualSearchManager(cvc).getGestureStateListener();
            cvc.addGestureStateListener(mGestureStateListener);
            cvc.setContextualSearchClient(getContextualSearchManager(cvc));
            TemplateUrlService.getInstance().addObserver(mTemplateUrlObserver);
        }
    }

    /**
     * Removes Contextual Search hooks for its client and listener from the given content view core.
     * @param cvc The content view core to detach the gesture state listener from.
     */
    private void removeContextualSearchHooks(ContentViewCore cvc) {
        if (cvc == null) return;

        if (mGestureStateListener != null) {
            cvc.removeGestureStateListener(mGestureStateListener);
            mGestureStateListener = null;
            cvc.setContextualSearchClient(null);
            TemplateUrlService.getInstance().removeObserver(mTemplateUrlObserver);
        }
    }

    /**
     * @return whether Contextual Search is enabled and active in this tab.
     */
    private boolean isContextualSearchActive(ContentViewCore cvc) {
        return !cvc.getWebContents().isIncognito() && getContextualSearchManager(cvc) != null
            && !PrefServiceBridge.getInstance().isContextualSearchDisabled()
            && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()
            // Svelte and Accessibility devices are incompatible with the first-run flow and
            // Talkback has poor interaction with tap to search (see http://crbug.com/399708 and
            // http://crbug.com/396934).
            // TODO(jeremycho): Handle these cases.
            && !getContextualSearchManager(cvc).isRunningInCompatibilityMode();
    }

    /**
     * @return the Contextual Search manager.
     */
    private ContextualSearchManager getContextualSearchManager(ContentViewCore cvc) {
        Activity activity = mTab.getWindowAndroid().getActivity().get();
        if (activity instanceof ChromeActivity) {
            return ((ChromeActivity) activity).getContextualSearchManager();
        }
        return null;
    }

    @CalledByNative
    private void onContextualSearchPrefChanged() {
        setContextualSearchHooks(mBaseContentViewCore);
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeContextualSearchTabHelper);
}
