// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.view.ContextThemeWrapper;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.ControlContainer;
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
    private static final String TAG = "WarmupManager";

    private static WarmupManager sWarmupManager;

    private final Set<String> mDnsRequestsInFlight;
    private final Map<String, Profile> mPendingPreconnectWithProfile;

    private int mToolbarContainerId;
    private ViewGroup mMainView;
    private WebContents mSpareWebContents;

    /**
     * @return The singleton instance for the WarmupManager, creating one if necessary.
     */
    public static WarmupManager getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sWarmupManager == null) sWarmupManager = new WarmupManager();
        return sWarmupManager;
    }

    private WarmupManager() {
        mDnsRequestsInFlight = new HashSet<>();
        mPendingPreconnectWithProfile = new HashMap<>();
    }

    /**
     * Inflates and constructs the view hierarchy that the app will use.
     * @param baseContext The base context to use for creating the ContextWrapper.
     * @param toolbarContainerId Id of the toolbar container.
     * @param toolbarId The toolbar's layout ID.
     */
    public void initializeViewHierarchy(Context baseContext, int toolbarContainerId,
            int toolbarId) {
        TraceEvent.begin("WarmupManager.initializeViewHierarchy");
        // Inflating the view hierarchy causes StrictMode violations on some
        // devices. Since layout inflation should happen on the UI thread, allow
        // the disk reads. crbug.com/644243.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
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
                ControlContainer controlContainer = (ControlContainer) stub.inflate();
                controlContainer.initWithToolbar(toolbarId);
            }
        } catch (InflateException e) {
            // See crbug.com/606715.
            Log.e(TAG, "Inflation exception.", e);
            mMainView = null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
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
     * @return Whether a pre-built view hierarchy exists for the given toolbarContainerId.
     */
    public boolean hasViewHierarchyWithToolbar(int toolbarContainerId) {
        ThreadUtils.assertOnUiThread();
        return mMainView != null && mToolbarContainerId == toolbarContainerId;
    }

    /**
     * Clears the inflated view hierarchy.
     */
    public void clearViewHierarchy() {
        ThreadUtils.assertOnUiThread();
        mMainView = null;
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

    /**
     * Creates and initializes a spare WebContents, to be used in a subsequent navigation.
     *
     * This creates a renderer that is suitable for any navigation. It can be picked up by any tab.
     * Can be called multiple times, and must be called from the UI thread.
     * Note that this is a no-op on low-end devices.
     */
    public void createSpareWebContents() {
        ThreadUtils.assertOnUiThread();
        if (mSpareWebContents != null || SysUtils.isLowEndDevice()) return;
        mSpareWebContents = WebContentsFactory.createWebContentsWithWarmRenderer(false, false);
    }

    /**
     * Destroys the spare WebContents if there is one.
     */
    public void destroySpareWebContents() {
        ThreadUtils.assertOnUiThread();
        if (mSpareWebContents == null) return;
        mSpareWebContents.destroy();
        mSpareWebContents = null;
    }

    /**
     * Returns a spare WebContents or null, depending on the availability of one.
     *
     * The parameters are the same as for {@link WebContentsFactory#createWebContents()}.
     *
     * @return a WebContents, or null.
     */
    public WebContents takeSpareWebContents(boolean incognito, boolean initiallyHidden) {
        ThreadUtils.assertOnUiThread();
        if (incognito || initiallyHidden) return null;
        WebContents result = mSpareWebContents;
        mSpareWebContents = null;
        return result;
    }

    /**
     * @return Whether a spare renderer is available.
     */
    public boolean hasSpareWebContents() {
        return mSpareWebContents != null;
    }

    private static native void nativePreconnectUrlAndSubresources(Profile profile, String url);
}
