// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.LevelListDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ForeignSessionHelper.ForeignSession;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.concurrent.TimeUnit;

/**
 * Header view shown above each group of items on the Recent Tabs page. Shows the name of the
 * group (e.g. "Recently closed" or "Jim's Laptop"), an icon, last synced time, and a button to
 * expand or collapse the group.
 */
public class RecentTabsGroupView extends RelativeLayout {

    /** Drawable levels for the device type icon and the expand/collapse arrow. */
    private static final int DRAWABLE_LEVEL_COLLAPSED = 0;
    private static final int DRAWABLE_LEVEL_EXPANDED = 1;

    private ImageView mDeviceIcon;
    private ImageView mExpandCollapseIcon;
    private TextView mDeviceLabel;
    private TextView mTimeLabel;
    private long mInitializationTimestamp;
    private int mDeviceLabelExpandedColor;
    private int mDeviceLabelCollapsedColor;
    private int mTimeLabelExpandedColor;
    private int mTimeLabelCollapsedColor;

    /**
     * Constructor for inflating from XML.
     *
     * @param context The context this view will work in.
     * @param attrs The attribute set for this view.
     */
    public RecentTabsGroupView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        mDeviceLabelExpandedColor = ApiCompatibilityUtils.getColor(res, R.color.light_active_color);
        mDeviceLabelCollapsedColor =
                ApiCompatibilityUtils.getColor(res, R.color.ntp_list_header_text);
        mTimeLabelExpandedColor =
                ApiCompatibilityUtils.getColor(res, R.color.ntp_list_header_subtext_active);
        mTimeLabelCollapsedColor =
                ApiCompatibilityUtils.getColor(res, R.color.ntp_list_header_subtext);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mDeviceIcon = (ImageView) findViewById(R.id.device_icon);
        mTimeLabel = (TextView) findViewById(R.id.time_label);
        mDeviceLabel = (TextView) findViewById(R.id.device_label);
        mExpandCollapseIcon = (ImageView) findViewById(R.id.expand_collapse_icon);

