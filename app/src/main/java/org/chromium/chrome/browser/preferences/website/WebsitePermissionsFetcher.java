// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import android.util.Pair;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.ContentSettingsType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

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
        void onWebsitePermissionsAvailable(Collection<Website> sites);
    }

    // This map looks up Websites by their origin and embedder.
    private final Map<Pair<WebsiteAddress, WebsiteAddress>, Website> mSites = new HashMap<>();

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
        // Cookies are stored per-host.
        queue.add(new CookieExceptionInfoFetcher());
        // Fullscreen are stored per-origin.
        queue.add(new FullscreenInfoFetcher());
        // Keygen permissions are per-origin.
        queue.add(new KeygenInfoFetcher());
        // Local storage info is per-origin.
        queue.add(new LocalStorageInfoFetcher());
        // Website storage is per-host.
        queue.add(new WebStorageInfoFetcher());
        // Popup exceptions are host-based patterns (unless we start
        // synchronizing popup exceptions with desktop Chrome).
        queue.add(new PopupExceptionInfoFetcher());
        // JavaScript exceptions are host-based patterns.
        queue.add(new JavaScriptExceptionInfoFetcher());
        // Protected media identifier permission is per-origin and per-embedder.
        queue.add(new ProtectedMediaIdentifierInfoFetcher());
        // Notification permission is per-origin.
        queue.add(new NotificationInfoFetcher());
        // Camera capture permission is per-origin and per-embedder.
        queue.add(new CameraCaptureInfoFetcher());
        // Micropohone capture permission is per-origin and per-embedder.
        queue.add(new MicrophoneCaptureInfoFetcher());
        // Background sync permission is per-origin.
        queue.add(new BackgroundSyncExceptionInfoFetcher());
        // Autoplay permission is per-origin.
        queue.add(new AutoplayExceptionInfoFetcher());
        // USB device permission is per-origin and per-embedder.
        queue.add(new UsbInfoFetcher());

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
            // Cookies exceptions are patterns.
            queue.add(new CookieExceptionInfoFetcher());
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
            // Push notification permission is per-origin.
            queue.add(new NotificationInfoFetcher());
        } else if (category.showBackgroundSyncSites()) {
            // Background sync info is per-origin.
            queue.add(new BackgroundSyncExceptionInfoFetcher());
        } else if (category.showProtectedMediaSites()) {
            // Protected media identifier permission is per-origin and per-embedder.
            queue.add(new ProtectedMediaIdentifierInfoFetcher());
        } else if (category.showAutoplaySites()) {
            // Autoplay permission is per-origin.
            queue.add(new AutoplayExceptionInfoFetcher());
        } else if (category.showUsbDevices()) {
            // USB device permission is per-origin.
            queue.add(new UsbInfoFetcher());
        }
        queue.add(new PermissionsAvailableCallbackRunner());
        queue.next();
    }

    private Website findOrCreateSite(WebsiteAddress origin, WebsiteAddress embedder) {
        // In Jelly Bean a null value triggers a NullPointerException in Pair.hashCode(). Storing
        // the origin twice works around it and won't conflict with other entries as this is how the
        // native code indicates to this class that embedder == origin.  https://crbug.com/636330
        Pair<WebsiteAddress, WebsiteAddress> key =
                Pair.create(origin, embedder == null ? origin : embedder);
        Website site = mSites.get(key);
        if (site == null) {
            site = new Website(origin, embedder);
            mSites.put(key, site);
        }
        return site;
    }

    private void setException(int contentSettingsType) {
        for (ContentSettingException exception :
                WebsitePreferenceBridge.getContentSettingsExceptions(contentSettingsType)) {
            // The pattern "*" represents the default setting, not a specific website.
            if (exception.getPattern().equals("*")) continue;
            WebsiteAddress address = WebsiteAddress.create(exception.getPattern());
            if (address == null) continue;
            Website site = findOrCreateSite(address, null);
            switch (contentSettingsType) {
                case ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOPLAY:
                    site.setAutoplayException(exception);
                    break;
                case ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC:
                    site.setBackgroundSyncException(exception);
                    break;
                case ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES:
                    site.setCookieException(exception);
                    break;
                case ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT:
                    site.setJavaScriptException(exception);
                    break;
                case ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS:
                    site.setPopupException(exception);
                    break;
                default:
                    assert false : "Unexpected content setting type received: "
                                   + contentSettingsType;
                    break;
            }
        }
    }

    /**
     * A single task in the WebsitePermissionsFetcher task queue. We need fetching of features to be
     * serialized, as we need to have all the origins in place prior to populating the hosts.
     */
    private abstract class Task {
        /** Override this method to implement a synchronous task. */
        void run() {}

        /**
         * Override this method to implement an asynchronous task. Call queue.next() once execution
         * is complete.
         */
        void runAsync(TaskQueue queue) {
            run();
            queue.next();
        }
    }

    /**
     * A queue used to store the sequence of tasks to run to fetch the website preferences. Each
     * task is run sequentially, and some of the tasks may run asynchronously.
     */
    private static class TaskQueue extends LinkedList<Task> {
        void next() {
            if (!isEmpty()) removeFirst().runAsync(this);
        }
    }

    private class AutoplayExceptionInfoFetcher extends Task {
        @Override
        public void run() {
            setException(ContentSettingsType.CONTENT_SETTINGS_TYPE_AUTOPLAY);
        }
    }

    private class GeolocationInfoFetcher extends Task {
        @Override
        public void run() {
            for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setGeolocationInfo(info);
            }
        }
    }

    private class MidiInfoFetcher extends Task {
        @Override
        public void run() {
            for (MidiInfo info : WebsitePreferenceBridge.getMidiInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setMidiInfo(info);
            }
        }
    }

    private class PopupExceptionInfoFetcher extends Task {
        @Override
        public void run() {
            setException(ContentSettingsType.CONTENT_SETTINGS_TYPE_POPUPS);
        }
    }

    private class JavaScriptExceptionInfoFetcher extends Task {
        @Override
        public void run() {
            setException(ContentSettingsType.CONTENT_SETTINGS_TYPE_JAVASCRIPT);
        }
    }

    private class CookieExceptionInfoFetcher extends Task {
        @Override
        public void run() {
            setException(ContentSettingsType.CONTENT_SETTINGS_TYPE_COOKIES);
        }
    }

    private class KeygenInfoFetcher extends Task {
        @Override
        public void run() {
            for (KeygenInfo info : WebsitePreferenceBridge.getKeygenInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setKeygenInfo(info);
            }
        }
    }

    /**
     * Class for fetching the fullscreen information.
     */
    private class FullscreenInfoFetcher extends Task {
        @Override
        public void run() {
            for (FullscreenInfo info : WebsitePreferenceBridge.getFullscreenInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setFullscreenInfo(info);
            }
        }
    }

    private class LocalStorageInfoFetcher extends Task {
        @Override
        public void runAsync(final TaskQueue queue) {
            WebsitePreferenceBridge.fetchLocalStorageInfo(new Callback<HashMap>() {
                @Override
                public void onResult(HashMap result) {
                    for (Object o : result.entrySet()) {
                        @SuppressWarnings("unchecked")
                        Map.Entry<String, LocalStorageInfo> entry =
                                (Map.Entry<String, LocalStorageInfo>) o;
                        WebsiteAddress address = WebsiteAddress.create(entry.getKey());
                        if (address == null) continue;
                        findOrCreateSite(address, null).setLocalStorageInfo(entry.getValue());
                    }
                    queue.next();
                }
            });
        }
    }

    private class WebStorageInfoFetcher extends Task {
        @Override
        public void runAsync(final TaskQueue queue) {
            WebsitePreferenceBridge.fetchStorageInfo(new Callback<ArrayList>() {
                @Override
                public void onResult(ArrayList result) {
                    @SuppressWarnings("unchecked")
                    ArrayList<StorageInfo> infoArray = result;

                    for (StorageInfo info : infoArray) {
                        WebsiteAddress address = WebsiteAddress.create(info.getHost());
                        if (address == null) continue;
                        findOrCreateSite(address, null).addStorageInfo(info);
                    }
                    queue.next();
                }
            });
        }
    }

    private class ProtectedMediaIdentifierInfoFetcher extends Task {
        @Override
        public void run() {
            for (ProtectedMediaIdentifierInfo info :
                    WebsitePreferenceBridge.getProtectedMediaIdentifierInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setProtectedMediaIdentifierInfo(info);
            }
        }
    }

    private class NotificationInfoFetcher extends Task {
        @Override
        public void run() {
            for (NotificationInfo info : WebsitePreferenceBridge.getNotificationInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setNotificationInfo(info);
            }
        }
    }

    private class CameraCaptureInfoFetcher extends Task {
        @Override
        public void run() {
            for (CameraInfo info : WebsitePreferenceBridge.getCameraInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setCameraInfo(info);
            }
        }
    }

    private class MicrophoneCaptureInfoFetcher extends Task {
        @Override
        public void run() {
            for (MicrophoneInfo info : WebsitePreferenceBridge.getMicrophoneInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).setMicrophoneInfo(info);
            }
        }
    }

    private class BackgroundSyncExceptionInfoFetcher extends Task {
        @Override
        public void run() {
            setException(ContentSettingsType.CONTENT_SETTINGS_TYPE_BACKGROUND_SYNC);
        }
    }

    private class UsbInfoFetcher extends Task {
        @Override
        public void run() {
            for (UsbInfo info : WebsitePreferenceBridge.getUsbInfo()) {
                WebsiteAddress origin = WebsiteAddress.create(info.getOrigin());
                if (origin == null) continue;
                WebsiteAddress embedder = WebsiteAddress.create(info.getEmbedder());
                findOrCreateSite(origin, embedder).addUsbInfo(info);
            }
        }
    }

    private class PermissionsAvailableCallbackRunner extends Task {
        @Override
        public void run() {
            mCallback.onWebsitePermissionsAvailable(mSites.values());
        }
    }
}
