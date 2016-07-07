// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import org.chromium.base.Log;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.blink_public.platform.WebDisplayMode;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.RepostFormWarningDialog;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.document.DocumentWebContentsDelegate;
import org.chromium.chrome.browser.findinpage.FindMatchRectsDetails;
import org.chromium.chrome.browser.findinpage.FindNotificationDetails;
import org.chromium.chrome.browser.media.MediaCaptureNotificationService;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.policy.PolicyAuditor.AuditEvent;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.InvalidateTypes;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.WindowOpenDisposition;

/**
 * A basic {@link TabWebContentsDelegateAndroid} that forwards some calls to the registered
 * {@link TabObserver}s.
 */
public class TabWebContentsDelegateAndroid extends WebContentsDelegateAndroid {
    /**
     * Listener to be notified when a find result is received.
     */
    public interface FindResultListener {
        public void onFindResult(FindNotificationDetails result);
    }

    /**
     * Listener to be notified when the rects corresponding to find matches are received.
     */
    public interface FindMatchRectsListener {
        public void onFindMatchRects(FindMatchRectsDetails result);
    }

    /** Used for logging. */
    private static final String TAG = "WebContentsDelegate";

    private final Tab mTab;
    protected final ChromeActivity mActivity;

    private FindResultListener mFindResultListener;

    private FindMatchRectsListener mFindMatchRectsListener = null;

    private int mDisplayMode = WebDisplayMode.Browser;

    protected Handler mHandler;
    private final Runnable mCloseContentsRunnable = new Runnable() {
        @Override
        public void run() {
            boolean isSelected = mActivity.getTabModelSelector().getCurrentTab() == mTab;
            mActivity.getTabModelSelector().closeTab(mTab);

            // If the parent Tab belongs to another Activity, fire the Intent to bring it back.
            if (isSelected && mTab.getParentIntent() != null
                    && mActivity.getIntent() != mTab.getParentIntent()) {
                boolean mayLaunch = FeatureUtilities.isDocumentMode(mActivity)
                        ? isParentInAndroidOverview() : true;
                if (mayLaunch) mActivity.startActivity(mTab.getParentIntent());
            }
        }

        /** If the API allows it, returns whether a Task still exists for the parent Activity. */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private boolean isParentInAndroidOverview() {
            ActivityManager activityManager =
                    (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
                Intent taskIntent = DocumentUtils.getBaseIntentFromTask(task);
                if (taskIntent != null && taskIntent.filterEquals(mTab.getParentIntent())) {
                    return true;
                }
            }
            return false;
        }
    };

    public TabWebContentsDelegateAndroid(Tab tab, ChromeActivity activity) {
        mTab = tab;
        mActivity = activity;
        mHandler = new Handler();
    }

    /**
     * Sets the current display mode which can be queried using media queries.
     * @param displayMode A value from {@link org.chromium.blink_public.platform.WebDisplayMode}.
     */
    public void setDisplayMode(int displayMode) {
        mDisplayMode = displayMode;
    }

    @CalledByNative
    private int getDisplayMode() {
        return mDisplayMode;
    }

    @CalledByNative
    private void onFindResultAvailable(FindNotificationDetails result) {
        if (mFindResultListener != null) {
            mFindResultListener.onFindResult(result);
        }
    }

    @CalledByNative
    private void onFindMatchRectsAvailable(FindMatchRectsDetails result) {
        if (mFindMatchRectsListener != null) {
            mFindMatchRectsListener.onFindMatchRects(result);
        }
    }

    /** Register to receive the results of startFinding calls. */
    public void setFindResultListener(FindResultListener listener) {
        mFindResultListener = listener;
    }

    /** Register to receive the results of requestFindMatchRects calls. */
    public void setFindMatchRectsListener(FindMatchRectsListener listener) {
        mFindMatchRectsListener = listener;
    }

    // Helper functions used to create types that are part of the public interface
    @CalledByNative
    private static Rect createRect(int x, int y, int right, int bottom) {
        return new Rect(x, y, right, bottom);
    }

    @CalledByNative
    private static RectF createRectF(float x, float y, float right, float bottom) {
        return new RectF(x, y, right, bottom);
    }

    @CalledByNative
    private static FindNotificationDetails createFindNotificationDetails(
            int numberOfMatches, Rect rendererSelectionRect,
            int activeMatchOrdinal, boolean finalUpdate) {
        return new FindNotificationDetails(numberOfMatches, rendererSelectionRect,
                activeMatchOrdinal, finalUpdate);
    }

    @CalledByNative
    private static FindMatchRectsDetails createFindMatchRectsDetails(
            int version, int numRects, RectF activeRect) {
        return new FindMatchRectsDetails(version, numRects, activeRect);
    }

    @CalledByNative
    private static void setMatchRectByIndex(
            FindMatchRectsDetails findMatchRectsDetails, int index, RectF rect) {
        findMatchRectsDetails.rects[index] = rect;
    }

    @Override
    public void onLoadProgressChanged(int progress) {
        if (!mTab.isLoading()) return;
        mTab.notifyLoadProgress(mTab.getProgress());
    }

