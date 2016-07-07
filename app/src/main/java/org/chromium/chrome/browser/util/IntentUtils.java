// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.BundleCompat;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities dealing with extracting information from intents.
 */
public class IntentUtils {
    private static final String TAG = "cr_IntentUtils";

    /** See {@link #isIntentTooLarge(Intent)}. */
    private static final int MAX_INTENT_SIZE_THRESHOLD = 750000;

    /**
     * Retrieves a list of components that would handle the given intent.
     * @param context The application context.
     * @param intent The intent which we are interested in.
     * @return The list of component names.
     */
    public static List<ComponentName> getIntentHandlers(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, 0);
        List<ComponentName> nameList = new ArrayList<ComponentName>();
        for (ResolveInfo r : list) {
            nameList.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
        }
        return nameList;
    }

    /**
     * Just like {@link Intent#getBooleanExtra(String, boolean)} but doesn't throw exceptions.
     */
    public static boolean safeGetBooleanExtra(Intent intent, String name, boolean defaultValue) {
        try {
            return intent.getBooleanExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBooleanExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getIntExtra(String, int)} but doesn't throw exceptions.
     */
    public static int safeGetIntExtra(Intent intent, String name, int defaultValue) {
        try {
            return intent.getIntExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getIntExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getLongExtra(String, long)} but doesn't throw exceptions.
     */
    public static long safeGetLongExtra(Intent intent, String name, long defaultValue) {
        try {
            return intent.getLongExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getLongExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getStringExtra(String)} but doesn't throw exceptions.
     */
    public static String safeGetStringExtra(Intent intent, String name) {
        try {
            return intent.getStringExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getStringExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Bundle#getString(String)} but doesn't throw exceptions.
     */
    public static String safeGetString(Bundle bundle, String name) {
        try {
            return bundle.getString(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getString failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getBundleExtra(String)} but doesn't throw exceptions.
     */
    public static Bundle safeGetBundleExtra(Intent intent, String name) {
        try {
            return intent.getBundleExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBundleExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Bundle#getBundle(String)} but doesn't throw exceptions.
     */
    public static Bundle safeGetBundle(Bundle bundle, String name) {
        try {
            return bundle.getBundle(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBundle failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * Just like {@link Bundle#getParcelable(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> T safeGetParcelable(Bundle bundle, String name) {
        try {
            return bundle.getParcelable(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelable failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getParcelableExtra(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> T safeGetParcelableExtra(Intent intent, String name) {
        try {
            return intent.getParcelableExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelableExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just link {@link Intent#getParcelableArrayListExtra(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(
            Intent intent, String name) {
        try {
            return intent.getParcelableArrayListExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelableArrayListExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getStringArrayListExtra(String)} but doesn't throw exceptions.
     */
    public static ArrayList<String> safeGetStringArrayListExtra(Intent intent, String name) {
        try {
            return intent.getStringArrayListExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getStringArrayListExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getByteArrayExtra(String)} but doesn't throw exceptions.
     */
    public static byte[] safeGetByteArrayExtra(Intent intent, String name) {
        try {
            return intent.getByteArrayExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getByteArrayExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link BundleCompat#getBinder()}, but doesn't throw exceptions.
     */
    public static IBinder safeGetBinder(Bundle bundle, String name) {
        if (bundle == null) return null;
        try {
            return BundleCompat.getBinder(bundle, name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBinder failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * @return a Binder from an Intent, or null.
     *
     * Creates a temporary copy of the extra Bundle, which is required as
     * Intent#getBinderExtra() doesn't exist, but Bundle.getBinder() does.
     */
    public static IBinder safeGetBinderExtra(Intent intent, String name) {
        if (!intent.hasExtra(name)) return null;
        Bundle extras = intent.getExtras();
        return safeGetBinder(extras, name);
    }

    /**
     * Inserts a {@link Binder} value into an Intent as an extra.
     *
     * Uses {@link BundleCompat#putBinder()}, but doesn't throw exceptions.
     *
     * @param intent Intent to put the binder into.
     * @param name Key.
     * @param binder Binder object.
     */
    @VisibleForTesting
    public static void safePutBinderExtra(Intent intent, String name, IBinder binder) {
        if (intent == null) return;
        Bundle bundle = new Bundle();
        try {
            BundleCompat.putBinder(bundle, name, binder);
        } catch (Throwable t) {
            // Catches parceling exceptions.
            Log.e(TAG, "putBinder failed on bundle " + bundle);
        }
        intent.putExtras(bundle);
    }

    /**
     * Returns how large the Intent will be in Parcel form, which is helpful for gauging whether
     * Android will deliver the Intent instead of throwing a TransactionTooLargeException.
     *
     * @param intent Intent to get the size of.
     * @return Number of bytes required to parcel the Intent.
     */
    public static int getParceledIntentSize(Intent intent) {
        Parcel parcel = Parcel.obtain();
        intent.writeToParcel(parcel, 0);
        return parcel.dataSize();
    }

    /**
     * Determines if an Intent's size is bigger than a reasonable threshold.  Having too many large
     * transactions in flight simultaneously (including Intents) causes Android to throw a
     * {@link TransactionTooLargeException}.  According to that class, the limit across all
     * transactions combined is one megabyte.  Best practice is to keep each individual Intent well
     * under the limit to avoid this situation.
     */
    public static boolean isIntentTooLarge(Intent intent) {
        return getParceledIntentSize(intent) > MAX_INTENT_SIZE_THRESHOLD;
    }
}
