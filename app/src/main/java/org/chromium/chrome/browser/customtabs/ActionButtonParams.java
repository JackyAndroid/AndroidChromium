// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.TintedDrawable;

/**
 * Container for all parameters related to creating a custom action button.
 */
/* package */ class ActionButtonParams {
    private static final String TAG = "cr_CustomTabs";

    private Bitmap mIcon;
    private String mDescription;
    private final PendingIntent mPendingIntent;
    private boolean mShouldTint;

    private ActionButtonParams(@NonNull Bitmap icon, @NonNull String description,
            @NonNull PendingIntent pendingIntent) {
        mIcon = icon;
        mDescription = description;
        mPendingIntent = pendingIntent;
    }

    /**
     * Replaces the current icon and description with new ones.
     */
    void update(@NonNull Bitmap icon, @NonNull String description) {
        mIcon = icon;
        mDescription = description;
    }

    /**
     * Sets whether the action button icon should be tinted.
     */
    void setTinted(boolean shouldTint) {
        mShouldTint = shouldTint;
    }

    /**
     * @return The drawable for the action button. Will be a {@link TintedDrawable} if in the VIEW
     *         intent the client required to tint it.
     */
    Drawable getIcon(Resources res) {
        Drawable drawable = null;
        if (mShouldTint) {
            drawable = TintedDrawable.constructTintedDrawable(res, mIcon);
        } else {
            drawable = new BitmapDrawable(res, mIcon);
        }
        return drawable;
    }

    /**
     * @return The content description for the custom action button.
     */
    String getDescription() {
        return mDescription;
    }

    /**
    * @return The {@link PendingIntent} that will be sent when the user clicks the action button.
    */
    PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    /**
     * Parses an {@link ActionButtonParams} from an action button bundle sent by clients.
     * @param bundle The action button bundle specified by
     *               {@link CustomTabsIntent#EXTRA_ACTION_BUTTON_BUNDLE}
     * @return The parsed {@link ActionButtonParams}. Return null if input is invalid.
     */
    static ActionButtonParams fromBundle(Context context, Bundle bundle) {
        if (bundle == null) return null;

        Bitmap bitmap = tryParseBitmapFromBundle(context, bundle);
        if (bitmap == null) return null;

        String description = tryParseDescriptionFromBundle(bundle);
        if (TextUtils.isEmpty(description)) {
            bitmap.recycle();
            return null;
        }

        PendingIntent pi = IntentUtils.safeGetParcelable(bundle,
                CustomTabsIntent.KEY_PENDING_INTENT);
        if (pi == null) return null;
        return new ActionButtonParams(bitmap, description, pi);
    }

    /**
     * @return The bitmap contained in the given {@link Bundle}. Will return null if input is
     *         invalid.
     */
    static Bitmap tryParseBitmapFromBundle(Context context, Bundle bundle) {
        if (bundle == null) return null;
        Bitmap bitmap = IntentUtils.safeGetParcelable(bundle, CustomTabsIntent.KEY_ICON);
        if (bitmap == null) return null;
        if (!checkCustomButtonIconWithinBounds(context, bitmap)) {
            Log.w(TAG, "Action button's icon size not acceptable. Please refer to "
                    + "https://developer.android.com/reference/android/support/customtabs/"
                    + "CustomTabsIntent.html#KEY_ICON");
            bitmap.recycle();
            return null;
        }
        return bitmap;
    }

    /**
     * @return The content description contained in the given {@link Bundle}. Will return null if
     *         input is invalid.
     */
    static String tryParseDescriptionFromBundle(Bundle bundle) {
        String description = IntentUtils.safeGetString(bundle, CustomTabsIntent.KEY_DESCRIPTION);
        if (TextUtils.isEmpty(description)) return null;
        return description;
    }

    private static boolean checkCustomButtonIconWithinBounds(Context context, Bitmap bitmap) {
        int height = context.getResources().getDimensionPixelSize(R.dimen.toolbar_icon_height);
        if (bitmap.getHeight() < height) return false;
        int scaledWidth = bitmap.getWidth() / bitmap.getHeight() * height;
        if (scaledWidth > 2 * height) return false;
        return true;
    }
}