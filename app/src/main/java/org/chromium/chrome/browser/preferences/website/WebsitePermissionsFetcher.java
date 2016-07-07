// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.chrome.browser.ContentSettingsType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Utility class that asynchronously fetches any Websites and the permissions
 * that the user has set for them.
 */
public class WebsitePermissionsFetcher {
    /**
     * A callback to pass to WebsitePermissionsFetcher. This is run when the
     * website permissions have been fetched.
     */
    public interface WebsitePermissionsCallback {
        void onWebsitePermissionsAvailable(
                Map<String, Set<Website>> sitesByOrigin, Map<String, Set<Website>> sitesByHost);
    }

    // This is a 1 <--> 1..N mapping between origin and Website.
    // TODO(mvanouwerkerk): The Website class has no equals or hashCode methods so storing them in
    // a HashSet is really confusing to readers of this code. There is no deduplication at all.
    private final Map<String, Set<Website>> mSitesByOrigin = new HashMap<>();

    // This is a 1 <--> 1..N mapping between host and Website.
    // TODO(mvanouwerkerk): The Website class has no equals or hashCode methods so storing them in
    // a HashSet is really confusing to readers of this code. There is no deduplication at all.
    private final Map<String, Set<Website>> mSitesByHost = new HashMap<>();

    // The callback to run when the permissions have been fetched.
    private final WebsitePermissionsCallback mCallback;

    /**
     * @param callback The callback to run when the fetch is complete.
     */
    public WebsitePermissionsFetcher(WebsitePermissionsCallback callback) {
        mCallback = callback;
    }

    /**
     * Fetches preferences for all sites that have them.
     * TODO(mvanouwerkerk): Add an argument |url| to only fetch permissions for
     * sites from the same origin as that of |url| - https://crbug.com/459222.
     */
    public void fetchAllPreferences() {
        TaskQueue queue = new TaskQueue();
        // Populate features from more specific to less specific.
        // Geolocation lookup permission is per-origin and per-embedder.
        queue.add(new GeolocationInfoFetcher());
        // Midi sysex access permission is per-origin and per-embedder.
        queue.add(new MidiInfoFetcher());
        // Cookies are stored per-origin.
        queue.add(new CookieInfoFetcher());
        // Fullscreen are stored per-origin.
        queue.add(new FullscreenInfoFetcher());
        // Local storage info is per-origin.
        queue.add(new LocalStorageInfoFetcher());
        // Website storage is per-host.
        queue.add(new WebStorageInfoFetcher());
        // Popup exceptions are host-based patterns (unless we start
        // synchronizing popup exceptions with desktop Chrome.)
        queue.add(new PopupExceptionInfoFetcher());
        // JavaScript exceptions are host-based patterns.
        queue.add(new JavaScriptExceptionInfoFetcher());
        // Protected media identifier permission is per-origin and per-embedder.
        queue.add(new ProtectedMediaIdentifierInfoFetcher());
        // Push notification permission is per-origin and per-embedder.
        queue.add(new PushNotificationInfoFetcher());
        // Camera capture permission is per-origin and per-embedder.
        queue.add(new CameraCaptureInfoFetcher());
        // Micropohone capture permission is per-origin and per-embedder.
        queue.add(new MicrophoneCaptureInfoFetcher());
        queue.add(new PermissionsAvailableCallbackRunner());
        queue.next();
    }

