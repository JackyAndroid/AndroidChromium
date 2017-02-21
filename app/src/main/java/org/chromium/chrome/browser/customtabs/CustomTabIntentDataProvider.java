// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.widget.RemoteViews;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.TintedDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * A model class that parses intent from third-party apps and provides results to
 * {@link CustomTabActivity}.
 */
public class CustomTabIntentDataProvider {
    private static final String TAG = "CustomTabIntentData";

    /**
     * Extra used to keep the caller alive. Its value is an Intent.
     */
    public static final String EXTRA_KEEP_ALIVE = "android.support.customtabs.extra.KEEP_ALIVE";

    /**
     * Herb: Extra that indicates whether or not the Custom Tab is being launched by an Intent fired
     * by Chrome itself.
     */
    public static final String EXTRA_IS_OPENED_BY_CHROME =
            "org.chromium.chrome.browser.customtabs.IS_OPENED_BY_CHROME";

    /** Indicates that the Custom Tab should style itself as a media viewer. */
    public static final String EXTRA_IS_MEDIA_VIEWER =
            "org.chromium.chrome.browser.customtabs.IS_MEDIA_VIEWER";

    //TODO(yusufo): Move this to CustomTabsIntent.
    /** Signals custom tabs to favor sending initial urls to external handler apps if possible. */
    public static final String EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER =
            "android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER";

    private static final int MAX_CUSTOM_MENU_ITEMS = 5;
    private static final String ANIMATION_BUNDLE_PREFIX =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? "android:activity." : "android:";
    private static final String BUNDLE_PACKAGE_NAME = ANIMATION_BUNDLE_PREFIX + "packageName";
    private static final String BUNDLE_ENTER_ANIMATION_RESOURCE =
            ANIMATION_BUNDLE_PREFIX + "animEnterRes";
    private static final String BUNDLE_EXIT_ANIMATION_RESOURCE =
            ANIMATION_BUNDLE_PREFIX + "animExitRes";

    private final CustomTabsSessionToken mSession;
    private final Intent mKeepAliveServiceIntent;
    private final int mTitleVisibilityState;
    private final boolean mIsMediaViewer;

    private int mToolbarColor;
    private int mBottomBarColor;
    private boolean mEnableUrlBarHiding;
    private List<CustomButtonParams> mCustomButtonParams;
    private Drawable mCloseButtonIcon;
    private List<Pair<String, PendingIntent>> mMenuEntries = new ArrayList<>();
    private Bundle mAnimationBundle;
    private boolean mShowShareItem;
    private CustomButtonParams mToolbarButton;
    private List<CustomButtonParams> mBottombarButtons = new ArrayList<>(2);
    private RemoteViews mRemoteViews;
    private int[] mClickableViewIds;
    private PendingIntent mRemoteViewsPendingIntent;
    // OnFinished listener for PendingIntents. Used for testing only.
    private PendingIntent.OnFinished mOnFinished;

    /** Herb: Whether this CustomTabActivity was explicitly started by another Chrome Activity. */
    private boolean mIsOpenedByChrome;

