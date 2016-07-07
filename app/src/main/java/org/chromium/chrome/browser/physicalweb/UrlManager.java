// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.notifications.NotificationConstants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This class stores URLs and surfaces notifications to the user.
 */
public class UrlManager {
    public static final String REFERER_KEY = "referer";
    public static final int NOTIFICATION_REFERER = 1;
    private static final String TAG = "PhysicalWeb";
    private static final String PREFS_NAME = "org.chromium.chrome.browser.physicalweb.URL_CACHE";
    private static final String PREFS_VERSION_KEY = "version";
    private static final String PREFS_URLS_KEY = "urls";
    private static final int PREFS_VERSION = 1;
    private static UrlManager sInstance = null;
    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PwsClient mPwsClient;

    /**
     * Construct the UrlManager.
     * @param context An instance of android.content.Context
     */
    public UrlManager(Context context) {
        mContext = context;
        mNotificationManager = NotificationManagerCompat.from(context);
        mPwsClient = new PwsClient();
    }

    /**
     * Get a singleton instance of this class.
     * @param context An instance of android.content.Context.
     * @return A singleton instance of this class.
     */
    public static UrlManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UrlManager(context);
        }
        return sInstance;
    }

    /**
     * Add a URL to the store of URLs.
     * This method additionally updates the Physical Web notification.
     * @param url The URL to add.
     */
    public void addUrl(String url) {
        Set<String> urls = getCachedUrls();
        urls.add(url);
        putCachedUrls(urls);
        updateNotification(urls);
    }

    /**
     * Remove a URL to the store of URLs.
     * This method additionally updates the Physical Web notification.
     * @param url The URL to remove.
     */
    public void removeUrl(String url) {
        Set<String> urls = getCachedUrls();
        urls.remove(url);
        putCachedUrls(urls);
        updateNotification(urls);
    }

    /**
     * Get the stored URLs.
     */
    public Set<String> getUrls() {
        return getCachedUrls();
    }

    private Set<String> getCachedUrls() {
        // Check the version.
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int prefsVersion = prefs.getInt(PREFS_VERSION_KEY, 0);
        if (prefsVersion != PREFS_VERSION) {
            return new HashSet<String>();
        }

        // Restore the cached urls.
        return prefs.getStringSet(PREFS_URLS_KEY, new HashSet<String>());
    }

    private void putCachedUrls(Set<String> urls) {
        // Write the version.
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFS_VERSION_KEY, PREFS_VERSION);

        // Write the urls.
        editor.putStringSet(PREFS_URLS_KEY, urls);
        editor.apply();
    }

    private PendingIntent createListUrlsIntent() {
        Intent intent = new Intent(mContext, ListUrlsActivity.class);
        intent.putExtra(REFERER_KEY, NOTIFICATION_REFERER);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        return pendingIntent;
    }

    private void updateNotification(Set<String> urls) {
        mPwsClient.resolve(urls, new PwsClient.ResolveScanCallback() {
            @Override
            public void onPwsResults(Collection<PwsResult> pwsResults) {
                // filter out duplicate site URLs
                Set<String> siteUrls = new HashSet<>();
                for (PwsResult pwsResult : pwsResults) {
                    String siteUrl = pwsResult.siteUrl;
                    if (siteUrl != null && !siteUrls.contains(siteUrl)) {
                        siteUrls.add(siteUrl);
                    }
                }

                if (siteUrls.isEmpty()) {
                    mNotificationManager.cancel(NotificationConstants.NOTIFICATION_ID_PHYSICAL_WEB);
                } else {
                    String displayUrl = siteUrls.iterator().next();
                    createNotification(displayUrl, siteUrls.size());
                }
            }
        });
    }

    private void createNotification(String displayUrl, int urlCount) {
        // Get values to display.
        Resources resources = mContext.getResources();
        String title = resources.getQuantityString(R.plurals.physical_web_notification_title,
                                                   urlCount, urlCount);
        PendingIntent pendingIntent = createListUrlsIntent();

        // Create the notification.
        Notification notification = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_physical_web_notification)
                .setContentTitle(title)
                .setContentText(displayUrl)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        mNotificationManager.notify(NotificationConstants.NOTIFICATION_ID_PHYSICAL_WEB,
                                    notification);
    }
}