    @Override
    public void onLoadStarted(boolean toDifferentDocument) {
        mTab.onLoadStarted(toDifferentDocument);
    }

    @Override
    public void onLoadStopped() {
        mTab.onLoadStopped();
    }

    @Override
    public void onUpdateUrl(String url) {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onUpdateUrl(mTab, url);
        }
    }

    @Override
    public void showRepostFormWarningDialog() {
        mTab.resetSwipeRefreshHandler();
        RepostFormWarningDialog warningDialog = new RepostFormWarningDialog(
                new Runnable() {
                    @Override
                    public void run() {
                        mTab.getWebContents().getNavigationController().cancelPendingReload();
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        mTab.getWebContents().getNavigationController().continuePendingReload();
                    }
                });
        warningDialog.show(mActivity.getFragmentManager(), null);
    }

    @Override
    public void toggleFullscreenModeForTab(boolean enableFullscreen) {
        if (mTab.getFullscreenManager() != null) {
            mTab.getFullscreenManager().setPersistentFullscreenMode(enableFullscreen);
        }

        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onToggleFullscreenMode(mTab, enableFullscreen);
        }
    }

    @Override
    public void navigationStateChanged(int flags) {
        if ((flags & InvalidateTypes.TAB) != 0) {
            MediaCaptureNotificationService.updateMediaNotificationForTab(
                    mTab.getApplicationContext(), mTab.getId(), isCapturingAudio(),
                    isCapturingVideo(), mTab.getUrl());
        }
        if ((flags & InvalidateTypes.TITLE) != 0) {
            // Update cached title then notify observers.
            mTab.updateTitle();
        }
        if ((flags & InvalidateTypes.URL) != 0) {
            RewindableIterator<TabObserver> observers = mTab.getTabObservers();
            while (observers.hasNext()) {
                observers.next().onUrlUpdated(mTab);
            }
        }
    }

    @Override
    public void visibleSSLStateChanged() {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().onSSLStateUpdated(mTab);
        }
    }

    @Override
    public void webContentsCreated(WebContents sourceWebContents, long openerRenderFrameId,
            String frameName, String targetUrl, WebContents newWebContents) {
        RewindableIterator<TabObserver> observers = mTab.getTabObservers();
        while (observers.hasNext()) {
            observers.next().webContentsCreated(mTab, sourceWebContents, openerRenderFrameId,
                    frameName, targetUrl, newWebContents);
        }
        // The URL can't be taken from the WebContents if it's paused.  Save it for later.
        assert mWebContentsUrlMapping == null;
        mWebContentsUrlMapping = Pair.create(newWebContents, targetUrl);

        // TODO(dfalcantara): Re-remove this once crbug.com/508366 is fixed.
        TabCreator tabCreator = mActivity.getTabCreator(mTab.isIncognito());

        if (tabCreator != null && tabCreator.createsTabsAsynchronously()) {
            DocumentWebContentsDelegate.getInstance().attachDelegate(newWebContents);
        }
    }

    @Override
    public void rendererUnresponsive() {
        super.rendererUnresponsive();
        if (mTab.getWebContents() != null) nativeOnRendererUnresponsive(mTab.getWebContents());
        mTab.handleRendererUnresponsive();
    }

    @Override
    public void rendererResponsive() {
        super.rendererResponsive();
        if (mTab.getWebContents() != null) nativeOnRendererResponsive(mTab.getWebContents());
        mTab.handleRendererResponsive();
    }

    @Override
    public boolean isFullscreenForTabOrPending() {
        return mTab.getFullscreenManager() == null
                ? false : mTab.getFullscreenManager().getPersistentFullscreenMode();
    }

    @Override
    public void openNewTab(String url, String extraHeaders, byte[] postData, int disposition,
            boolean isRendererInitiated) {
        mTab.openNewTab(url, extraHeaders, postData, disposition, true, isRendererInitiated);
    }

    private Pair<WebContents, String> mWebContentsUrlMapping;

    protected TabModel getTabModel() {
        // TODO(dfalcantara): Remove this when DocumentActivity.getTabModelSelector()
        //                    can return a TabModelSelector that activateContents() can use.
        return mActivity.getTabModelSelector().getModel(mTab.isIncognito());
    }

    @CalledByNative
    public boolean shouldResumeRequestsForCreatedWindow() {
        // Pause the WebContents if an Activity has to be created for it first.
        TabCreator tabCreator = mActivity.getTabCreator(mTab.isIncognito());
        assert tabCreator != null;
        return !tabCreator.createsTabsAsynchronously();
    }

    @CalledByNative
    public boolean addNewContents(WebContents sourceWebContents, WebContents webContents,
            int disposition, Rect initialPosition, boolean userGesture) {
        assert mWebContentsUrlMapping.first == webContents;

        TabCreator tabCreator = mActivity.getTabCreator(mTab.isIncognito());
        assert tabCreator != null;

        // Grab the URL, which might not be available via the Tab.
        String url = mWebContentsUrlMapping.second;
        mWebContentsUrlMapping = null;

        // Skip opening a new Tab if it doesn't make sense.
        if (mTab.isClosing()) return false;

        // Creating new Tabs asynchronously requires starting a new Activity to create the Tab,
        // so the Tab returned will always be null.  There's no way to know synchronously
        // whether the Tab is created, so assume it's always successful.
        boolean createdSuccessfully = tabCreator.createTabWithWebContents(
                webContents, mTab.getId(), TabLaunchType.FROM_LONGPRESS_FOREGROUND, url);
        boolean success = tabCreator.createsTabsAsynchronously() || createdSuccessfully;
        if (success && disposition == WindowOpenDisposition.NEW_POPUP) {
            PolicyAuditor auditor =
                    ((ChromeApplication) mTab.getApplicationContext()).getPolicyAuditor();
            auditor.notifyAuditEvent(mTab.getApplicationContext(),
                    AuditEvent.OPEN_POPUP_URL_SUCCESS, url, "");
        }

        return success;
    }

    @Override
    public void activateContents() {
        boolean activityIsDestroyed = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            activityIsDestroyed = mActivity.isDestroyed();
        }
        if (activityIsDestroyed || !mTab.isInitialized()) {
            Log.e(TAG, "Activity destroyed before calling activateContents().  Bailing out.");
            return;
        }

        TabModel model = getTabModel();
        int index = model.indexOf(mTab);
        if (index == TabModel.INVALID_TAB_INDEX) return;
        TabModelUtils.setIndex(model, index);
        bringActivityToForeground();
    }

    /**
     * Brings chrome's Activity to foreground, if it is not so.
     */
    protected void bringActivityToForeground() {
        // This intent is sent in order to get the activity back to the foreground if it was
        // not already. The previous call will activate the right tab in the context of the
        // TabModel but will only show the tab to the user if Chrome was already in the
        // foreground.
        // The intent is getting the tabId mostly because it does not cost much to do so.
        // When receiving the intent, the tab associated with the tabId should already be
        // active.
        // Note that calling only the intent in order to activate the tab is slightly slower
        // because it will change the tab when the intent is handled, which happens after
        // Chrome gets back to the foreground.
        Intent newIntent = Tab.createBringTabToFrontIntent(mTab.getId());
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mTab.getApplicationContext().startActivity(newIntent);
    }

    @Override
    public void closeContents() {
        // Execute outside of callback, otherwise we end up deleting the native
        // objects in the middle of executing methods on them.
        mHandler.removeCallbacks(mCloseContentsRunnable);
        mHandler.post(mCloseContentsRunnable);
    }

    @Override
    public boolean takeFocus(boolean reverse) {
        if (reverse) {
            View menuButton = mActivity.findViewById(R.id.menu_button);
            if (menuButton == null || !menuButton.isShown()) {
                menuButton = mActivity.findViewById(R.id.document_menu_button);
            }
            if (menuButton != null && menuButton.isShown()) {
                return menuButton.requestFocus();
            }

            View tabSwitcherButton = mActivity.findViewById(R.id.tab_switcher_button);
            if (tabSwitcherButton != null && tabSwitcherButton.isShown()) {
                return tabSwitcherButton.requestFocus();
            }
        } else {
            View urlBar = mActivity.findViewById(R.id.url_bar);
            if (urlBar != null) return urlBar.requestFocus();
        }
        return false;
    }

    @Override
    public void handleKeyboardEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (mActivity.onKeyDown(event.getKeyCode(), event)) return;

            // Handle the Escape key here (instead of in KeyboardShortcuts.java), so it doesn't
            // interfere with other parts of the activity (e.g. the URL bar).
            if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers()) {
                WebContents wc = mTab.getWebContents();
                if (wc != null) wc.stop();
                return;
            }
        }
        handleMediaKey(event);
    }

    /**
     * Redispatches unhandled media keys. This allows bluetooth headphones with play/pause or
     * other buttons to function correctly.
     */
    @TargetApi(19)
    private void handleMediaKey(KeyEvent e) {
        if (Build.VERSION.SDK_INT < 19) return;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_CLOSE:
            case KeyEvent.KEYCODE_MEDIA_EJECT:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                AudioManager am = (AudioManager) mActivity.getSystemService(
                        Context.AUDIO_SERVICE);
                am.dispatchMediaKeyEvent(e);
                break;
            default:
                break;
        }
    }

    /**
     * @return Whether audio is being captured.
     */
    private boolean isCapturingAudio() {
        return !mTab.isClosing() && nativeIsCapturingAudio(mTab.getWebContents());
    }

    /**
     * @return Whether video is being captured.
     */
    private boolean isCapturingVideo() {
        return !mTab.isClosing() && nativeIsCapturingVideo(mTab.getWebContents());
    }

    private static native void nativeOnRendererUnresponsive(WebContents webContents);
    private static native void nativeOnRendererResponsive(WebContents webContents);
    private static native boolean nativeIsCapturingAudio(WebContents webContents);
    private static native boolean nativeIsCapturingVideo(WebContents webContents);
}