    /**
     * Fetches all preferences within a specific category.
     *
     * @param catgory A category to fetch.
     */
    public void fetchPreferencesForCategory(SiteSettingsCategory category) {
        if (category.showAllSites()) {
            fetchAllPreferences();
            return;
        }

        TaskQueue queue = new TaskQueue();
        // Populate features from more specific to less specific.
        if (category.showGeolocationSites()) {
            // Geolocation lookup permission is per-origin and per-embedder.
            queue.add(new GeolocationInfoFetcher());
        } else if (category.showCookiesSites()) {
            // Cookies are stored per-origin.
            queue.add(new CookieInfoFetcher());
        } else if (category.showStorageSites()) {
            // Local storage info is per-origin.
            queue.add(new LocalStorageInfoFetcher());
            // Website storage is per-host.
            queue.add(new WebStorageInfoFetcher());
        } else if (category.showFullscreenSites()) {
            // Full screen is per-origin.
            queue.add(new FullscreenInfoFetcher());
        } else if (category.showCameraSites()) {
            // Camera capture permission is per-origin and per-embedder.
            queue.add(new CameraCaptureInfoFetcher());
        } else if (category.showMicrophoneSites()) {
            // Micropohone capture permission is per-origin and per-embedder.
            queue.add(new MicrophoneCaptureInfoFetcher());
        } else if (category.showPopupSites()) {
            // Popup exceptions are host-based patterns (unless we start
            // synchronizing popup exceptions with desktop Chrome.)
            queue.add(new PopupExceptionInfoFetcher());
        } else if (category.showJavaScriptSites()) {
            // JavaScript exceptions are host-based patterns.
            queue.add(new JavaScriptExceptionInfoFetcher());
        } else if (category.showNotificationsSites()) {
            // Push notification permission is per-origin and per-embedder.
            queue.add(new PushNotificationInfoFetcher());
        } else if (category.showProtectedMediaSites()) {
            // Protected media identifier permission is per-origin and per-embedder.
            queue.add(new ProtectedMediaIdentifierInfoFetcher());
        }
        queue.add(new PermissionsAvailableCallbackRunner());
        queue.next();
    }

    private Website createSiteByOriginAndHost(WebsiteAddress address) {
        String origin = address.getOrigin();
        String host = address.getHost();
        Website site = new Website(address);
        if (!mSitesByOrigin.containsKey(origin)) mSitesByOrigin.put(origin, new HashSet<Website>());
        mSitesByOrigin.get(origin).add(site);
        if (!mSitesByHost.containsKey(host)) mSitesByHost.put(host, new HashSet<Website>());
        mSitesByHost.get(host).add(site);
        return site;
    }

    private Set<Website> findOrCreateSitesByOrigin(WebsiteAddress address) {
        String origin = address.getOrigin();
        if (!mSitesByOrigin.containsKey(origin)) createSiteByOriginAndHost(address);
        return mSitesByOrigin.get(origin);
    }

    private Set<Website> findOrCreateSitesByHost(WebsiteAddress address) {
        String host = address.getHost();
        if (!mSitesByHost.containsKey(host)) {
            mSitesByHost.put(host, new HashSet<Website>());
            mSitesByHost.get(host).add(new Website(address));
        }
        return mSitesByHost.get(host);
    }

    /**
     * A single task in the WebsitePermissionsFetcher task queue. We need
     * fetching of features to be serialized, as we need to have all the origins
     * in place prior to populating the hosts.
     */
    private interface Task {
        void run(TaskQueue queue);
    }

    /**
     * A queue used to store the sequence of tasks to run to fetch the website
     * preferences. Each task is run sequentially (although the queue as a whole
     * is run asynchronously). Each task should call queue.next() at the end to
     * run the next task in the queue.
     */
    private static class TaskQueue extends LinkedList<Task> {
        void next() {
            if (!isEmpty()) removeFirst().run(this);
        }
    }