        // Create drawable for expand/collapse arrow.
        LevelListDrawable collapseIcon = new LevelListDrawable();
        collapseIcon.addLevel(DRAWABLE_LEVEL_COLLAPSED, DRAWABLE_LEVEL_COLLAPSED,
                TintedDrawable.constructTintedDrawable(getResources(), R.drawable.ic_expanded));
        TintedDrawable collapse =
                TintedDrawable.constructTintedDrawable(getResources(), R.drawable.ic_collapsed);
        collapse.setTint(
                ApiCompatibilityUtils.getColorStateList(getResources(), R.color.blue_mode_tint));
        collapseIcon.addLevel(DRAWABLE_LEVEL_EXPANDED, DRAWABLE_LEVEL_EXPANDED, collapse);
        mExpandCollapseIcon.setImageDrawable(collapseIcon);
    }

    /**
     * Initialize the state of the group view. Should be called immediatly after object creation.
     *
     * @param initializationTimestamp The timestamp to compute the time since last session sync.
     */
    public void initialize(long initializationTimestamp) {
        mInitializationTimestamp = initializationTimestamp;
    }

    /**
     * Configures the view for currently open tabs.
     *
     * @param isExpanded Whether the view is expanded or collapsed.
     */
    public void configureForCurrentlyOpenTabs(boolean isExpanded) {
        mDeviceIcon.setVisibility(View.VISIBLE);
        mDeviceIcon.setImageResource(DeviceFormFactor.isTablet(getContext())
                ? R.drawable.recent_tablet : R.drawable.recent_phone);
        String title = getResources().getString(R.string.recent_tabs_this_device);
        mDeviceLabel.setText(title);
        setTimeLabelVisibility(View.GONE);
        configureExpandedCollapsed(isExpanded);
    }

    /**
     * Configures the view for a foreign session.
     *
     * @param session The session to configure the view for.
     * @param isExpanded Whether the view is expanded or collapsed.
     */
    public void configureForForeignSession(ForeignSession session, boolean isExpanded) {
        mDeviceIcon.setVisibility(View.VISIBLE);
        mDeviceLabel.setText(session.name);
        setTimeLabelVisibility(View.VISIBLE);
        mTimeLabel.setText(getTimeString(session));
        switch (session.deviceType) {
            case ForeignSession.DEVICE_TYPE_PHONE:
                mDeviceIcon.setImageResource(R.drawable.recent_phone);
                break;
            case ForeignSession.DEVICE_TYPE_TABLET:
                mDeviceIcon.setImageResource(R.drawable.recent_tablet);
                break;
            default:
                mDeviceIcon.setImageResource(R.drawable.recent_laptop);
                break;
        }
        configureExpandedCollapsed(isExpanded);
    }

    /**
     * Configures the view for the recently closed tabs group.
     *
     * @param isExpanded Whether the view is expanded or collapsed.
     */
    public void configureForRecentlyClosedTabs(boolean isExpanded) {
        mDeviceIcon.setVisibility(View.VISIBLE);
        mDeviceIcon.setImageResource(R.drawable.recent_recently_closed);
        mDeviceLabel.setText(R.string.recently_closed);
        setTimeLabelVisibility(View.GONE);
        configureExpandedCollapsed(isExpanded);
    }

    /**
     * Configures the view for the sync promo.
     *
     * @param isExpanded Whether the view is expanded or collapsed.
     */
    public void configureForSyncPromo(boolean isExpanded) {
        mDeviceIcon.setVisibility(View.VISIBLE);
        mDeviceIcon.setImageResource(R.drawable.recent_laptop);
        mDeviceLabel.setText(R.string.ntp_recent_tabs_sync_promo_title);
        setTimeLabelVisibility(View.GONE);
        configureExpandedCollapsed(isExpanded);
    }

    private void configureExpandedCollapsed(boolean isExpanded) {
        String description = getResources().getString(isExpanded
                ? R.string.ntp_recent_tabs_accessibility_expanded_group
                : R.string.ntp_recent_tabs_accessibility_collapsed_group);
        mExpandCollapseIcon.setContentDescription(description);

        int level = isExpanded ? DRAWABLE_LEVEL_EXPANDED : DRAWABLE_LEVEL_COLLAPSED;
        mExpandCollapseIcon.getDrawable().setLevel(level);
        mDeviceIcon.setActivated(isExpanded);

        mDeviceLabel.setTextColor(isExpanded
                ? mDeviceLabelExpandedColor
                : mDeviceLabelCollapsedColor);
        mTimeLabel.setTextColor(isExpanded ? mTimeLabelExpandedColor : mTimeLabelCollapsedColor);
    }

    private String getTimeString(ForeignSession session) {
        long sessionModifiedTimeSeconds =
                TimeUnit.SECONDS.convert(session.modifiedTime, TimeUnit.MILLISECONDS);
        long timeDelta = mInitializationTimestamp - sessionModifiedTimeSeconds;
        timeDelta = timeDelta > 0 ? timeDelta : 0;

        long daysElapsed = timeDelta / (24L * 60L * 60L);
        long hoursElapsed = timeDelta / (60L * 60L);
        long minutesElapsed = timeDelta / 60L;

        Resources resources = getContext().getResources();
        if (daysElapsed > 0L) {
            return resources.getString(R.string.ntp_recent_tabs_last_synced_days, daysElapsed);
        } else if (hoursElapsed > 0L) {
            return resources.getString(R.string.ntp_recent_tabs_last_synced_hours, hoursElapsed);
        } else if (minutesElapsed > 0L) {
            return resources.getString(
                    R.string.ntp_recent_tabs_last_synced_minutes, minutesElapsed);
        } else {
            return resources.getString(R.string.ntp_recent_tabs_last_synced_just_now);
        }
    }

    /**
     * Shows or hides the time label (e.g. "Last synced: just now") and adjusts the positions of the
     * icon and device label. In particular, the icon and device label are top-aligned when the time
     * is visible, but are vertically centered when the time is gone.
     */
    private void setTimeLabelVisibility(int visibility) {
        if (mTimeLabel.getVisibility() == visibility) return;
        mTimeLabel.setVisibility(visibility);
        if (visibility == View.GONE) {
            replaceRule(mDeviceIcon, ALIGN_PARENT_TOP, CENTER_VERTICAL);
            replaceRule(mDeviceLabel, ALIGN_PARENT_TOP, CENTER_VERTICAL);
        } else {
            replaceRule(mDeviceIcon, CENTER_VERTICAL, ALIGN_PARENT_TOP);
            replaceRule(mDeviceLabel, CENTER_VERTICAL, ALIGN_PARENT_TOP);
        }
    }

    private static void replaceRule(View view, int oldRule, int newRule) {
        RelativeLayout.LayoutParams lp = ((RelativeLayout.LayoutParams) view.getLayoutParams());
        lp.addRule(oldRule, 0);
        lp.addRule(newRule);
    }
}