    /**
     * Constructs a {@link CustomTabIntentDataProvider}.
     */
    public CustomTabIntentDataProvider(Intent intent, Context context) {
        if (intent == null) assert false;
        mSession = CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        parseHerbExtras(intent, context);

        retrieveCustomButtons(intent, context);
        retrieveToolbarColor(intent, context);
        retrieveBottomBarColor(intent);
        mEnableUrlBarHiding = IntentUtils.safeGetBooleanExtra(
                intent, CustomTabsIntent.EXTRA_ENABLE_URLBAR_HIDING, true);
        mKeepAliveServiceIntent = IntentUtils.safeGetParcelableExtra(intent, EXTRA_KEEP_ALIVE);

        Bitmap bitmap = IntentUtils.safeGetParcelableExtra(intent,
                CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON);
        if (bitmap != null && !checkCloseButtonSize(context, bitmap)) {
            bitmap.recycle();
            bitmap = null;
        }
        if (bitmap == null) {
            mCloseButtonIcon = TintedDrawable.constructTintedDrawable(context.getResources(),
                    R.drawable.btn_close);
        } else {
            mCloseButtonIcon = new BitmapDrawable(context.getResources(), bitmap);
        }

        List<Bundle> menuItems =
                IntentUtils.getParcelableArrayListExtra(intent, CustomTabsIntent.EXTRA_MENU_ITEMS);
        if (menuItems != null) {
            for (int i = 0; i < Math.min(MAX_CUSTOM_MENU_ITEMS, menuItems.size()); i++) {
                Bundle bundle = menuItems.get(i);
                String title =
                        IntentUtils.safeGetString(bundle, CustomTabsIntent.KEY_MENU_ITEM_TITLE);
                PendingIntent pendingIntent =
                        IntentUtils.safeGetParcelable(bundle, CustomTabsIntent.KEY_PENDING_INTENT);
                if (TextUtils.isEmpty(title) || pendingIntent == null) continue;
                mMenuEntries.add(new Pair<String, PendingIntent>(title, pendingIntent));
            }
        }

        mAnimationBundle = IntentUtils.safeGetBundleExtra(
                intent, CustomTabsIntent.EXTRA_EXIT_ANIMATION_BUNDLE);
        mTitleVisibilityState = IntentUtils.safeGetIntExtra(intent,
                CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE);
        mShowShareItem = IntentUtils.safeGetBooleanExtra(intent,
                CustomTabsIntent.EXTRA_DEFAULT_SHARE_MENU_ITEM, false);
        mRemoteViews = IntentUtils.safeGetParcelableExtra(intent,
                CustomTabsIntent.EXTRA_REMOTEVIEWS);
        mClickableViewIds = IntentUtils.safeGetIntArrayExtra(intent,
                CustomTabsIntent.EXTRA_REMOTEVIEWS_VIEW_IDS);
        mRemoteViewsPendingIntent = IntentUtils.safeGetParcelableExtra(intent,
                CustomTabsIntent.EXTRA_REMOTEVIEWS_PENDINGINTENT);
        mIsMediaViewer = IntentHandler.isIntentChromeOrFirstParty(intent, context)
                && IntentUtils.safeGetBooleanExtra(intent, EXTRA_IS_MEDIA_VIEWER, false);
    }

    /**
     * Gets custom buttons from the intent and updates {@link #mCustomButtonParams},
     * {@link #mBottombarButtons} and {@link #mToolbarButton}.
     */
    private void retrieveCustomButtons(Intent intent, Context context) {
        mCustomButtonParams = CustomButtonParams.fromIntent(context, intent);
        if (mCustomButtonParams != null) {
            for (CustomButtonParams params : mCustomButtonParams) {
                if (params.showOnToolbar()) {
                    mToolbarButton = params;
                } else {
                    mBottombarButtons.add(params);
                }
            }
        }
    }

    /**
     * Processes the color passed from the client app and updates {@link #mToolbarColor}.
     */
    private void retrieveToolbarColor(Intent intent, Context context) {
        int defaultColor = ApiCompatibilityUtils.getColor(context.getResources(),
                R.color.default_primary_color);
        int color = IntentUtils.safeGetIntExtra(intent, CustomTabsIntent.EXTRA_TOOLBAR_COLOR,
                defaultColor);
        mToolbarColor = removeTransparencyFromColor(color, defaultColor);
    }

    /**
     * Must be called after calling {@link #retrieveToolbarColor(Intent, Context)}.
     */
    private void retrieveBottomBarColor(Intent intent) {
        int defaultColor = mToolbarColor;
        int color = IntentUtils.safeGetIntExtra(intent,
                CustomTabsIntent.EXTRA_SECONDARY_TOOLBAR_COLOR, defaultColor);
        mBottomBarColor = removeTransparencyFromColor(color, defaultColor);
    }

    /**
     * Removes the alpha channel of the given color and returns the processed value. If the result
     * is blank, returns the fallback value.
     */
    private int removeTransparencyFromColor(int color, int fallbackColor) {
        color |= 0xFF000000;
        if (color == Color.TRANSPARENT) color = fallbackColor;
        return color;
    }

