// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.content.Intent;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Records UMA relevant to Document mode.
 */
public class DocumentUma {
    /**
     * Records what caused a DocumentActivity to be resumed.
     */
    static void recordStartedBy(int source) {
        RecordHistogram.recordSparseSlowlyHistogram("DocumentActivity.StartedBy", source);
    }

    public static void recordInDocumentMode(boolean isInDocumentMode) {
        RecordHistogram.recordEnumeratedHistogram(
                "DocumentActivity.RunningMode", isInDocumentMode ? 0 : 1, 2);
    }

    /**
     * Records UMA about the Intent fired to create a DocumentActivity.
     * @param packageName Name of the the application package.
     * @param intent Intent used to launch the Activity.
     */
    static void recordStartedBy(String packageName, Intent intent) {
        if (intent == null) {
            recordStartedBy(DocumentMetricIds.STARTED_BY_UNKNOWN);
            return;
        }

        int intentSource = DocumentMetricIds.STARTED_BY_UNKNOWN;
        IntentHandler.ExternalAppId appId =
                IntentHandler.determineExternalIntentSource(packageName, intent);

        if (intent.hasExtra(IntentHandler.EXTRA_STARTED_BY)) {
            intentSource = IntentUtils.safeGetIntExtra(intent,
                    IntentHandler.EXTRA_STARTED_BY, DocumentMetricIds.STARTED_BY_UNKNOWN);
        } else if (IntentUtils.safeGetBooleanExtra(intent,
                ShortcutHelper.REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, false)) {
            // TODO(dfalcantara): Add a new a boolean instead of piggybacking on this Intent extra.
            intentSource = DocumentMetricIds.STARTED_BY_LAUNCHER;
        } else if (IntentUtils.safeGetBooleanExtra(
                intent, IntentHandler.EXTRA_APPEND_TASK, false)) {
            intentSource = DocumentMetricIds.STARTED_BY_SEARCH_RESULT_PAGE;
        } else if (IntentUtils.safeGetBooleanExtra(
                intent, IntentHandler.EXTRA_PRESERVE_TASK, false)) {
            // TODO(dfalcantara): Figure out how split apart Intents fired by the search box.
            intentSource = DocumentMetricIds.STARTED_BY_SEARCH_SUGGESTION_EXTERNAL;
        } else if (appId == IntentHandler.ExternalAppId.GMAIL) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_GMAIL;
        } else if (appId == IntentHandler.ExternalAppId.FACEBOOK) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_FACEBOOK;
        } else if (appId == IntentHandler.ExternalAppId.PLUS) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_PLUS;
        } else if (appId == IntentHandler.ExternalAppId.TWITTER) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_TWITTER;
        } else if (appId == IntentHandler.ExternalAppId.CHROME) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_CHROME;
        } else if (appId == IntentHandler.ExternalAppId.HANGOUTS) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_HANGOUTS;
        } else if (appId == IntentHandler.ExternalAppId.MESSENGER) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_MESSENGER;
        } else if (appId == IntentHandler.ExternalAppId.NEWS) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_NEWS;
        } else if (appId == IntentHandler.ExternalAppId.LINE) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_LINE;
        } else if (appId == IntentHandler.ExternalAppId.WHATSAPP) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_WHATSAPP;
        } else if (appId == IntentHandler.ExternalAppId.GSA) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_GSA;
        } else if (appId == IntentHandler.ExternalAppId.OTHER) {
            intentSource = DocumentMetricIds.STARTED_BY_EXTERNAL_APP_OTHER;
        }

        if (intentSource == DocumentMetricIds.STARTED_BY_UNKNOWN) {
            android.util.Log.d("DocumentUma", "Unknown source detected");
        }

        if (intentSource >= DocumentMetricIds.STARTED_BY_EXTERNAL_APP_GMAIL
                && intentSource < DocumentMetricIds.STARTED_BY_CONTEXTUAL_SEARCH) {
            // Document activity was started from an external app, record which one.
            RecordHistogram.recordEnumeratedHistogram("MobileIntent.PageLoadDueToExternalApp",
                    appId.ordinal(), IntentHandler.ExternalAppId.INDEX_BOUNDARY.ordinal());
        }

        recordStartedBy(intentSource);
    }
}
