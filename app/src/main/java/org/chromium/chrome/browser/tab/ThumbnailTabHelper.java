// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.res.Resources;
import android.os.Handler;
import android.text.TextUtils;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Handles capturing most visited thumbnails for a tab.
 */
public class ThumbnailTabHelper {

    private static final String TAG = "ThumbnailTabHelper";

    /** The general motivation for this value is giving the scrollbar fadeout
     *  animation sufficient time to finish before the capture executes. */
    private static final int THUMBNAIL_CAPTURE_DELAY_MS = 350;

    private final Tab mTab;
    private final Handler mHandler;

    private final int mThumbnailWidthDp;
    private final int mThumbnailHeightDp;

    private ContentViewCore mContentViewCore;
    private boolean mThumbnailCapturedForLoad;
    private boolean mIsRenderViewHostReady;
    private boolean mWasRenderViewHostReady;
    private String mRequestedUrl;

    private final Runnable mThumbnailRunnable = new Runnable() {
        @Override
        public void run() {
            // http://crbug.com/461506 : Do not get thumbnail unless render view host is ready.
            if (!mIsRenderViewHostReady) return;

            if (mThumbnailCapturedForLoad) return;
            // Prevent redundant thumbnail capture attempts.
            mThumbnailCapturedForLoad = true;
            if (!canUpdateHistoryThumbnail()) {
                // Allow a hidden tab to re-attempt capture in the future via |show()|.
                mThumbnailCapturedForLoad = !mTab.isHidden();
                return;
            }
            if (mTab.getWebContents() == null) return;

            mRequestedUrl = mTab.getUrl();
            nativeCaptureThumbnail(ThumbnailTabHelper.this, mTab.getWebContents(),
                    mThumbnailWidthDp, mThumbnailHeightDp);
        }
    };

    private final TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onContentChanged(Tab tab) {
            ThumbnailTabHelper.this.onContentChanged();
        }

        @Override
        public void onCrash(Tab tab, boolean sadTabShown) {
            cancelThumbnailCapture();
        }

        @Override
        public void onPageLoadStarted(Tab tab, String url) {
            cancelThumbnailCapture();
            mThumbnailCapturedForLoad = false;
        }

        @Override
        public void onPageLoadFinished(Tab tab) {
            rescheduleThumbnailCapture();
        }

        @Override
        public void onPageLoadFailed(Tab tab, int errorCode) {
            cancelThumbnailCapture();
        }

        @Override
        public void onShown(Tab tab) {
            // For tabs opened in the background, they may finish loading prior to becoming visible
            // and the thumbnail capture triggered as part of load finish will be skipped as the
            // tab has nothing rendered.  To handle this case, we also attempt thumbnail capture
            // when showing the tab to give it a better chance to have valid content.
            rescheduleThumbnailCapture();
        }

        @Override
        public void onClosingStateChanged(Tab tab, boolean closing) {
            if (closing) cancelThumbnailCapture();
        }

        @Override
        public void onDestroyed(Tab tab) {
            mTab.removeObserver(mTabObserver);
            if (mContentViewCore != null) {
                mContentViewCore.removeGestureStateListener(mGestureListener);
                mContentViewCore = null;
            }
        }

        @Override
        public void onDidStartProvisionalLoadForFrame(
                Tab tab, long frameId, long parentFrameId, boolean isMainFrame, String validatedUrl,
                boolean isErrorPage, boolean isIframeSrcdoc) {
            if (isMainFrame) {
                mWasRenderViewHostReady = mIsRenderViewHostReady;
                mIsRenderViewHostReady = false;
            }
        }

        @Override
        public void onDidFailLoad(
                Tab tab, boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                String description, String failingUrl) {
            // For a case that URL overriding happens, we should recover |mIsRenderViewHostReady| to
            // its old value to enable capturing thumbnail of the current page.
            // If this failure shows an error page, capturing thumbnail will be denied anyway in
            // canUpdateHistoryThumbnail().
            if (isProvisionalLoad && isMainFrame) mIsRenderViewHostReady = mWasRenderViewHostReady;
        }

