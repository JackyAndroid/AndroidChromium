/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.customtabs;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsService.Result;
import android.support.customtabs.CustomTabsSessionToken.DummyCallback;
import android.widget.RemoteViews;

import java.util.List;

/**
 * A class to be used for Custom Tabs related communication. Clients that want to launch Custom Tabs
 * can use this class exclusively to handle all related communication.
 */
public final class CustomTabsSession {
    private static final String TAG = "CustomTabsSession";
    private final ICustomTabsService mService;
    private final ICustomTabsCallback mCallback;
    private final ComponentName mComponentName;

    /**
     * Provides browsers a way to generate a dummy {@link CustomTabsSession} for testing
     * purposes.
     *
     * @param componentName The component the session should be created for.
     * @return A dummy session with no functionality.
     */
    public static CustomTabsSession createDummySessionForTesting(ComponentName componentName) {
        return new CustomTabsSession(null, new DummyCallback(), componentName);
    }

    /* package */ CustomTabsSession(
            ICustomTabsService service, ICustomTabsCallback callback, ComponentName componentName) {
        mService = service;
        mCallback = callback;
        mComponentName = componentName;
    }

    /**
     * Tells the browser of a likely future navigation to a URL.
     * The most likely URL has to be specified first. Optionally, a list of
     * other likely URLs can be provided. They are treated as less likely than
     * the first one, and have to be sorted in decreasing priority order. These
     * additional URLs may be ignored.
     * All previous calls to this method will be deprioritized.
     *
     * @param url                Most likely URL.
     * @param extras             Reserved for future use.
     * @param otherLikelyBundles Other likely destinations, sorted in decreasing
     *                           likelihood order. Inside each Bundle, the client should provide a
     *                           {@link Uri} using {@link CustomTabsService#KEY_URL} with
     *                           {@link Bundle#putParcelable(String, android.os.Parcelable)}.
     * @return                   true for success.
     */
    public boolean mayLaunchUrl(Uri url, Bundle extras, List<Bundle> otherLikelyBundles) {
        try {
            return mService.mayLaunchUrl(mCallback, url, extras, otherLikelyBundles);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * This sets the action button on the toolbar with ID
     * {@link CustomTabsIntent#TOOLBAR_ACTION_BUTTON_ID}.
     *
     * @param icon          The new icon of the action button.
     * @param description   Content description of the action button.
     *
     * @see {@link CustomTabsSession#setToolbarItem(int, Bitmap, String)}
     */
    public boolean setActionButton(@NonNull Bitmap icon, @NonNull String description) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(CustomTabsIntent.KEY_ICON, icon);
        bundle.putString(CustomTabsIntent.KEY_DESCRIPTION, description);

        Bundle metaBundle = new Bundle();
        metaBundle.putBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, bundle);
        try {
            return mService.updateVisuals(mCallback, metaBundle);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Updates the {@link RemoteViews} of the secondary toolbar in an existing custom tab session.
     * @param remoteViews   The updated {@link RemoteViews} that will be shown in secondary toolbar.
     * @param clickableIDs  The ids of clickable views. The onClick event of these views will be
     *                      handled by custom tabs.
     * @param pendingIntent The {@link PendingIntent} that will be sent when the user clicks on one
     *                      of the {@link View}s in clickableIDs.
     */
    public boolean setSecondaryToolbar(RemoteViews remoteViews, int[] clickableIDs,
                                       PendingIntent pendingIntent) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(CustomTabsIntent.EXTRA_REMOTEVIEWS, remoteViews);
        bundle.putIntArray(CustomTabsIntent.EXTRA_REMOTEVIEWS_VIEW_IDS, clickableIDs);
        bundle.putParcelable(CustomTabsIntent.EXTRA_REMOTEVIEWS_PENDINGINTENT, pendingIntent);
        try {
            return mService.updateVisuals(mCallback, bundle);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the visuals for toolbar items. Will only succeed if a custom tab created using this
     * session is in the foreground in browser and the given id is valid.
     * @param id            The id for the item to update.
     * @param icon          The new icon of the toolbar item.
     * @param description   Content description of the toolbar item.
     * @return              Whether the update succeeded.
     */
    @Deprecated
    public boolean setToolbarItem(int id, @NonNull Bitmap icon, @NonNull String description) {
        Bundle bundle = new Bundle();
        bundle.putInt(CustomTabsIntent.KEY_ID, id);
        bundle.putParcelable(CustomTabsIntent.KEY_ICON, icon);
        bundle.putString(CustomTabsIntent.KEY_DESCRIPTION, description);

        Bundle metaBundle = new Bundle();
        metaBundle.putBundle(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, bundle);
        try {
            return mService.updateVisuals(mCallback, metaBundle);
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean validatePostMessageOrigin() {
        try {
            return mService.validatePostMessageOrigin(mCallback);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Result
    public synchronized int postMessage(String message, Bundle extras) {
        try {
            return mService.postMessage(mCallback, message, extras);
        } catch (RemoteException e) {
            return CustomTabsService.RESULT_FAILURE_REMOTE_ERROR;
        }
    }

    /* package */ IBinder getBinder() {
        return mCallback.asBinder();
    }

    /* package */ ComponentName getComponentName() {
        return mComponentName;
    }
}
