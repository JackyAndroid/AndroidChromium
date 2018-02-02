// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Browser;
import android.text.TextUtils;
import android.widget.ImageView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity2;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchUma;
import org.chromium.chrome.browser.contextualsearch.QuickActionCategory;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;
import org.chromium.ui.resources.dynamics.ViewResourceInflater;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores information related to a Contextual Search "quick action."
 */
public class ContextualSearchQuickActionControl extends ViewResourceInflater {
    private static final Map<Integer, Integer> ICON_MAP;
    static {
        Map<Integer, Integer> icons = new HashMap<>();
        icons.put(QuickActionCategory.ADDRESS, R.drawable.ic_place_googblue_36dp);
        icons.put(QuickActionCategory.EMAIL, R.drawable.ic_email_googblue_36dp);
        icons.put(QuickActionCategory.EVENT, R.drawable.ic_event_googblue_36dp);
        icons.put(QuickActionCategory.PHONE, R.drawable.ic_phone_googblue_36dp);
        ICON_MAP = Collections.unmodifiableMap(icons);
    }

    private static final Map<Integer, Integer> DEFAULT_APP_CAPTION_MAP;
    static {
        Map<Integer, Integer> captions = new HashMap<>();
        captions.put(QuickActionCategory.ADDRESS,
                R.string.contextual_search_quick_action_caption_open);
        captions.put(QuickActionCategory.EMAIL,
                R.string.contextual_search_quick_action_caption_email);
        captions.put(QuickActionCategory.EVENT,
                R.string.contextual_search_quick_action_caption_event);
        captions.put(QuickActionCategory.PHONE,
                R.string.contextual_search_quick_action_caption_phone);
        DEFAULT_APP_CAPTION_MAP = Collections.unmodifiableMap(captions);
    }

    private static final Map<Integer, Integer> FALLBACK_CAPTION_MAP;
    static {
        Map<Integer, Integer> captions = new HashMap<>();
        captions.put(QuickActionCategory.ADDRESS,
                R.string.contextual_search_quick_action_caption_generic_map);
        captions.put(QuickActionCategory.EMAIL,
                R.string.contextual_search_quick_action_caption_generic_email);
        captions.put(QuickActionCategory.EVENT,
                R.string.contextual_search_quick_action_caption_generic_event);
        captions.put(QuickActionCategory.PHONE,
                R.string.contextual_search_quick_action_caption_phone);
        FALLBACK_CAPTION_MAP = Collections.unmodifiableMap(captions);
    }

    private Context mContext;
    private String mQuickActionUri;
    private int mQuickActionCategory;
    private boolean mHasQuickAction;
    private Intent mIntent;
    private String mCaption;

    /**
     * @param context The Android Context used to inflate the View.
     * @param resourceLoader The resource loader that will handle the snapshot capturing.
     */
    public ContextualSearchQuickActionControl(Context context,
            DynamicResourceLoader resourceLoader) {
        super(R.layout.contextual_search_quick_action_icon_view,
                R.id.contextual_search_quick_action_icon_view,
                context, null, resourceLoader);
        mContext = context;
    }

    /**
     * @param quickActionUri The URI for the intent associated with the quick action. If the URI is
     *                       the empty string or cannot be parsed no quick action will be available.
     * @param quickActionCategory The {@link QuickActionCategory} for the quick action.
     */
    public void setQuickAction(String quickActionUri, int quickActionCategory) {
        if (TextUtils.isEmpty(quickActionUri) || quickActionCategory == QuickActionCategory.NONE
                || quickActionCategory >= QuickActionCategory.BOUNDARY) {
            reset();
            return;
        }

        mQuickActionUri = quickActionUri;
        mQuickActionCategory = quickActionCategory;

        resolveIntent();
    }
    /**
     * Sends the intent associated with the quick action if one is available.
     */
    public void sendIntent() {
        if (mIntent == null) return;
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Set the Browser application ID to us in case the user chooses Chrome
        // as the app from the intent picker.
        Context context = getContext();
        mIntent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        mIntent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
        if (context instanceof ChromeTabbedActivity2) {
            // Set the window ID so the new tab opens in the correct window.
            mIntent.putExtra(IntentHandler.EXTRA_WINDOW_ID, 2);
        }

        IntentUtils.safeStartActivity(mContext, mIntent);
    }

