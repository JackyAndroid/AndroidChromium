// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.text.Html;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.location.LocationUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides diagnostic information about the Physical Web feature.
 */
public class PhysicalWebDiagnosticsPage extends BasicNativePage {
    private String mSuccessColor;
    private String mFailureColor;
    private String mIndeterminateColor;
    private View mPageView;
    private Button mLaunchButton;
    private TextView mDiagnosticsText;

    /**
     * Create a new instance of the Physical Web diagnostics page.
     * @param activity The activity to get context and manage fragments.
     * @param tab The tab to load urls.
     */
    public PhysicalWebDiagnosticsPage(Activity activity, Tab tab) {
        super(activity, tab);
    }

    @Override
    protected void initialize(final Activity activity, Tab tab) {
        Resources resources = activity.getResources();
        mSuccessColor = colorToHexValue(ApiCompatibilityUtils.getColor(resources,
                R.color.physical_web_diags_success_color));
        mFailureColor = colorToHexValue(ApiCompatibilityUtils.getColor(resources,
                R.color.physical_web_diags_failure_color));
        mIndeterminateColor = colorToHexValue(ApiCompatibilityUtils.getColor(resources,
                R.color.physical_web_diags_indeterminate_color));

        LayoutInflater inflater = LayoutInflater.from(activity);
        mPageView = inflater.inflate(R.layout.physical_web_diagnostics, null);

        mLaunchButton = (Button) mPageView.findViewById(R.id.physical_web_launch);
        mLaunchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startActivity(createListUrlsIntent());
            }
        });

        mDiagnosticsText = (TextView) mPageView.findViewById(R.id.physical_web_diagnostics_text);
        mDiagnosticsText.setAutoLinkMask(Linkify.WEB_URLS);
        mDiagnosticsText.setText(Html.fromHtml(createDiagnosticsReportHtml()));
    }

    @Override
    public String getTitle() {
        return "Physical Web Diagnostics";
    }

    @Override
    public String getHost() {
        return UrlConstants.PHYSICAL_WEB_HOST;
    }

    @Override
    public View getView() {
        return mPageView;
    }

    private void appendResult(StringBuilder sb, boolean success, String successMessage,
            String failureMessage) {
        int successValue = (success ? Utils.RESULT_SUCCESS : Utils.RESULT_FAILURE);
        appendResult(sb, successValue, successMessage, failureMessage, null);
    }

    private void appendResult(StringBuilder sb, int successValue, String successMessage,
            String failureMessage, String indeterminateMessage) {
        String color;
        String message;
        switch (successValue) {
            case Utils.RESULT_SUCCESS:
                color = mSuccessColor;
                message = successMessage;
                break;
            case Utils.RESULT_FAILURE:
                color = mFailureColor;
                message = failureMessage;
                break;
            case Utils.RESULT_INDETERMINATE:
                color = mIndeterminateColor;
                message = indeterminateMessage;
                break;
            default:
                return;
        }

        sb.append(String.format("<font color=\"%s\">%s</font><br/>", color, message));
    }

    private void appendPrerequisitesReport(StringBuilder sb) {
        boolean isSdkVersionCorrect = isSdkVersionCorrect();
        boolean isDataConnectionActive = Utils.isDataConnectionActive();
        int bluetoothStatus = Utils.getBluetoothEnabledStatus();
        LocationUtils locationUtils = LocationUtils.getInstance();
        boolean isLocationServicesEnabled = locationUtils.isSystemLocationSettingEnabled();
        boolean isLocationPermissionGranted = locationUtils.hasAndroidLocationPermission();
        boolean isPreferenceEnabled = PhysicalWeb.isPhysicalWebPreferenceEnabled();
        boolean isOnboarding = PhysicalWeb.isOnboarding();

        int prerequisitesResult = Utils.RESULT_SUCCESS;
        if (!isSdkVersionCorrect
                || !isDataConnectionActive
                || bluetoothStatus == Utils.RESULT_FAILURE
                || !isLocationServicesEnabled
                || !isLocationPermissionGranted
                || !isPreferenceEnabled) {
            prerequisitesResult = Utils.RESULT_FAILURE;
            mLaunchButton.setEnabled(false);
        } else if (bluetoothStatus == Utils.RESULT_INDETERMINATE) {
            prerequisitesResult = Utils.RESULT_INDETERMINATE;
            mLaunchButton.setEnabled(false);
        }

        sb.append("<h2>Status</h2>");

        sb.append("Physical Web is ");
        appendResult(sb, prerequisitesResult != Utils.RESULT_FAILURE, "ON", "OFF");

        sb.append("<h2>Prerequisites</h2>");

        sb.append("Android SDK version: ");
        appendResult(sb, isSdkVersionCorrect, "Compatible", "Incompatible");

        sb.append("Data connection: ");
        appendResult(sb, isDataConnectionActive, "Connected", "Not connected");

        sb.append("Location services: ");
        appendResult(sb, isLocationServicesEnabled, "Enabled", "Disabled");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append("Location app permission: ");
            appendResult(sb, isLocationPermissionGranted, "Granted", "Not granted");
        }

        sb.append("Physical Web privacy settings: ");
        String preferenceDisabledMessage = (isOnboarding ? "Default (off)" : "Off");
        appendResult(sb, isPreferenceEnabled, "On", preferenceDisabledMessage);

        sb.append("Bluetooth: ");
        appendResult(sb, bluetoothStatus, "Enabled", "Disabled", "Unknown");

        // Append instructions for how to verify Bluetooth is enabled when we are unable to check
        // programmatically.
        if (bluetoothStatus == Utils.RESULT_INDETERMINATE) {
            sb.append("<br/>To verify Bluetooth is enabled on this device, check that the "
                    + "Bluetooth icon is shown in the status bar.");
        }
    }

    private void appendUrlManagerReport(StringBuilder sb) {
        UrlManager urlManager = UrlManager.getInstance();

        Set<String> nearbyUrls = new HashSet<>(urlManager.getNearbyUrls());
        Set<String> resolvedUrls = new HashSet<>(urlManager.getResolvedUrls());
        Set<String> union = new HashSet<>(nearbyUrls);
        union.addAll(resolvedUrls);

        sb.append("<h2>Nearby web pages</h2>");

        if (union.isEmpty()) {
            sb.append("No nearby web pages found<br/>");
        } else {
            for (String url : union) {
                boolean isNearby = nearbyUrls.contains(url);
                boolean isResolved = resolvedUrls.contains(url);

                sb.append(url);
                if (!isNearby) {
                    sb.append(" : LOST");
                } else if (!isResolved) {
                    sb.append(" : UNRESOLVED");
                }
                sb.append("<br/>");
            }
        }
    }

    private String createDiagnosticsReportHtml() {
        StringBuilder sb = new StringBuilder();
        appendPrerequisitesReport(sb);
        sb.append("<br/>");
        appendUrlManagerReport(sb);
        return sb.toString();
    }

    private boolean isSdkVersionCorrect() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
    }

    private static Intent createListUrlsIntent() {
        return new Intent(ContextUtils.getApplicationContext(), ListUrlsActivity.class)
                .putExtra(ListUrlsActivity.REFERER_KEY, ListUrlsActivity.DIAGNOSTICS_REFERER);
    }

    private static String colorToHexValue(int color) {
        return "#" + Integer.toHexString(color & 0x00ffffff);
    }
}
