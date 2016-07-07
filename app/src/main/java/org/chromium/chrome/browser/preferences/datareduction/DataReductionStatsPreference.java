// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import static android.text.format.DateUtils.FORMAT_NO_YEAR;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;

import static org.chromium.third_party.android.datausagechart.ChartDataUsageView.DAYS_IN_CHART;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.third_party.android.datausagechart.ChartDataUsageView;
import org.chromium.third_party.android.datausagechart.NetworkStatsHistory;

import java.util.TimeZone;

/**
 * Preference category used to display statistics on data reduction.
 */
public class DataReductionStatsPreference extends PreferenceCategory {
    private NetworkStatsHistory mOriginalNetworkStatsHistory;
    private NetworkStatsHistory mReceivedNetworkStatsHistory;

    private TextView mOriginalSizeTextView;
    private TextView mReceivedSizeTextView;
    private TextView mPercentReductionTextView;
    private TextView mStartDateTextView;
    private TextView mEndDateTextView;
    private ChartDataUsageView mChartDataUsageView;
    private long mLeftPosition;
    private long mRightPosition;
    private Long mCurrentTime;
    private String mOriginalTotalPhrase;
    private String mReceivedTotalPhrase;
    private String mPercentReductionPhrase;
    private String mStartDatePhrase;
    private String mEndDatePhrase;

    public DataReductionStatsPreference(
            Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public DataReductionStatsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DataReductionStatsPreference(Context context) {
        super(context);
    }

    /**
     * Sets the current statistics for viewing. These include the original total daily size of
     * received resources before compression, and the actual total daily size of received
     * resources after compression. The last update time is specified in milliseconds since the
     * epoch.
     * @param lastUpdateTimeMillis The last time the statistics were updated.
     * @param networkStatsHistoryOriginal The history of original content lengths.
     * @param networkStatsHistoryReceived The history of received content lengths.
     */
    public void setReductionStats(
            long lastUpdateTimeMillis,
            NetworkStatsHistory networkStatsHistoryOriginal,
            NetworkStatsHistory networkStatsHistoryReceived) {
        mCurrentTime = lastUpdateTimeMillis;
        mRightPosition = mCurrentTime + DateUtils.HOUR_IN_MILLIS
                - TimeZone.getDefault().getOffset(mCurrentTime);
        mLeftPosition = lastUpdateTimeMillis - DateUtils.DAY_IN_MILLIS * DAYS_IN_CHART;
        mOriginalNetworkStatsHistory = networkStatsHistoryOriginal;
        mReceivedNetworkStatsHistory = networkStatsHistoryReceived;
    }

    @Override
    protected boolean onPrepareAddPreference(Preference preference) {
        return super.onPrepareAddPreference(preference);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    /**
     * Sets up a data usage chart and text views containing data reduction statistics.
     * @oaram view The current view.
     */
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        if (mOriginalTotalPhrase == null) updateDetailData();
        mOriginalSizeTextView = (TextView) view.findViewById(R.id.data_reduction_original_size);
        mOriginalSizeTextView.setText(mOriginalTotalPhrase);
        mReceivedSizeTextView = (TextView) view.findViewById(R.id.data_reduction_compressed_size);
        mReceivedSizeTextView.setText(mReceivedTotalPhrase);
        mPercentReductionTextView = (TextView) view.findViewById(R.id.data_reduction_percent);
        mPercentReductionTextView.setText(mPercentReductionPhrase);
        mStartDateTextView = (TextView) view.findViewById(R.id.data_reduction_start_date);
        mStartDateTextView.setText(mStartDatePhrase);
        mEndDateTextView = (TextView) view.findViewById(R.id.data_reduction_end_date);
        mEndDateTextView.setText(mEndDatePhrase);

        mChartDataUsageView = (ChartDataUsageView) view.findViewById(R.id.chart);
        mChartDataUsageView.bindOriginalNetworkStats(mOriginalNetworkStatsHistory);
        mChartDataUsageView.bindCompressedNetworkStats(mReceivedNetworkStatsHistory);
        mChartDataUsageView.setVisibleRange(
                mCurrentTime - DateUtils.DAY_IN_MILLIS * DAYS_IN_CHART,
                mCurrentTime + DateUtils.HOUR_IN_MILLIS, mLeftPosition, mRightPosition);

        View dataReductionProxyUnreachableWarning =
                view.findViewById(R.id.data_reduction_proxy_unreachable);
        if (DataReductionProxySettings.getInstance().isDataReductionProxyUnreachable()) {
            dataReductionProxyUnreachableWarning.setVisibility(View.VISIBLE);
        } else {
            dataReductionProxyUnreachableWarning.setVisibility(View.GONE);
        }
    }

    /**
     * Update data reduction statistics whenever the chart's inspection
     * range changes. In particular, this creates strings describing the total
     * original size of all data received over the date range, the total size
     * of all data received (after compression), the percent data reduction
     * and the range of dates over which these statistics apply.
     */
    private void updateDetailData() {
        final long start = mLeftPosition;
        // Include up to the last second of the currently selected day.
        final long end = mRightPosition;
        final long now = mCurrentTime;
        final Context context = getContext();

        NetworkStatsHistory.Entry originalEntry =
                mOriginalNetworkStatsHistory.getValues(start, end, now, null);
        // Only received bytes are tracked.
        final long originalTotalBytes = originalEntry.rxBytes;
        mOriginalTotalPhrase = Formatter.formatFileSize(context, originalTotalBytes);

        NetworkStatsHistory.Entry compressedEntry =
                mReceivedNetworkStatsHistory.getValues(start, end, now, null);
        // Only received bytes are tracked.
        final long compressedTotalBytes = compressedEntry.rxBytes;
        mReceivedTotalPhrase = Formatter.formatFileSize(context, compressedTotalBytes);

        float percentage = 0.0f;
        if (originalTotalBytes > 0L && originalTotalBytes > compressedTotalBytes) {
            percentage = (originalTotalBytes - compressedTotalBytes) / (float) originalTotalBytes;
        }
        mPercentReductionPhrase = String.format("%.0f%%", 100.0 * percentage);

        mStartDatePhrase = formatDate(context, start);
        mEndDatePhrase = formatDate(context, end);
    }

    private static String formatDate(Context context, long millisSinceEpoch) {
        final int flags = FORMAT_SHOW_DATE | FORMAT_NO_YEAR;
        return DateUtils.formatDateTime(context, millisSinceEpoch, flags).toString();
    }
}
