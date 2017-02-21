// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Icon;
import android.os.Build;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Abstract base class for building a notification. Stores all given arguments for later use.
 */
public abstract class NotificationBuilderBase {
    protected static class Action {
        enum Type {
            /**
             * Regular action that triggers the provided intent when tapped.
             */
            BUTTON,

            /**
             * Action that triggers a remote input when tapped, for Android Wear input and inline
             * replies from Android N.
             */
            TEXT
        }

        public int iconId;
        public Bitmap iconBitmap;
        public CharSequence title;
        public PendingIntent intent;
        public Type type;

        /**
         * If the action.type is TEXT, this corresponds to the placeholder text for the input.
         */
        public String placeholder;

        Action(int iconId, CharSequence title, PendingIntent intent, Type type,
                String placeholder) {
            this.iconId = iconId;
            this.title = title;
            this.intent = intent;
            this.type = type;
            this.placeholder = placeholder;
        }

        Action(Bitmap iconBitmap, CharSequence title, PendingIntent intent, Type type,
                String placeholder) {
            this.iconBitmap = iconBitmap;
            this.title = title;
            this.intent = intent;
            this.type = type;
            this.placeholder = placeholder;
        }
    }

    /**
     * Maximum length of CharSequence inputs to prevent excessive memory consumption. At current
     * screen sizes we display about 500 characters at most, so this is a pretty generous limit, and
     * it matches what the Notification class does.
     */
    @VisibleForTesting
    static final int MAX_CHARSEQUENCE_LENGTH = 5 * 1024;

    /**
     * Background color for generated notification icons.
     */
    @VisibleForTesting
    static final int NOTIFICATION_ICON_BG_COLOR = 0xFF969696;

    /**
     * Density-independent text size for generated notification icons.
     */
    @VisibleForTesting
    static final int NOTIFICATION_ICON_TEXT_SIZE_DP = 28;

    /**
     * The maximum number of author provided action buttons. The settings button is not part of this
     * count.
     */
    private static final int MAX_AUTHOR_PROVIDED_ACTION_BUTTONS = 2;

    private final int mLargeIconWidthPx;
    private final int mLargeIconHeightPx;
    private final RoundedIconGenerator mIconGenerator;

    protected CharSequence mTitle;
    protected CharSequence mBody;
    protected CharSequence mOrigin;
    protected CharSequence mTickerText;
    protected Bitmap mImage;
    protected int mSmallIconId;
    protected Bitmap mSmallIconBitmap;
    protected PendingIntent mContentIntent;
    protected PendingIntent mDeleteIntent;
    protected List<Action> mActions = new ArrayList<>(MAX_AUTHOR_PROVIDED_ACTION_BUTTONS);
    protected Action mSettingsAction;
    protected int mDefaults = Notification.DEFAULT_ALL;
    protected long[] mVibratePattern;
    protected long mTimestamp;
    protected boolean mRenotify;

    private Bitmap mLargeIcon;

    public NotificationBuilderBase(Resources resources) {
        mLargeIconWidthPx =
                resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        mLargeIconHeightPx =
                resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
        mIconGenerator = createIconGenerator(resources);
    }

    /**
     * Combines all of the options that have been set and returns a new Notification object.
     */
    public abstract Notification build();

    /**
     * Sets the title text of the notification.
     */
    public NotificationBuilderBase setTitle(@Nullable CharSequence title) {
        mTitle = limitLength(title);
        return this;
    }

    /**
     * Sets the body text of the notification.
     */
    public NotificationBuilderBase setBody(@Nullable CharSequence body) {
        mBody = limitLength(body);
        return this;
    }

    /**
     * Sets the origin text of the notification.
     */
    public NotificationBuilderBase setOrigin(@Nullable CharSequence origin) {
        mOrigin = limitLength(origin);
        return this;
    }

    /**
     * Sets the text that is displayed in the status bar when the notification first arrives.
     */
    public NotificationBuilderBase setTicker(@Nullable CharSequence tickerText) {
        mTickerText = limitLength(tickerText);
        return this;
    }

    /**
     * Sets the content image to be prominently displayed when the notification is expanded.
     */
    public NotificationBuilderBase setImage(@Nullable Bitmap image) {
        mImage = image;
        return this;
    }

    /**
     * Sets the large icon that is shown in the notification.
     */
    public NotificationBuilderBase setLargeIcon(@Nullable Bitmap icon) {
        mLargeIcon = icon;
        return this;
    }

    /**
     * Sets the small icon that is shown in the notification and in the status bar. Wherever the
     * platform supports using a small icon bitmap, and a non-null {@code Bitmap} is provided, it
     * will take precedence over one specified as a resource id.
     */
    public NotificationBuilderBase setSmallIcon(int iconId) {
        mSmallIconId = iconId;
        return this;
    }

