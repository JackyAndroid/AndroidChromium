// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkswidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.google.android.apps.chrome.appwidget.bookmarks.BookmarkThumbnailWidgetProvider;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Widget that shows a preview of the user's bookmarks.
 */
public class BookmarkThumbnailWidgetProviderBase extends AppWidgetProvider {
    private static final String ACTION_BOOKMARK_APPWIDGET_UPDATE_SUFFIX =
            ".BOOKMARK_APPWIDGET_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Handle bookmark-specific updates ourselves because they might be
        // coming in without extras, which AppWidgetProvider then blocks.
        final String action = intent.getAction();
        if (getBookmarkAppWidgetUpdateAction(context).equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            if (intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
                performUpdate(context, appWidgetManager,
                        new int[] {IntentUtils.safeGetIntExtra(intent,
                                AppWidgetManager.EXTRA_APPWIDGET_ID, -1)});
            } else {
                performUpdate(context, appWidgetManager,
                        appWidgetManager.getAppWidgetIds(getComponentName(context)));
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        super.onUpdate(context, manager, ids);
        performUpdate(context, manager, ids);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int widgetId : appWidgetIds) {
            BookmarkThumbnailWidgetService.deleteWidgetState(context, widgetId);
        }
        removeOrphanedStates(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        removeOrphanedStates(context);
    }

    /**
     * Refreshes all Chrome Bookmark widgets.
     */
    public static void refreshAllWidgets(Context context) {
        context.sendBroadcast(new Intent(
                getBookmarkAppWidgetUpdateAction(context),
                null, context, BookmarkThumbnailWidgetProvider.class));
    }

    static String getBookmarkAppWidgetUpdateAction(Context context) {
        return context.getPackageName() + ACTION_BOOKMARK_APPWIDGET_UPDATE_SUFFIX;
    }

    /**
     *  Checks for any states that may have not received onDeleted.
     */
    private void removeOrphanedStates(Context context) {
        AppWidgetManager wm = AppWidgetManager.getInstance(context);
        int[] ids = wm.getAppWidgetIds(getComponentName(context));
        for (int id : ids) {
            BookmarkThumbnailWidgetService.deleteWidgetState(context, id);
        }
    }

    private void performUpdate(Context context,
            AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Intent chromeIntent = new Intent(Intent.ACTION_MAIN);
        chromeIntent.setPackage(context.getPackageName());

        PendingIntent launchChrome = PendingIntent.getActivity(context, 0, chromeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        for (int appWidgetId : appWidgetIds) {
            Intent updateIntent = new Intent(context, BookmarkThumbnailWidgetService.class);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            updateIntent.setData(Uri.parse(updateIntent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.bookmark_thumbnail_widget);
            views.setOnClickPendingIntent(R.id.app_shortcut, launchChrome);
            views.setRemoteAdapter(R.id.bookmarks_list, updateIntent);

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.bookmarks_list);
            Intent ic = new Intent(context, BookmarkWidgetProxy.class);
            views.setPendingIntentTemplate(R.id.bookmarks_list,
                    PendingIntent.getBroadcast(context, 0, ic,
                    PendingIntent.FLAG_UPDATE_CURRENT));
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /**
     * Build {@link ComponentName} describing this specific
     * {@link AppWidgetProvider}
     */
    private static ComponentName getComponentName(Context context) {
        return new ComponentName(context, BookmarkThumbnailWidgetProvider.class);
    }
}