    /**
     * @return The caption associated with the quick action or null if no quick action is
     *         available.
     */
    public String getCaption() {
        return mCaption;
    }

    /**
     * @return The resource id for the icon associated with the quick action or 0 if no quick
     *         action is available.
     */
    public int getIconResId() {
        return mHasQuickAction ? getViewId() : 0;
    }

    /**
     * @return Whether there is currently a quick action available.
     */
    public boolean hasQuickAction() {
        return mHasQuickAction;
    }

    /**
     * Resets quick action data.
     */
    public void reset() {
        mQuickActionUri = "";
        mQuickActionCategory = QuickActionCategory.NONE;
        mHasQuickAction = false;
        mIntent = null;
        mCaption = "";
    }

    @Override
    protected boolean shouldAttachView() {
        return false;
    }

    private void resolveIntent() {
        try {
            mIntent = Intent.parseUri(mQuickActionUri, 0);
        } catch (URISyntaxException e) {
            // If the intent cannot be parsed, there is no quick action available.
            ContextualSearchUma.logQuickActionIntentResolution(mQuickActionCategory, 0);
            reset();
            return;
        }

        PackageManager packageManager = mContext.getPackageManager();

        // If a default is set, PackageManager#resolveActivity() will return the ResolveInfo
        // for the default activity.
        ResolveInfo possibleDefaultActivity = packageManager.resolveActivity(mIntent, 0);

        // PackageManager#queryIntentActivities() will return a list of activities that can handle
        // the intent, sorted from best to worst. If there are no matching activities, an empty
        // list is returned.
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(
                mIntent, 0);

        int numMatchingActivities = 0;
        ResolveInfo defaultActivityResolveInfo = null;
        for (ResolveInfo resolveInfo : resolveInfoList) {
            // TODO(twellington): do not count browser activities as a match?
            if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.exported) {
                numMatchingActivities++;

                // Return early if this resolveInfo matches the possibleDefaultActivity.
                ActivityInfo possibleDefaultActivityInfo = possibleDefaultActivity.activityInfo;
                if (possibleDefaultActivityInfo == null) continue;

                ActivityInfo resolveActivityInfo = resolveInfo.activityInfo;
                boolean matchesPossibleDefaultActivity =
                        TextUtils.equals(resolveActivityInfo.name,
                                possibleDefaultActivityInfo.name)
                        && TextUtils.equals(resolveActivityInfo.packageName,
                                possibleDefaultActivityInfo.packageName);

                if (matchesPossibleDefaultActivity) {
                    defaultActivityResolveInfo = resolveInfo;
                    break;
                }
            }
        }

        ContextualSearchUma.logQuickActionIntentResolution(mQuickActionCategory,
                numMatchingActivities);

        if (numMatchingActivities == 0) {
            reset();
            return;
        }

        mHasQuickAction = true;
        Drawable iconDrawable = null;
        int iconResId = 0;
        if (defaultActivityResolveInfo != null) {
            iconDrawable = defaultActivityResolveInfo.loadIcon(mContext.getPackageManager());

            if (mQuickActionCategory != QuickActionCategory.PHONE) {
                // Use the default app's name to construct the caption.
                mCaption = mContext.getResources().getString(
                        DEFAULT_APP_CAPTION_MAP.get(mQuickActionCategory),
                        defaultActivityResolveInfo.loadLabel(packageManager));
            } else {
                // The caption for phone numbers does not use the app's name.
                mCaption = mContext.getResources().getString(
                        DEFAULT_APP_CAPTION_MAP.get(mQuickActionCategory));
            }
        } else {
            iconResId = ICON_MAP.get(mQuickActionCategory);
            mCaption = mContext.getResources().getString(
                    FALLBACK_CAPTION_MAP.get(mQuickActionCategory));
        }

        inflate();

        if (iconDrawable != null) {
            ((ImageView) getView()).setImageDrawable(iconDrawable);
        } else {
            ((ImageView) getView()).setImageResource(iconResId);
        }

        invalidate();
    }
}