        @Override
        public void onDidCommitProvisionalLoadForFrame(
                Tab tab, long frameId, boolean isMainFrame, String url, int transitionType) {
            if (isMainFrame) mIsRenderViewHostReady = true;
        }
    };

    private GestureStateListener mGestureListener = new GestureStateListener() {
        @Override
        public void onFlingStartGesture(int vx, int vy, int scrollOffsetY, int scrollExtentY) {
            cancelThumbnailCapture();
        }

        @Override
        public void onFlingEndGesture(int scrollOffsetY, int scrollExtentY) {
            rescheduleThumbnailCapture();
        }

        @Override
        public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
            cancelThumbnailCapture();
        }

        @Override
        public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {
            rescheduleThumbnailCapture();
        }
    };

    /**
     * Creates a thumbnail tab helper for the given tab.
     * @param tab The Tab whose thumbnails will be generated by this helper.
     */
    public static void createForTab(Tab tab) {
        if (!tab.isIncognito()) new ThumbnailTabHelper(tab);
    }

    /**
     * Constructs the thumbnail tab helper for a given Tab.
     * @param tab The Tab whose thumbnails will be generated by this helper.
     */
    private ThumbnailTabHelper(Tab tab) {
        mTab = tab;
        mTab.addObserver(mTabObserver);

        mHandler = new Handler();

        Resources res = tab.getWindowAndroid().getApplicationContext().getResources();
        float density = res.getDisplayMetrics().density;
        mThumbnailWidthDp = Math.round(
                res.getDimension(R.dimen.most_visited_thumbnail_width) / density);
        mThumbnailHeightDp = Math.round(
                res.getDimension(R.dimen.most_visited_thumbnail_height) / density);

        onContentChanged();
    }

    private void onContentChanged() {
        if (mContentViewCore != null) {
            mContentViewCore.removeGestureStateListener(mGestureListener);
        }

        mContentViewCore = mTab.getContentViewCore();
        if (mContentViewCore != null) {
            mContentViewCore.addGestureStateListener(mGestureListener);
            nativeInitThumbnailHelper(mContentViewCore.getWebContents());
        }
    }

    private ChromeActivity getActivity() {
        WindowAndroid window = mTab.getWindowAndroid();
        return (ChromeActivity) window.getActivity().get();
    }

    private void cancelThumbnailCapture() {
        mHandler.removeCallbacks(mThumbnailRunnable);
    }

    private void rescheduleThumbnailCapture() {
        if (mThumbnailCapturedForLoad) return;
        cancelThumbnailCapture();
        // Capture will be rescheduled when the GestureStateListener receives a
        // scroll or fling end notification.
        if (mTab.getContentViewCore() != null
                && mTab.getContentViewCore().isScrollInProgress()) {
            return;
        }
        mHandler.postDelayed(mThumbnailRunnable, THUMBNAIL_CAPTURE_DELAY_MS);
    }

    private boolean canUpdateHistoryThumbnail() {
        String url = mTab.getUrl();
        if (url.startsWith(UrlConstants.CHROME_SCHEME)
                || url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME)) {
            return false;
        }
        return mTab.isReady()
                && !mTab.isShowingErrorPage()
                && !mTab.isHidden()
                && !mTab.isShowingSadTab()
                && !mTab.isShowingInterstitialPage()
                && mTab.getProgress() == 100
                && mTab.getWidth() > 0
                && mTab.getHeight() > 0;
    }

    @CalledByNative
    private boolean shouldSaveCapturedThumbnail() {
        // Ensure that the URLs match for the requested page, and ensure
        // that the page is still valid for thumbnail capturing (i.e.
        // not showing an error page).
        return TextUtils.equals(mRequestedUrl, mTab.getUrl())
                && mThumbnailCapturedForLoad
                && canUpdateHistoryThumbnail();
    }

    private static native void nativeInitThumbnailHelper(WebContents webContents);
    private static native void nativeCaptureThumbnail(ThumbnailTabHelper thumbnailTabHelper,
            WebContents webContents, int thumbnailWidthDp, int thumbnailHeightDp);
}
