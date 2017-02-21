// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.util.List;

/**
 * Delegate that manages bottom bar area inside of {@link CustomTabActivity}.
 */
class CustomTabBottomBarDelegate {
    private static final String TAG = "CustomTab";
    private static final LaunchMetrics.ActionEvent REMOTE_VIEWS_SHOWN =
            new LaunchMetrics.ActionEvent("CustomTabsRemoteViewsShown");
    private static final LaunchMetrics.ActionEvent REMOTE_VIEWS_UPDATED =
            new LaunchMetrics.ActionEvent("CustomTabsRemoteViewsUpdated");
    private static final int SLIDE_ANIMATION_DURATION_MS = 400;
    private ChromeActivity mActivity;
    private ViewGroup mBottomBarView;
    private CustomTabIntentDataProvider mDataProvider;
    private PendingIntent mClickPendingIntent;
    private int[] mClickableIDs;

    private OnClickListener mBottomBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mClickPendingIntent == null) return;
            Intent extraIntent = new Intent();
            extraIntent.putExtra(CustomTabsIntent.EXTRA_REMOTEVIEWS_CLICKED_ID, v.getId());
            sendPendingIntentWithUrl(mClickPendingIntent, extraIntent, mActivity);
        }
    };

    public CustomTabBottomBarDelegate(ChromeActivity activity,
            CustomTabIntentDataProvider dataProvider) {
        mActivity = activity;
        mDataProvider = dataProvider;
    }

    /**
     * Makes the bottom bar area to show, if any.
     */
    public void showBottomBarIfNecessary() {
        if (!mDataProvider.shouldShowBottomBar()) return;

        RemoteViews remoteViews = mDataProvider.getBottomBarRemoteViews();
        if (remoteViews != null) {
            REMOTE_VIEWS_SHOWN.record();
            mClickableIDs = mDataProvider.getClickableViewIDs();
            mClickPendingIntent = mDataProvider.getRemoteViewsPendingIntent();
            showRemoteViews(remoteViews);
        } else {
            List<CustomButtonParams> items = mDataProvider.getCustomButtonsOnBottombar();
            if (items.isEmpty()) return;
            LinearLayout layout = new LinearLayout(mActivity);
            layout.setId(R.id.custom_tab_bottom_bar_wrapper);
            layout.setBackgroundColor(mDataProvider.getBottomBarColor());
            for (CustomButtonParams params : items) {
                if (params.showOnToolbar()) continue;
                final PendingIntent pendingIntent = params.getPendingIntent();
                OnClickListener clickListener = null;
                if (pendingIntent != null) {
                    clickListener = new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sendPendingIntentWithUrl(pendingIntent, null, mActivity);
                        }
                    };
                }
                layout.addView(
                        params.buildBottomBarButton(mActivity, getBottomBarView(), clickListener));
            }
            getBottomBarView().addView(layout);
        }
    }

    /**
     * Updates the custom buttons on bottom bar area.
     * @param params The {@link CustomButtonParams} that describes the button to update.
     */
    public void updateBottomBarButtons(CustomButtonParams params) {
        ImageButton button = (ImageButton) getBottomBarView().findViewById(params.getId());
        button.setContentDescription(params.getDescription());
        button.setImageDrawable(params.getIcon(mActivity.getResources()));
    }

    /**
     * Updates the RemoteViews on the bottom bar. If the given remote view is null, animates the
     * bottom bar out.
     * @param remoteViews The new remote view hierarchy sent from the client.
     * @param clickableIDs Array of view ids, the onclick event of which is intercepcted by chrome.
     * @param pendingIntent The {@link PendingIntent} that will be sent on clicking event.
     * @return Whether the update is successful.
     */
    public boolean updateRemoteViews(RemoteViews remoteViews, int[] clickableIDs,
            PendingIntent pendingIntent) {
        // Update only makes sense if we are already showing a RemoteViews.
        if (mDataProvider.getBottomBarRemoteViews() == null) return false;
        REMOTE_VIEWS_UPDATED.record();
        if (remoteViews == null) {
            if (mBottomBarView == null) return false;
            hideBottomBar();
            mClickableIDs = null;
            mClickPendingIntent = null;
        } else {
            // TODO: investigate updating the RemoteViews without replacing the entire hierarchy.
            mClickableIDs = clickableIDs;
            mClickPendingIntent = pendingIntent;
            getBottomBarView().removeViewAt(1);
            showRemoteViews(remoteViews);
        }
        return true;
    }

    /**
     * Gets the {@link ViewGroup} of the bottom bar. If it has not been inflated, inflate it first.
     */
    private ViewGroup getBottomBarView() {
        if (mBottomBarView == null) {
            ViewStub bottomBarStub = ((ViewStub) mActivity.findViewById(R.id.bottombar_stub));
            bottomBarStub.setLayoutResource(R.layout.custom_tabs_bottombar);
            mBottomBarView = (ViewGroup) bottomBarStub.inflate();
        }
        return mBottomBarView;
    }

    private void hideBottomBar() {
        if (mBottomBarView == null) return;
        ((ViewGroup) mBottomBarView.getParent()).removeView(mBottomBarView);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        final ViewGroup compositorView = mActivity.getCompositorViewHolder();
        compositorView.addView(mBottomBarView, lp);
        compositorView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                compositorView.removeOnLayoutChangeListener(this);
                mBottomBarView.animate().alpha(0f).translationY(mBottomBarView.getHeight())
                        .setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE)
                        .setDuration(SLIDE_ANIMATION_DURATION_MS)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                ((ViewGroup) mBottomBarView.getParent()).removeView(mBottomBarView);
                                mBottomBarView = null;
                            }
                        }).start();
            }
        });
    }

    private void showRemoteViews(RemoteViews remoteViews) {
        View inflatedView = remoteViews.apply(mActivity, getBottomBarView());
        if (mClickableIDs != null && mClickPendingIntent != null) {
            for (int id: mClickableIDs) {
                if (id < 0) return;
                View view = inflatedView.findViewById(id);
                if (view != null) view.setOnClickListener(mBottomBarClickListener);
            }
        }
        getBottomBarView().addView(inflatedView, 1);
    }

    private static void sendPendingIntentWithUrl(PendingIntent pendingIntent, Intent extraIntent,
            ChromeActivity activity) {
        Intent addedIntent = extraIntent == null ? new Intent() : new Intent(extraIntent);
        Tab tab = activity.getActivityTab();
        if (tab != null) addedIntent.setData(Uri.parse(tab.getUrl()));
        try {
            pendingIntent.send(activity, 0, addedIntent, null, null);
        } catch (CanceledException e) {
            Log.e(TAG, "CanceledException when sending pending intent.");
        }
    }
}