    /**
     * Sets the small icon that is shown in the notification and in the status bar. Wherever the
     * platform supports using a small icon bitmap, and a non-null {@code Bitmap} is provided, it
     * will take precedence over one specified as a resource id.
     */
    public NotificationBuilderBase setSmallIcon(@Nullable Bitmap iconBitmap) {
        Bitmap copyOfBitmap = null;
        if (iconBitmap != null) {
            copyOfBitmap = iconBitmap.copy(iconBitmap.getConfig(), true /* isMutable */);
            applyWhiteOverlayToBitmap(copyOfBitmap);
        }
        mSmallIconBitmap = copyOfBitmap;
        return this;
    }

    /**
     * Sets the PendingIntent to send when the notification is clicked.
     */
    public NotificationBuilderBase setContentIntent(@Nullable PendingIntent intent) {
        mContentIntent = intent;
        return this;
    }

    /**
     * Sets the PendingIntent to send when the notification is cleared by the user directly from the
     * notification panel.
     */
    public NotificationBuilderBase setDeleteIntent(@Nullable PendingIntent intent) {
        mDeleteIntent = intent;
        return this;
    }

    /**
     * Adds an action to the notification, displayed as a button adjacent to the notification
     * content.
     */
    public NotificationBuilderBase addButtonAction(@Nullable Bitmap iconBitmap,
            @Nullable CharSequence title, @Nullable PendingIntent intent) {
        addAuthorProvidedAction(iconBitmap, title, intent, Action.Type.BUTTON, null);
        return this;
    }

    /**
     * Adds an action to the notification, displayed as a button adjacent to the notification
     * content, which when tapped will trigger a remote input. This enables Android Wear input and,
     * from Android N, displays a text box within the notification for inline replies.
     */
    public NotificationBuilderBase addTextAction(@Nullable Bitmap iconBitmap,
            @Nullable CharSequence title, @Nullable PendingIntent intent, String placeholder) {
        addAuthorProvidedAction(iconBitmap, title, intent, Action.Type.TEXT, placeholder);
        return this;
    }

    private void addAuthorProvidedAction(@Nullable Bitmap iconBitmap, @Nullable CharSequence title,
            @Nullable PendingIntent intent, Action.Type actionType, @Nullable String placeholder) {
        if (mActions.size() == MAX_AUTHOR_PROVIDED_ACTION_BUTTONS) {
            throw new IllegalStateException(
                    "Cannot add more than " + MAX_AUTHOR_PROVIDED_ACTION_BUTTONS + " actions.");
        }
        if (iconBitmap != null) {
            applyWhiteOverlayToBitmap(iconBitmap);
        }
        mActions.add(new Action(iconBitmap, limitLength(title), intent, actionType, placeholder));
    }

    /**
     * Adds an action to the notification for opening the settings screen.
     */
    public NotificationBuilderBase addSettingsAction(
            int iconId, @Nullable CharSequence title, @Nullable PendingIntent intent) {
        mSettingsAction = new Action(iconId, limitLength(title), intent, Action.Type.BUTTON, null);
        return this;
    }

    /**
     * Sets the default notification options that will be used.
     * <p>
     * The value should be one or more of the following fields combined with
     * bitwise-or:
     * {@link Notification#DEFAULT_SOUND}, {@link Notification#DEFAULT_VIBRATE},
     * {@link Notification#DEFAULT_LIGHTS}.
     * <p>
     * For all default values, use {@link Notification#DEFAULT_ALL}.
     */
    public NotificationBuilderBase setDefaults(int defaults) {
        mDefaults = defaults;
        return this;
    }

    /**
     * Sets the vibration pattern to use.
     */
    public NotificationBuilderBase setVibrate(long[] pattern) {
        mVibratePattern = Arrays.copyOf(pattern, pattern.length);
        return this;
    }

    /**
     * Sets the timestamp at which the event of the notification took place.
     */
    public NotificationBuilderBase setTimestamp(long timestamp) {
        mTimestamp = timestamp;
        return this;
    }

    /**
     * Sets the behavior for when the notification is replaced.
     */
    public NotificationBuilderBase setRenotify(boolean renotify) {
        mRenotify = renotify;
        return this;
    }

    /**
     * Gets the large icon for the notification.
     *
     * If a large icon was supplied to the builder, returns this icon, scaled to an appropriate size
     * if necessary.
     *
     * If no large icon was supplied then returns a default icon based on the notification origin.
     *
     * See {@link NotificationBuilderBase#ensureNormalizedIcon} for more details.
     */
    protected Bitmap getNormalizedLargeIcon() {
        return ensureNormalizedIcon(mLargeIcon, mOrigin);
    }

    /**
     * Ensures the availability of an icon for the notification.
     *
     * If |icon| is a valid, non-empty Bitmap, the bitmap will be scaled to be of an appropriate
     * size for the current Android device. Otherwise, a default icon will be created based on the
     * origin the notification is being displayed for.
     *
     * @param icon The developer-provided icon they intend to use for the notification.
     * @param origin The origin the notification is being displayed for.
     * @return An appropriately sized icon to use for the notification.
     */
    @VisibleForTesting
    public Bitmap ensureNormalizedIcon(Bitmap icon, CharSequence origin) {
        if (icon == null || icon.getWidth() == 0) {
            return origin != null ? mIconGenerator.generateIconForUrl(origin.toString(), true)
                                  : null;
        }
        if (icon.getWidth() > mLargeIconWidthPx || icon.getHeight() > mLargeIconHeightPx) {
            return Bitmap.createScaledBitmap(
                    icon, mLargeIconWidthPx, mLargeIconHeightPx, false /* not filtered */);
        }
        return icon;
    }

