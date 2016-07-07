// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.media.MediaCaptureNotificationService;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.policy.PolicyAuditor.AuditEvent;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.concurrent.TimeUnit;

/**
 * WebContentsObserver used by Tab.
 */
public class TabWebContentsObserver extends WebContentsObserver {
    // URL didFailLoad error code. Should match the value in net_error_list.h.
    public static final int BLOCKED_BY_ADMINISTRATOR = -22;

    /** Used for logging. */
    private static final String TAG = "TabWebContentsObs";

    // TabRendererCrashStatus defined in tools/metrics/histograms/histograms.xml.
    private static final int TAB_RENDERER_CRASH_STATUS_SHOWN_IN_FOREGROUND_APP = 0;
    private static final int TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_FOREGROUND_APP = 1;
    private static final int TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_BACKGROUND_APP = 2;
    private static final int TAB_RENDERER_CRASH_STATUS_MAX = 3;

    private final Tab mTab;
    private int mThemeColor;

    public TabWebContentsObserver(WebContents webContents, Tab tab) {
        super(webContents);
        mTab = tab;
        mThemeColor = mTab.getDefaultThemeColor();
    }

    @Override
    public void renderProcessGone(boolean processWasOomProtected) {
        Log.i(TAG, "renderProcessGone() for tab id: " + mTab.getId()
                + ", oom protected: " + Boolean.toString(processWasOomProtected)
                + ", already needs reload: " + Boolean.toString(mTab.needsReload()));
        // Do nothing for subsequent calls that happen while the tab remains crashed. This
        // can occur when the tab is in the background and it shares the renderer with other
        // tabs. After the renderer crashes, the WebContents of its tabs are still around
        // and they still share the RenderProcessHost. When one of the tabs reloads spawning
        // a new renderer for the shared RenderProcessHost and the new renderer crashes
        // again, all tabs sharing this renderer will be notified about the crash (including
        // potential background tabs that did not reload yet).
        if (mTab.needsReload() || mTab.isShowingSadTab()) return;

        int activityState = ApplicationStatus.getStateForActivity(
                mTab.getWindowAndroid().getActivity().get());
        int rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_MAX;
        if (!processWasOomProtected
                || activityState == ActivityState.PAUSED
                || activityState == ActivityState.STOPPED
                || activityState == ActivityState.DESTROYED) {
            // The tab crashed in background or was killed by the OS out-of-memory killer.
            //setNeedsReload(true);
            mTab.setNeedsReload(true);
            if (ApplicationStatus.getStateForApplication()
                    == ApplicationState.HAS_RUNNING_ACTIVITIES) {
                rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_FOREGROUND_APP;
            } else {
                rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_BACKGROUND_APP;
            }
        } else {
            rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_SHOWN_IN_FOREGROUND_APP;
            mTab.showSadTab();
            // This is necessary to correlate histogram data with stability counts.
            UmaSessionStats.logRendererCrash();
        }
        RecordHistogram.recordEnumeratedHistogram(
                "Tab.RendererCrashStatus", rendererCrashStatus, TAB_RENDERER_CRASH_STATUS_MAX);

        mTab.handleTabCrash();

        boolean sadTabShown = mTab.isShowingSadTab();
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onCrash(mTab, sadTabShown);
        }
    }

    @Override
    public void navigationEntryCommitted() {
        if (mTab.getNativePage() != null) {
            mTab.pushNativePageStateToNavigationEntry();
        }
    }

    @Override
    public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
        if (isMainFrame) mTab.didFinishPageLoad();
        PolicyAuditor auditor =
                ((ChromeApplication) mTab.getApplicationContext()).getPolicyAuditor();
        auditor.notifyAuditEvent(
                mTab.getApplicationContext(), AuditEvent.OPEN_URL_SUCCESS, validatedUrl, "");
    }

    @Override
    public void didFailLoad(boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
            String description, String failingUrl, boolean wasIgnoredByHandler) {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidFailLoad(mTab, isProvisionalLoad, isMainFrame, errorCode,
                    description, failingUrl);
        }

        if (isMainFrame) mTab.didFailPageLoad(errorCode);

        PolicyAuditor auditor =
                ((ChromeApplication) mTab.getApplicationContext()).getPolicyAuditor();
        auditor.notifyAuditEvent(mTab.getApplicationContext(), AuditEvent.OPEN_URL_FAILURE,
                failingUrl, description);
        if (errorCode == BLOCKED_BY_ADMINISTRATOR) {
            auditor.notifyAuditEvent(
                    mTab.getApplicationContext(), AuditEvent.OPEN_URL_BLOCKED, failingUrl, "");
        }
    }

    @Override
    public void didStartProvisionalLoadForFrame(long frameId, long parentFrameId,
            boolean isMainFrame, String validatedUrl, boolean isErrorPage,
            boolean isIframeSrcdoc) {
        if (isMainFrame) mTab.didStartPageLoad(validatedUrl, isErrorPage);

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidStartProvisionalLoadForFrame(mTab, frameId, parentFrameId,
                    isMainFrame, validatedUrl, isErrorPage, isIframeSrcdoc);
        }
    }

    @Override
    public void didCommitProvisionalLoadForFrame(long frameId, boolean isMainFrame, String url,
            int transitionType) {
        if (isMainFrame && UmaUtils.isRunningApplicationStart()) {
            // Currently it takes about 2000ms to commit a navigation if the measurement
            // begins very early in the browser start. How many buckets (b) are needed to
            // explore the _typical_ values with granularity 100ms and a maximum duration
            // of 1 minute?
            //   s^{n+1} / s^{n} = 2100 / 2000
            //   s = 1.05
            //   s^b = 60000
            //   b = ln(60000) / ln(1.05) ~= 225
            RecordHistogram.recordCustomTimesHistogram("Startup.FirstCommitNavigationTime",
                    System.currentTimeMillis() - UmaUtils.getMainEntryPointTime(),
                    1, 60000 /* 1 minute */, TimeUnit.MILLISECONDS, 225);
            UmaUtils.setRunningApplicationStart(false);
        }

        if (isMainFrame) {
            mTab.setIsTabStateDirty(true);
            mTab.updateTitle();
        }

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidCommitProvisionalLoadForFrame(
                    mTab, frameId, isMainFrame, url, transitionType);
        }

        observers.rewind();
        while (observers.hasNext()) {
            observers.next().onUrlUpdated(mTab);
        }

        if (!isMainFrame) return;
        mTab.handleDidCommitProvisonalLoadForFrame(url, transitionType);
    }

    @Override
    public void didNavigateMainFrame(String url, String baseUrl,
            boolean isNavigationToDifferentPage, boolean isFragmentNavigation, int statusCode) {
        FullscreenManager fullscreenManager = mTab.getFullscreenManager();
        if (isNavigationToDifferentPage && fullscreenManager != null) {
            fullscreenManager.setPersistentFullscreenMode(false);
        }

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidNavigateMainFrame(
                    mTab, url, baseUrl, isNavigationToDifferentPage,
                    isFragmentNavigation, statusCode);
        }

        mTab.stopSwipeRefreshHandler();
    }

    @Override
    public void didFirstVisuallyNonEmptyPaint() {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().didFirstVisuallyNonEmptyPaint(mTab);
        }
    }

    @Override
    public void didChangeThemeColor(int color) {
        int securityLevel = mTab.getSecurityLevel();
        if (securityLevel == ConnectionSecurityLevel.SECURITY_ERROR
                || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING
                || securityLevel == ConnectionSecurityLevel.SECURITY_POLICY_WARNING) {
            color = mTab.getDefaultThemeColor();
        }
        if (mTab.isShowingInterstitialPage()) color = mTab.getDefaultThemeColor();
        if (!FeatureUtilities.isDocumentMode(mTab.getApplicationContext())
                && !isThemeColorEnabledInTabbedMode(mTab.getApplicationContext())) {
            color = mTab.getDefaultThemeColor();
        }
        if (color == Color.TRANSPARENT) color = mTab.getDefaultThemeColor();
        if (mTab.isIncognito()) color = mTab.getDefaultThemeColor();
        color |= 0xFF000000;
        if (mTab.getThemeColor() == color) return;
        mThemeColor = color;
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidChangeThemeColor(mTab, mTab.getThemeColor());
        }
    }

    @Override
    public void didAttachInterstitialPage() {
        mTab.getInfoBarContainer().setVisibility(View.INVISIBLE);
        mTab.showRenderedPage();
        didChangeThemeColor(mTab.getDefaultThemeColor());

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidAttachInterstitialPage(mTab);
        }
        mTab.notifyLoadProgress(mTab.getProgress());

        mTab.updateFullscreenEnabledState();

        PolicyAuditor auditor =
                ((ChromeApplication) mTab.getApplicationContext()).getPolicyAuditor();
        auditor.notifyCertificateFailure(mTab.getWebContents(), mTab.getApplicationContext());
    }

    @Override
    public void didDetachInterstitialPage() {
        mTab.getInfoBarContainer().setVisibility(View.VISIBLE);
        didChangeThemeColor(mTab.getWebContents().getThemeColor(mTab.getDefaultThemeColor()));

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidDetachInterstitialPage(mTab);
        }
        mTab.notifyLoadProgress(mTab.getProgress());

        mTab.updateFullscreenEnabledState();

        if (!mTab.maybeShowNativePage(mTab.getUrl(), false)) {
            mTab.showRenderedPage();
        }
    }

    @Override
    public void didStartNavigationToPendingEntry(String url) {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onDidStartNavigationToPendingEntry(mTab, url);
        }
    }

    @Override
    public void destroy() {
        MediaCaptureNotificationService.updateMediaNotificationForTab(
                mTab.getApplicationContext(), mTab.getId(), false, false, mTab.getUrl());
        super.destroy();
    }

    /**
     * @return The theme-color for this web contents.
     */
    int getThemeColor() {
        return mThemeColor;
    }

    private static boolean isThemeColorEnabledInTabbedMode(Context context) {
        if (DeviceFormFactor.isTablet(context)) return false;
        CommandLine commandLine = CommandLine.getInstance();
        return ChromeVersionInfo.isLocalBuild()
                || ChromeVersionInfo.isCanaryBuild()
                || ChromeVersionInfo.isDevBuild()
                || commandLine.hasSwitch(ChromeSwitches.ENABLE_THEME_COLOR_IN_TABBED_MODE);
    }
}