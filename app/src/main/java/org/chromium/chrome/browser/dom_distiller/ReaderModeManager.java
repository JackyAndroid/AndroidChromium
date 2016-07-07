// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManager;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchObserver;
import org.chromium.chrome.browser.dom_distiller.ReaderModePanel.ReaderModePanelHost;
import org.chromium.chrome.browser.gsa.GSAContextDisplaySelection;
import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.infobar.InfoBarContainer.InfoBarContainerObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.dom_distiller.content.DistillablePageUtils;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Manages UI effects for reader mode including hiding and showing the
 * reader mode and reader mode preferences toolbar icon and hiding the
 * top controls when a reader mode page has finished loading.
 */
public class ReaderModeManager extends EmptyTabObserver
        implements ContextualSearchObserver, InfoBarContainerObserver, ReaderModePanelHost {

    /**
     * Observer for changes to the current status of reader mode.
     */
    public static interface ReaderModeManagerObserver {
        /**
         * Triggered on changes to the reader mode status for the owning tab.
         *
         * @param readerModeStatus The current status of reader mode.
         * @see ReaderModeManager#POSSIBLE
         * @see ReaderModeManager#NOT_POSSIBLE
         * @see ReaderModeManager#STARTED
         */
        void onReaderModeStatusChanged(int readerModeStatus);
    }

    /**
     * POSSIBLE means reader mode can be entered.
     */
    public static final int POSSIBLE = 0;
    /**
     * NOT_POSSIBLE means reader mode cannot be entered.
     */
    public static final int NOT_POSSIBLE = 1;
    /**
     * STARTED means reader mode is currently in reader mode.
     */
    public static final int STARTED = 2;

    /**
     * The url of the last page visited if the last page was reader mode page.  Otherwise null.
     */
    private String mReaderModePageUrl;

    /**
     * Whether the page is an article or not.
     */
    private int mReaderModeStatus = NOT_POSSIBLE;

    /**
     * Whether the fact that the current web page was distillable or not has been recorded.
     */
    private boolean mIsUmaRecorded;

    private WebContentsObserver mWebContentsObserver;

    private final Tab mTab;

    private final ReaderModePanel mReaderModePanel;

    private final int mHeaderBackgroundColor;

    public ReaderModeManager(Tab tab, Context context) {
        mTab = tab;
        mTab.addObserver(this);
        mReaderModePanel = isEnabled(context) ? new ReaderModePanel(this, context) : null;
        mHeaderBackgroundColor = context != null
                ? ApiCompatibilityUtils.getColor(
                        context.getResources(), R.color.reader_mode_header_bg)
                : 0;
    }

    // EmptyTabObserver:
    @Override
    public void onDestroyed(Tab tab) {
        ContextualSearchManager contextualSearchManager = getContextualSearchManager(tab);
        if (contextualSearchManager != null) contextualSearchManager.removeObserver(this);

        if (mTab.getInfoBarContainer() != null) mTab.getInfoBarContainer().removeObserver(this);

        if (mReaderModePanel != null) mReaderModePanel.onDestroy();

        if (mWebContentsObserver != null) {
            mWebContentsObserver.destroy();
            mWebContentsObserver = null;
        }

        destroyReaderModeControl();
    }

    @Override
    public void onContentChanged(Tab tab) {
        if (mWebContentsObserver != null) {
            mWebContentsObserver.destroy();
            mWebContentsObserver = null;
        }
        if (tab.getWebContents() != null) {
            mWebContentsObserver = createWebContentsObserver(tab.getWebContents());
            if (DomDistillerUrlUtils.isDistilledPage(tab.getUrl())) {
                mReaderModeStatus = STARTED;
                mReaderModePageUrl = tab.getUrl();
                if (mReaderModePanel != null) mReaderModePanel.updateBottomButtonBar();
            }
        }
        ContextualSearchManager contextualSearchManager = getContextualSearchManager(tab);
        if (contextualSearchManager != null) contextualSearchManager.addObserver(this);

        if (tab.getInfoBarContainer() != null) tab.getInfoBarContainer().addObserver(this);
    }

    @Override
    public void onToggleFullscreenMode(Tab tab, boolean enable) {
        if (mReaderModePanel == null) return;

        if (enable) {
            mReaderModePanel.onEnterFullscreen();
        } else {
            mReaderModePanel.onExitFullscreen();
        }
    }

    // ContextualSearchObserver:
    @Override
    public void onShowContextualSearch(GSAContextDisplaySelection selectionContext) {
        if (mReaderModePanel != null) mReaderModePanel.hideButtonBar();
    }

    @Override
    public void onHideContextualSearch() {
        if (mReaderModePanel != null) mReaderModePanel.unhideButtonBar();
    }

    // InfoBarContainerObserver:
    @Override
    public void onAddInfoBar(InfoBarContainer container, InfoBar infoBar, boolean isFirst) {
        if (isFirst && mReaderModePanel != null) mReaderModePanel.onShowInfobarContainer();
    }

    @Override
    public void onRemoveInfoBar(InfoBarContainer container, InfoBar infoBar, boolean isLast) {
        if (isLast && mReaderModePanel != null) mReaderModePanel.onHideInfobarContainer();
    }

    // ReaderModePanelHost:

    @Override
    public int getReaderModeHeaderBackgroundColor() {
        return mHeaderBackgroundColor;
    }

    @Override
    public int getReaderModeStatus() {
        return mReaderModeStatus;
    }

    @Override
    public Tab getTab() {
        return mTab;
    }

    @Override
    public boolean isInsideDismissButton(float x, float y) {
        ReaderModeActivityDelegate delegate = getReaderModeActivityDelegate();
        if (delegate == null) return false;
        return delegate.getReaderModeControl().isInsideDismissButton(x, y);
    }

    @Override
    public void createReaderModeControl() {
        ReaderModeActivityDelegate delegate = getReaderModeActivityDelegate();
        if (delegate != null) delegate.getReaderModeControl();
    }

    @Override
    public void destroyReaderModeControl() {
        ReaderModeActivityDelegate delegate = getReaderModeActivityDelegate();
        if (delegate != null) delegate.destroyReaderModeControl();
    }

    /**
     * @return The panel associated with the managed tab.
     */
    public ReaderModePanel getReaderModePanel() {
        return mReaderModePanel;
    }

    private ReaderModeActivityDelegate getReaderModeActivityDelegate() {
        return mTab.getReaderModeActivityDelegate();
    }

    private WebContentsObserver createWebContentsObserver(WebContents webContents) {
        return new WebContentsObserver(webContents) {
            @Override
            public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
                if (!isMainFrame) return;
                if (DomDistillerUrlUtils.isDistilledPage(mTab.getUrl())) return;
                updateStatusBasedOnReaderModeCriteria(true);
            }

            @Override
            public void didFailLoad(boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                        String description, String failingUrl, boolean wasIgnoredByHandler) {
                if (!isMainFrame) return;
                if (DomDistillerUrlUtils.isDistilledPage(mTab.getUrl())) return;
                updateStatusBasedOnReaderModeCriteria(true);
            }

            @Override
            public void didStartProvisionalLoadForFrame(long frameId, long parentFrameId,
                    boolean isMainFrame, String validatedUrl, boolean isErrorPage,
                    boolean isIframeSrcdoc) {
                if (!isMainFrame) return;
                if (DomDistillerUrlUtils.isDistilledPage(validatedUrl)) {
                    mReaderModeStatus = STARTED;
                    if (mReaderModePanel != null) mReaderModePanel.updateBottomButtonBar();
                    mReaderModePageUrl = validatedUrl;
                }
            }

            @Override
            public void didNavigateMainFrame(String url, String baseUrl,
                    boolean isNavigationToDifferentPage, boolean isNavigationInPage,
                    int statusCode) {
                // TODO(cjhopman): This should possibly ignore navigations that replace the entry
                // (like those from history.replaceState()).
                if (isNavigationInPage) return;
                if (DomDistillerUrlUtils.isDistilledPage(url)) return;

                mReaderModeStatus = POSSIBLE;
                if (!TextUtils.equals(url,
                        DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(
                                mReaderModePageUrl))) {
                    mReaderModeStatus = NOT_POSSIBLE;
                    mIsUmaRecorded = false;
                    // Do not call updateStatusBasedOnReaderModeCriteria here.
                    // For ADABOOST_MODEL, it is unlikely to get valid info at this event.
                }
                mReaderModePageUrl = null;
                if (mReaderModePanel != null) mReaderModePanel.updateBottomButtonBar();
            }
        };
    }

    // Updates reader mode status based on whether or not the page should be viewed in reader mode.
    private void updateStatusBasedOnReaderModeCriteria(final boolean forceRecord) {
        if (mTab.getWebContents() == null) return;
        if (mTab.getContentViewCore() == null) return;

        DistillablePageUtils.isPageDistillable(mTab.getWebContents(),
                mTab.getContentViewCore().getIsMobileOptimizedHint(),
                new DistillablePageUtils.PageDistillableCallback() {
                    @Override
                    public void onIsPageDistillableResult(boolean isDistillable) {
                        if (isDistillable) {
                            mReaderModeStatus = POSSIBLE;
                        } else {
                            mReaderModeStatus = NOT_POSSIBLE;
                        }
                        if (!mIsUmaRecorded && (mReaderModeStatus == POSSIBLE || forceRecord)) {
                            mIsUmaRecorded = true;
                            RecordHistogram.recordBooleanHistogram(
                                    "DomDistiller.PageDistillable", mReaderModeStatus == POSSIBLE);
                        }
                        if (mReaderModePanel != null) mReaderModePanel.updateBottomButtonBar();
                    }
                });
    }

    private ContextualSearchManager getContextualSearchManager(Tab tab) {
        if (tab == null || tab.getWindowAndroid() == null) return null;
        Activity activity = tab.getWindowAndroid().getActivity().get();
        if (!(activity instanceof ChromeActivity)) return null;
        return ((ChromeActivity) activity).getContextualSearchManager();
    }

    /**
     * @return Whether Reader mode and its new UI are enabled.
     * @param context A context
     */
    public static boolean isEnabled(Context context) {
        if (context == null) return false;

        boolean enabled = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_DOM_DISTILLER)
                && !CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.DISABLE_READER_MODE_BOTTOM_BAR)
                && !DeviceFormFactor.isTablet(context)
                && DomDistillerTabUtils.isDistillerHeuristicsEnabled();
        return enabled;
    }
}