    /**
     * Creates a public version of the notification to be displayed in sensitive contexts, such as
     * on the lockscreen, displaying just the site origin and badge or generated icon.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected Notification createPublicNotification(Context context) {
        // Use Android's Notification.Builder because we want the default small icon behaviour.
        Notification.Builder builder =
                new Notification.Builder(context)
                        .setContentText(context.getString(
                                org.chromium.chrome.R.string.notification_hidden_text))
                        .setSmallIcon(org.chromium.chrome.R.drawable.ic_chrome);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            // On N, 'subtext' displays at the top of the notification and this looks better.
            builder.setSubText(mOrigin);
        } else {
            // Set origin as title on L & M, because they look odd without one.
            builder.setContentTitle(mOrigin);
            // Hide the timestamp to match Android's default public notifications on L and M.
            builder.setShowWhen(false);
        }

        // Use the badge if provided and SDK supports it, else use a generated icon.
        if (mSmallIconBitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // The Icon class was added in Android M.
            Bitmap publicIcon = mSmallIconBitmap.copy(mSmallIconBitmap.getConfig(), true);
            builder.setSmallIcon(Icon.createWithBitmap(publicIcon));
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && mOrigin != null) {
            // Only set the large icon for L & M because on N(+?) it would add an extra icon on
            // the right hand side, which looks odd without a notification title.
            builder.setLargeIcon(mIconGenerator.generateIconForUrl(mOrigin.toString(), true));
        }
        return builder.build();
    }

    @Nullable
    private static CharSequence limitLength(@Nullable CharSequence input) {
        if (input == null) {
            return input;
        }
        if (input.length() > MAX_CHARSEQUENCE_LENGTH) {
            return input.subSequence(0, MAX_CHARSEQUENCE_LENGTH);
        }
        return input;
    }

    /**
     * Sets the small icon on {@code builder} using a {@code Bitmap} if a non-null bitmap is
     * provided and the API level is high enough, otherwise the resource id is used.
     */
    @TargetApi(Build.VERSION_CODES.M) // For the Icon class.
    protected static void setSmallIconOnBuilder(
            Notification.Builder builder, int iconId, @Nullable Bitmap iconBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && iconBitmap != null) {
            builder.setSmallIcon(Icon.createWithBitmap(iconBitmap));
        } else {
            builder.setSmallIcon(iconId);
        }
    }

    /**
     * Adds an action to {@code builder} using a {@code Bitmap} if a bitmap is provided and the API
     * level is high enough, otherwise a resource id is used.
     */
    @SuppressWarnings("deprecation") // For addAction(int, CharSequence, PendingIntent)
    protected static void addActionToBuilder(Notification.Builder builder, Action action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // Notification.Action.Builder and RemoteInput were added in KITKAT_WATCH.
            Notification.Action.Builder actionBuilder = getActionBuilder(action);
            if (action.type == Action.Type.TEXT) {
                assert action.placeholder != null;
                actionBuilder.addRemoteInput(
                        new RemoteInput.Builder(NotificationConstants.KEY_TEXT_REPLY)
                                .setLabel(action.placeholder)
                                .build());
            }
            builder.addAction(actionBuilder.build());
        } else {
            builder.addAction(action.iconId, action.title, action.intent);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH) // For Notification.Action.Builder
    @SuppressWarnings("deprecation") // For Builder(int, CharSequence, PendingIntent)
    private static Notification.Action.Builder getActionBuilder(Action action) {
        Notification.Action.Builder actionBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && action.iconBitmap != null) {
            // Icon was added in Android M.
            Icon icon = Icon.createWithBitmap(action.iconBitmap);
            actionBuilder = new Notification.Action.Builder(icon, action.title, action.intent);
        } else {
            actionBuilder =
                    new Notification.Action.Builder(action.iconId, action.title, action.intent);
        }
        return actionBuilder;
    }

    /**
     * Paints {@code bitmap} white. This processing should be performed if the Android system
     * expects a bitmap to be white, and the bitmap is not already known to be white. The bitmap
     * must be mutable.
     */
    static void applyWhiteOverlayToBitmap(Bitmap bitmap) {
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(bitmap, 0, 0, paint);
    }

    @VisibleForTesting
    static RoundedIconGenerator createIconGenerator(Resources resources) {
        int largeIconWidthPx =
                resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        int largeIconHeightPx =
                resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
        float density = resources.getDisplayMetrics().density;
        int cornerRadiusPx = Math.min(largeIconWidthPx, largeIconHeightPx) / 2;
        return new RoundedIconGenerator(largeIconWidthPx, largeIconHeightPx, cornerRadiusPx,
                NOTIFICATION_ICON_BG_COLOR, NOTIFICATION_ICON_TEXT_SIZE_DP * density);
    }
}