    /**
     * @return The session specified in the intent, or null.
     */
    public CustomTabsSessionToken getSession() {
        return mSession;
    }

    /**
     * @return The keep alive service intent specified in the intent, or null.
     */
    public Intent getKeepAliveServiceIntent() {
        return mKeepAliveServiceIntent;
    }

    /**
     * @return Whether url bar hiding should be enabled in the custom tab. Default is false.
     */
    public boolean shouldEnableUrlBarHiding() {
        return mEnableUrlBarHiding;
    }

    /**
     * @return The toolbar color specified in the intent. Will return the color of
     *         default_primary_color, if not set in the intent.
     */
    public int getToolbarColor() {
        return mToolbarColor;
    }

    /**
     * @return The drawable of the icon of close button shown in the custom tab toolbar. If the
     *         client app provides an icon in valid size, use this icon; else return the default
     *         drawable.
     */
    public Drawable getCloseButtonDrawable() {
        return mCloseButtonIcon;
    }

    /**
     * @return The title visibility state for the toolbar.
     *         Default is {@link CustomTabsIntent#NO_TITLE}.
     */
    public int getTitleVisibilityState() {
        return mTitleVisibilityState;
    }

    /**
     * @return Whether the default share item should be shown in the menu.
     */
    public boolean shouldShowShareMenuItem() {
        return mShowShareItem;
    }

    /**
     * @return The params for the custom button that shows on the toolbar. If there is no applicable
     *         buttons, returns null.
     */
    public CustomButtonParams getCustomButtonOnToolbar() {
        return mToolbarButton;
    }

    /**
     * @return The list of params representing the buttons on the bottombar.
     */
    public List<CustomButtonParams> getCustomButtonsOnBottombar() {
        return mBottombarButtons;
    }

    /**
     * @return Whether the bottom bar should be shown.
     */
    public boolean shouldShowBottomBar() {
        return !mBottombarButtons.isEmpty() || mRemoteViews != null;
    }

    /**
     * @return The color of the bottom bar, or {@link #getToolbarColor()} if not specified.
     */
    public int getBottomBarColor() {
        return mBottomBarColor;
    }

    /**
     * @return The {@link RemoteViews} to show on the bottom bar, or null if the extra is not
     *         specified.
     */
    public RemoteViews getBottomBarRemoteViews() {
        return mRemoteViews;
    }

    /**
     * @return A array of {@link View} ids, of which the onClick event is handled by the custom tab.
     */
    public int[] getClickableViewIDs() {
        return mClickableViewIds.clone();
    }

    /**
     * @return The {@link PendingIntent} that is sent when the user clicks on the remote view.
     */
    public PendingIntent getRemoteViewsPendingIntent() {
        return mRemoteViewsPendingIntent;
    }

    /**
     * Gets params for all custom buttons, which is the combination of
     * {@link #getCustomButtonsOnBottombar()} and {@link #getCustomButtonOnToolbar()}.
     */
    public List<CustomButtonParams> getAllCustomButtons() {
        return mCustomButtonParams;
    }

    /**
     * @return The {@link CustomButtonParams} having the given id. Returns null if no such params
     *         can be found.
     */
    public CustomButtonParams getButtonParamsForId(int id) {
        for (CustomButtonParams params : mCustomButtonParams) {
            // A custom button params will always carry an ID. If the client calls updateVisuals()
            // without an id, we will assign the toolbar action button id to it.
            if (id == params.getId()) return params;
        }
        return null;
    }

    /**
     * @return Titles of menu items that were passed from client app via intent.
     */
    public List<String> getMenuTitles() {
        ArrayList<String> list = new ArrayList<>();
        for (Pair<String, PendingIntent> pair : mMenuEntries) {
            list.add(pair.first);
        }
        return list;
    }