    private class GeolocationInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setGeolocationInfo(info);
            }
            queue.next();
        }
    }

    private class MidiInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (MidiInfo info : WebsitePreferenceBridge.getMidiInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setMidiInfo(info);
            }
            queue.next();
        }
    }

    private class PopupExceptionInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (ContentSettingException exception :
                    WebsitePreferenceBridge.getContentSettingsExceptions(
                            ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS)) {
                // The pattern "*" represents the default setting, not a
                // specific website.
                if (exception.getPattern().equals("*")) continue;
                WebsiteAddress address = WebsiteAddress.create(exception.getPattern());
                if (address == null) continue;
                Set<Website> sites = findOrCreateSitesByHost(address);
                for (Website site : sites) {
                    site.setPopupException(exception);
                }
            }
            queue.next();
        }
    }

    private class JavaScriptExceptionInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (ContentSettingException exception
                    : WebsitePreferenceBridge.getContentSettingsExceptions(
                            ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT)) {
                // The pattern "*" represents the default setting, not a specific website.
                if (exception.getPattern().equals("*")) continue;
                WebsiteAddress address = WebsiteAddress.create(exception.getPattern());
                if (address == null) continue;
                Set<Website> sites = findOrCreateSitesByHost(address);
                for (Website site : sites) {
                    site.setJavaScriptException(exception);
                }
            }
            queue.next();
        }
    }

    private class CookieInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (CookieInfo info : WebsitePreferenceBridge.getCookieInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setCookieInfo(info);
            }
            queue.next();
        }
    }

    /**
     * Class for fetching the fullscreen information.
     */
    private class FullscreenInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (FullscreenInfo info : WebsitePreferenceBridge.getFullscreenInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setFullscreenInfo(info);
            }
            queue.next();
        }
    }

    private class LocalStorageInfoFetcher implements Task {
        @Override
        public void run(final TaskQueue queue) {
            WebsitePreferenceBridge.fetchLocalStorageInfo(
                    new WebsitePreferenceBridge.LocalStorageInfoReadyCallback() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public void onLocalStorageInfoReady(HashMap map) {
                            for (Object o : map.entrySet()) {
                                Map.Entry<String, LocalStorageInfo> entry =
                                        (Map.Entry<String, LocalStorageInfo>) o;
                                WebsiteAddress address = WebsiteAddress.create(entry.getKey());
                                if (address == null) continue;
                                Set<Website> sites = findOrCreateSitesByOrigin(address);
                                for (Website site : sites) {
                                    site.setLocalStorageInfo(entry.getValue());
                                }
                            }
                            queue.next();
                        }
                    });
        }
    }

    private class WebStorageInfoFetcher implements Task {
        @Override
        public void run(final TaskQueue queue) {
            WebsitePreferenceBridge.fetchStorageInfo(
                    new WebsitePreferenceBridge.StorageInfoReadyCallback() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public void onStorageInfoReady(ArrayList array) {
                            ArrayList<StorageInfo> infoArray = array;
                            for (StorageInfo info : infoArray) {
                                WebsiteAddress address = WebsiteAddress.create(info.getHost());
                                if (address == null) continue;
                                Set<Website> sites = findOrCreateSitesByHost(address);
                                for (Website site : sites) {
                                    site.addStorageInfo(info);
                                }
                            }
                            queue.next();
                        }
                    });
        }
    }

    private class ProtectedMediaIdentifierInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (ProtectedMediaIdentifierInfo info :
                    WebsitePreferenceBridge.getProtectedMediaIdentifierInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setProtectedMediaIdentifierInfo(info);
            }
            queue.next();
        }
    }

    private class PushNotificationInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (PushNotificationInfo info : WebsitePreferenceBridge.getPushNotificationInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setPushNotificationInfo(info);
            }
            queue.next();
        }
    }

    private class CameraCaptureInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (CameraInfo info : WebsitePreferenceBridge.getCameraInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setCameraInfo(info);
            }
            queue.next();
        }
    }

    private class MicrophoneCaptureInfoFetcher implements Task {
        @Override
        public void run(TaskQueue queue) {
            for (MicrophoneInfo info : WebsitePreferenceBridge.getMicrophoneInfo()) {
                WebsiteAddress address = WebsiteAddress.create(info.getOrigin());
                if (address == null) continue;
                createSiteByOriginAndHost(address).setMicrophoneInfo(info);
            }
            queue.next();
        }
    }

    private class PermissionsAvailableCallbackRunner implements Task {
        @Override
        public void run(TaskQueue queue) {
            mCallback.onWebsitePermissionsAvailable(mSitesByOrigin, mSitesByHost);
            queue.next();
        }
    }
}
