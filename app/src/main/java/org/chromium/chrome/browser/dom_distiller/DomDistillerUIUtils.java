// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.app.Activity;
import android.support.v7.app.AlertDialog;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.feedback.FeedbackCollector;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Java implementation of dom_distiller::android::DistillerUIHandleAndroid.
 */
@JNINamespace("dom_distiller::android")
public final class DomDistillerUIUtils {
    private static final String DISTILLATION_QUALITY_KEY = "Distillation quality";
    private static final String DISTILLATION_QUALITY_GOOD = "good";
    private static final String DISTILLATION_QUALITY_BAD = "bad";

    // Static handle to object for feedback reporting.
    private static FeedbackReporter sFeedbackReporter;

    // Static handle to Reader Mode's manager.
    private static ReaderModeManagerDelegate sManagerDelegate;

    /**
     * Set the delegate to the ReaderModeManager.
     * @param delegate The delegate for the ReaderModeManager.
     */
    public static void setReaderModeManagerDelegate(ReaderModeManagerDelegate delegate) {
        sManagerDelegate = delegate;
    }

    /**
     * A static method for native code to open the external feedback form UI.
     * @param webContents The WebContents containing the distilled content.
     * @param url The URL to report feedback for.
     * @param good True if the feedback is good and false if not.
     */
    @CalledByNative
    public static void reportFeedbackWithWebContents(
            WebContents webContents, String url, final boolean good) {
        ThreadUtils.assertOnUiThread();
        // TODO(mdjones): It would be better to get the WebContents from the manager so that the
        // native code does not need to depend on RenderFrame.
        Activity activity = getActivityFromWebContents(webContents);
        if (activity == null) return;

        if (sFeedbackReporter == null) {
            ChromeApplication application = (ChromeApplication) activity.getApplication();
            sFeedbackReporter = application.createFeedbackReporter();
        }
        FeedbackCollector.create(activity, Profile.getLastUsedProfile(), url,
                new FeedbackCollector.FeedbackResult() {
                    @Override
                    public void onResult(FeedbackCollector collector) {
                        String quality =
                                good ? DISTILLATION_QUALITY_GOOD : DISTILLATION_QUALITY_BAD;
                        collector.add(DISTILLATION_QUALITY_KEY, quality);
                        sFeedbackReporter.reportFeedback(collector);
                    }
                });
    }

    /**
     * A static method for native code to call to open the distiller UI settings.
     * @param webContents The WebContents containing the distilled content.
     */
    @CalledByNative
    public static void openSettings(WebContents webContents) {
        Activity activity = getActivityFromWebContents(webContents);
        if (webContents != null && activity != null) {
            RecordUserAction.record("DomDistiller_DistilledPagePrefsOpened");
            AlertDialog.Builder builder =
                    new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
            builder.setView(DistilledPagePrefsView.create(activity));
            builder.show();
        }
    }

    /**
     * A static method for native code to close the current Reader Mode panel. This should be
     * some usage of a "close" button.
     * @param animate If the panel should animate closed.
     */
    @CalledByNative
    public static void closePanel(boolean animate) {
        if (sManagerDelegate == null) return;
        sManagerDelegate.closeReaderPanel(StateChangeReason.CLOSE_BUTTON, animate);
    }

    /**
     * Clear static references to objects.
     * @param delegate The delegate requesting the destoy. This prevents different managers in
     * document mode from accidentally clearing a reference it doesn't own.
     */
    public static void destroy(ReaderModeManagerDelegate delegate) {
        if (delegate != sManagerDelegate) return;
        sManagerDelegate = null;
    }

    /**
     * @param webContents The WebContents to get the Activity from.
     * @return The Activity associated with the WebContents.
     */
    private static Activity getActivityFromWebContents(WebContents webContents) {
        if (webContents == null) return null;

        ContentViewCore contentView = ContentViewCore.fromWebContents(webContents);
        if (contentView == null) return null;

        WindowAndroid window = contentView.getWindowAndroid();
        if (window == null) return null;

        return window.getActivity().get();
    }

    private DomDistillerUIUtils() {}
}