    /**
     * Triggers the client-defined action when the user clicks a custom menu item.
     * @param menuIndex The index that the menu item is shown in the result of
     *                  {@link #getMenuTitles()}
     */
    public void clickMenuItemWithUrl(ChromeActivity activity, int menuIndex, String url) {
        Intent addedIntent = new Intent();
        addedIntent.setData(Uri.parse(url));
        try {
            // Media viewers pass in PendingIntents that contain CHOOSER Intents.  Setting the data
            // in these cases prevents the Intent from firing correctly.
            PendingIntent pendingIntent = mMenuEntries.get(menuIndex).second;
            pendingIntent.send(
                    activity, 0, isMediaViewer() ? null : addedIntent, mOnFinished, null);
        } catch (CanceledException e) {
            Log.e(TAG, "Custom tab in Chrome failed to send pending intent.");
        }
    }

    /**
     * @return Whether chrome should animate when it finishes. We show animations only if the client
     *         app has supplied the correct animation resources via intent extra.
     */
    public boolean shouldAnimateOnFinish() {
        return mAnimationBundle != null && getClientPackageName() != null;
    }

    /**
     * @return The package name of the client app. This is used for a workaround in order to
     *         retrieve the client's animation resources.
     */
    public String getClientPackageName() {
        if (mAnimationBundle == null) return null;
        return mAnimationBundle.getString(BUNDLE_PACKAGE_NAME);
    }

    /**
     * @return The resource id for enter animation, which is used in
     *         {@link Activity#overridePendingTransition(int, int)}.
     */
    public int getAnimationEnterRes() {
        return shouldAnimateOnFinish() ? mAnimationBundle.getInt(BUNDLE_ENTER_ANIMATION_RESOURCE)
                : 0;
    }

    /**
     * @return The resource id for exit animation, which is used in
     *         {@link Activity#overridePendingTransition(int, int)}.
     */
    public int getAnimationExitRes() {
        return shouldAnimateOnFinish() ? mAnimationBundle.getInt(BUNDLE_EXIT_ANIMATION_RESOURCE)
                : 0;
    }

    /**
     * Sends the pending intent for the custom button on toolbar with the given url as data.
     * @param context The context to use for sending the {@link PendingIntent}.
     * @param url The url to attach as additional data to the {@link PendingIntent}.
     */
    public void sendButtonPendingIntentWithUrl(Context context, String url) {
        Intent addedIntent = new Intent();
        addedIntent.setData(Uri.parse(url));
        try {
            getCustomButtonOnToolbar().getPendingIntent().send(context, 0, addedIntent, mOnFinished,
                    null);
        } catch (CanceledException e) {
            Log.e(TAG, "CanceledException while sending pending intent in custom tab");
        }
    }

    private boolean checkCloseButtonSize(Context context, Bitmap bitmap) {
        int size = context.getResources().getDimensionPixelSize(R.dimen.toolbar_icon_height);
        if (bitmap.getHeight() == size && bitmap.getWidth() == size) return true;
        return false;
    }

    /**
     * Set the callback object for {@link PendingIntent}s that are sent in this class. For testing
     * purpose only.
     */
    @VisibleForTesting
    void setPendingIntentOnFinishedForTesting(PendingIntent.OnFinished onFinished) {
        mOnFinished = onFinished;
    }

    /**
     * @return See {@link #EXTRA_IS_OPENED_BY_CHROME}.
     */
    boolean isOpenedByChrome() {
        return mIsOpenedByChrome;
    }

    /**
     * @return See {@link #EXTRA_IS_MEDIA_VIEWER}.
     */
    boolean isMediaViewer() {
        return mIsMediaViewer;
    }

    /**
     * Parses out extras specifically added for Herb.
     *
     * @param intent Intent fired to open the CustomTabActivity.
     * @param context Context for the package.
     */
    private void parseHerbExtras(Intent intent, Context context) {
        String herbFlavor = FeatureUtilities.getHerbFlavor();
        if (TextUtils.isEmpty(herbFlavor)
                || TextUtils.equals(ChromeSwitches.HERB_FLAVOR_DISABLED, herbFlavor)) {
            return;
        }

        mIsOpenedByChrome = IntentUtils.safeGetBooleanExtra(
                intent, EXTRA_IS_OPENED_BY_CHROME, false);
    }
}
