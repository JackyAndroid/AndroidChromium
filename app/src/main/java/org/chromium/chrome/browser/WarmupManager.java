// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.os.AsyncTask;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.prerender.ExternalPrerenderHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.WebContents;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is a singleton that holds utilities for warming up Chrome and prerendering urls
 * without creating the Activity.
 *
 * This class is not thread-safe and must only be used on the UI thread.
 */
public final class WarmupManager {
    private static WarmupManager sWarmupManager;

    private final Set<String> mDnsRequestsInFlight;
    private final Map<String, Profile> mPendingPreconnectWithProfile;

    private boolean mPrerenderIsAllowed;
    private WebContents mPrerenderedWebContents;
    private boolean mPrerendered;
    private int mToolbarContainerId;
    private ViewGroup mMainView;
    private ExternalPrerenderHandler mExternalPrerenderHandler;

    /**
     * @return The singleton instance for the WarmupManager, creating one if necessary.
     */
    public static WarmupManager getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sWarmupManager == null) sWarmupManager = new WarmupManager();
        return sWarmupManager;
    }

    private WarmupManager() {
        mPrerenderIsAllowed = true;
        mDnsRequestsInFlight = new HashSet<String>();
        mPendingPreconnectWithProfile = new HashMap<String, Profile>();
    }

    /**
     * Disallow prerendering from now until the browser process death.
     */
    public void disallowPrerendering() {
        ThreadUtils.assertOnUiThread();
        mPrerenderIsAllowed = false;
        cancelCurrentPrerender();
        mExternalPrerenderHandler = null;
    }

    /**
     * Check whether prerender manager has the given url prerendered. This also works with
     * redirected urls.
     *
     * Uses the last used profile.
     *
     * @param url The url to check.
     * @return Whether the given url has been prerendered.
     */
    public boolean hasPrerenderedUrl(String url) {
        ThreadUtils.assertOnUiThread();
        if (!mPrerenderIsAllowed) return false;
        return hasAnyPrerenderedUrl() && ExternalPrerenderHandler.hasPrerenderedUrl(
                Profile.getLastUsedProfile(), url, mPrerenderedWebContents);
    }

    /**
     * @return Whether any url has been prerendered.
     */
    public boolean hasAnyPrerenderedUrl() {
        ThreadUtils.assertOnUiThread();
        if (!mPrerenderIsAllowed) return false;
        return mPrerendered;
    }

    /**
     * @return The prerendered {@link WebContents} clearing out the reference WarmupManager owns.
     */
    public WebContents takePrerenderedWebContents() {
        ThreadUtils.assertOnUiThread();
        if (!mPrerenderIsAllowed) return null;
        WebContents prerenderedWebContents = mPrerenderedWebContents;
        assert (mPrerenderedWebContents != null);
        mPrerenderedWebContents = null;
        return prerenderedWebContents;
    }

    /**
     * Prerenders the given url using the prerender_manager.
     *
     * Uses the last used profile.
     *
     * @param url The url to prerender.
     * @param referrer The referrer url to be used while prerendering
     * @param widthPix The width in pixels to which the page should be prerendered.
     * @param heightPix The height in pixels to which the page should be prerendered.
     */
    public void prerenderUrl(final String url, final String referrer,
            final int widthPix, final int heightPix) {
        ThreadUtils.assertOnUiThread();
        if (!mPrerenderIsAllowed) return;
        clearWebContentsIfNecessary();
        if (mExternalPrerenderHandler == null) {
            mExternalPrerenderHandler = new ExternalPrerenderHandler();
        }

        mPrerenderedWebContents = mExternalPrerenderHandler.addPrerender(
                Profile.getLastUsedProfile(), url, referrer, widthPix, heightPix);
        if (mPrerenderedWebContents != null) mPrerendered = true;
    }

    /**
     * Inflates and constructs the view hierarchy that the app will use.
     * @param baseContext The base context to use for creating the ContextWrapper.
     * @param toolbarContainerId Id of the toolbar container.
     */
    public void initializeViewHierarchy(Context baseContext, int toolbarContainerId) {
        TraceEvent.begin("WarmupManager.initializeViewHierarchy");
        try {
            ThreadUtils.assertOnUiThread();
            if (mMainView != null && mToolbarContainerId == toolbarContainerId) return;
            ContextThemeWrapper context =
                    new ContextThemeWrapper(baseContext, ChromeActivity.getThemeId());
            FrameLayout contentHolder = new FrameLayout(context);
            mMainView = (ViewGroup) LayoutInflater.from(context).inflate(
                    R.layout.main, contentHolder);
            mToolbarContainerId = toolbarContainerId;
            if (toolbarContainerId != ChromeActivity.NO_CONTROL_CONTAINER) {
                ViewStub stub = (ViewStub) mMainView.findViewById(R.id.control_container_stub);
                stub.setLayoutResource(toolbarContainerId);
                stub.inflate();
            }
        } finally {
            TraceEvent.end("WarmupManager.initializeViewHierarchy");
        }
    }

    /**
     * Transfers all the children in the view hierarchy to the giving ViewGroup as child.
     * @param contentView The parent ViewGroup to use for the transfer.
     */
    public void transferViewHierarchyTo(ViewGroup contentView) {
        ThreadUtils.assertOnUiThread();
        ViewGroup viewHierarchy = mMainView;
        mMainView = null;
        if (viewHierarchy == null) return;
        while (viewHierarchy.getChildCount() > 0) {
            View currentChild = viewHierarchy.getChildAt(0);
            viewHierarchy.removeView(currentChild);
            contentView.addView(currentChild);
        }
    }

    /**
     * Destroys the native WebContents instance the WarmupManager currently holds onto.
     */
    public void clearWebContentsIfNecessary() {
        ThreadUtils.assertOnUiThread();
        mPrerendered = false;
        if (mPrerenderedWebContents == null) return;

        mPrerenderedWebContents.destroy();
        mPrerenderedWebContents = null;
    }

    /**
     * Cancel the current prerender.
     */
    public void cancelCurrentPrerender() {
        ThreadUtils.assertOnUiThread();
        clearWebContentsIfNecessary();
        if (mExternalPrerenderHandler == null) return;

        mExternalPrerenderHandler.cancelCurrentPrerender();
    }

    /**
     * @return Whether the view hierarchy has been prebuilt with a given toolbar ID. If there is no
     * match, clears the inflated view.
     */
    public boolean hasBuiltOrClearViewHierarchyWithToolbar(int toolbarContainerId) {
        ThreadUtils.assertOnUiThread();
        boolean match = mMainView != null && mToolbarContainerId == toolbarContainerId;
        if (!match) mMainView = null;
        return match;
    }

    /**
     * Launches a background DNS query for a given URL.
     *
     * @param url URL from which the domain to query is extracted.
     */
    private void prefetchDnsForUrlInBackground(final String url) {
        mDnsRequestsInFlight.add(url);
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                try {
                    InetAddress.getByName(new URL(url).getHost());
                } catch (MalformedURLException e) {
                    // We don't do anything with the result of the request, it
                    // is only here to warm up the cache, thus ignoring the
                    // exception is fine.
                } catch (UnknownHostException e) {
                    // As above.
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mDnsRequestsInFlight.remove(url);
                if (mPendingPreconnectWithProfile.containsKey(url)) {
                    Profile profile = mPendingPreconnectWithProfile.get(url);
                    mPendingPreconnectWithProfile.remove(url);
                    maybePreconnectUrlAndSubResources(profile, url);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    /** Launches a background DNS query for a given URL if the data reduction proxy is not in use.
     *
     * @param context The Application context.
     * @param url URL from which the domain to query is extracted.
     */
    public void maybePrefetchDnsForUrlInBackground(Context context, String url) {
        ThreadUtils.assertOnUiThread();
        if (!DataReductionProxySettings.isEnabledBeforeNativeLoad(context)) {
            prefetchDnsForUrlInBackground(url);
        }
    }

    /** Asynchronously preconnects to a given URL if the data reduction proxy is not in use.
     *
     * @param profile The profile to use for the preconnection.
     * @param url The URL we want to preconnect to.
     */
    public void maybePreconnectUrlAndSubResources(Profile profile, String url) {
        ThreadUtils.assertOnUiThread();
        if (!DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) {
            // If there is already a DNS request in flight for this URL, then
            // the preconnection will start by issuing a DNS request for the
            // same domain, as the result is not cached. However, such a DNS
            // request has already been sent from this class, so it is better to
            // wait for the answer to come back before preconnecting. Otherwise,
            // the preconnection logic will wait for the result of the second
            // DNS request, which should arrive after the result of the first
            // one. Note that we however need to wait for the main thread to be
            // available in this case, since the preconnection will be sent from
            // AsyncTask.onPostExecute(), which may delay it.
            if (mDnsRequestsInFlight.contains(url)) {
                // Note that if two requests come for the same URL with two
                // different profiles, the last one will win.
                mPendingPreconnectWithProfile.put(url, profile);
            } else {
                nativePreconnectUrlAndSubresources(profile, url);
            }
        }
    }

    private static native void nativePreconnectUrlAndSubresources(Profile profile, String url);
}
