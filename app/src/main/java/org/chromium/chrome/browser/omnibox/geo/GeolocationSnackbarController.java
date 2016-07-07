// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.geo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.preferences.MainPreferences;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.ui.UiUtils;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * Controller for the geolocation disclosure snackbar, which notifies the user that google.com uses
 * the device location to provide localized search results.
 *
 * This snackbar appears only once: the first time the user focuses the omnibox when the X-Geo
 * header has the potential to be sent along with a search request. For this to happen, several
 * conditions have to be met:
 *  - The current tab is not incognito
 *  - (Android M+) Chrome has been granted location permission
 *  - The default search engine is Google
 *  - Location is not disabled for google.com (or google.fr, etc) in Chrome's site settings
 */
public class GeolocationSnackbarController implements SnackbarController {

    private static final String GEOLOCATION_SNACKBAR_SHOWN_PREF = "geolocation_snackbar_shown";
    private static final int SNACKBAR_DURATION_MS = 8000;
    private static final int ACCESSIBILITY_SNACKBAR_DURATION_MS = 15000;

    private static Boolean sGeolocationSnackbarShown;

    private GeolocationSnackbarController() {}

    /**
     * Shows the geolocation snackbar if it hasn't already been shown and the geolocation snackbar
     * is currently relevant: i.e. the default search engine is Google, location is enabled
     * for Chrome, the tab is not incognito, etc.
     *
     * @param snackbarManager The SnackbarManager used to show the snackbar.
     * @param view Any view that's attached to the view hierarchy.
     * @param isIncognito Whether the currently visible tab is incognito.
     * @param delayMs The delay in ms before the snackbar should be shown. This is intended to
     *                give the keyboard time to animate in.
     */
    public static void maybeShowSnackbar(final SnackbarManager snackbarManager, View view,
            boolean isIncognito, int delayMs) {
        final Context context = view.getContext();
        if (getGeolocationSnackbarShown(context)) return;

        // If in incognito mode, don't show the snackbar now, but maybe show it later.
        if (isIncognito) return;

        if (neverShowSnackbar(context)) {
            setGeolocationSnackbarShown(context);
            return;
        }

        Uri searchUri = Uri.parse(TemplateUrlService.getInstance().getUrlForSearchQuery("foo"));
        TypefaceSpan robotoMediumSpan = new TypefaceSpan("sans-serif-medium");
        String messageWithoutSpans = context.getResources().getString(
                R.string.omnibox_geolocation_disclosure, "<b>" + searchUri.getHost() + "</b>");
        SpannableString message = SpanApplier.applySpans(messageWithoutSpans,
                new SpanInfo("<b>", "</b>", robotoMediumSpan));
        String settings = context.getResources().getString(R.string.preferences);
        int durationMs = DeviceClassManager.isAccessibilityModeEnabled(view.getContext())
                ? ACCESSIBILITY_SNACKBAR_DURATION_MS : SNACKBAR_DURATION_MS;
        final GeolocationSnackbarController controller = new GeolocationSnackbarController();
        final Snackbar snackbar = Snackbar.make(message, controller)
                .setAction(settings, view)
                .setSingleLine(false)
                .setDuration(durationMs);

        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                snackbarManager.dismissSnackbars(controller);
                snackbarManager.showSnackbar(snackbar);
                setGeolocationSnackbarShown(context);
            }
        }, delayMs);
    }

    private static boolean neverShowSnackbar(Context context) {
        // Don't show the snackbar on pre-M devices because location permission was explicitly
        // granted at install time.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        // Don't show the snackbar if Chrome doesn't have location permission since X-Geo won't be
        // sent unless the user explicitly grants this permission.
        if (!GeolocationHeader.hasGeolocationPermission(context)) return true;

        // Don't show the snackbar if Google isn't the default search engine since X-Geo won't be
        // sent unless the user explicitly sets Google as their default search engine and sees that
        // "location is allowed" for omnibox searches in the process.
        if (!TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()) return true;

        // Don't show the snackbar if location is disabled for google.com, since X-Geo won't be sent
        // unless the user explicitly reenables location for google.com.
        Uri searchUri = Uri.parse(TemplateUrlService.getInstance().getUrlForSearchQuery("foo"));
        if (GeolocationHeader.isLocationDisabledForUrl(searchUri, false)) return true;

        return false;
    }

    // SnackbarController implementation:

    @Override
    public void onDismissNoAction(Object actionData) {}

    @Override
    public void onDismissForEachType(boolean isTimeout) {}

    @Override
    public void onAction(Object actionData) {
        View view = (View) actionData;
        UiUtils.hideKeyboard(view);

        Context context = view.getContext();
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(context, null);
        Bundle fragmentArgs = new Bundle();
        fragmentArgs.putBoolean(MainPreferences.EXTRA_SHOW_SEARCH_ENGINE_PICKER, true);
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        context.startActivity(intent);
    }

    /**
     * Returns whether the geolocation snackbar has been shown before.
     */
    static boolean getGeolocationSnackbarShown(Context context) {
        if (sGeolocationSnackbarShown == null) {
            // Cache the preference value since this method is called often.
            sGeolocationSnackbarShown = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(GEOLOCATION_SNACKBAR_SHOWN_PREF, false);
        }

        return sGeolocationSnackbarShown;
    }

    private static void setGeolocationSnackbarShown(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(GEOLOCATION_SNACKBAR_SHOWN_PREF, true).apply();
        sGeolocationSnackbarShown = Boolean.TRUE;
    }
}
