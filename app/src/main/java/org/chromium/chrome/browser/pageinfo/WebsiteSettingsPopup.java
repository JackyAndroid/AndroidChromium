// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.pageinfo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.v7.widget.AppCompatTextView;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.omnibox.OmniboxUrlEmphasizer;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.ContentSettingsResources;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.ssl.SecurityStateModel;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.location.LocationUtils;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;
import org.chromium.ui.interpolators.BakedBezierInterpolator;
import org.chromium.ui.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Java side of Android implementation of the website settings UI.
 * TODO(sashab): Rename this, and all its resources, to PageInfo* and page_info_* instead of
 *               WebsiteSettings* and website_settings_*. Do this on the C++ side as well.
 */
public class WebsiteSettingsPopup implements OnClickListener {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({OPENED_FROM_MENU, OPENED_FROM_TOOLBAR})
    private @interface OpenedFromSource {}

    public static final int OPENED_FROM_MENU = 1;
    public static final int OPENED_FROM_TOOLBAR = 2;

    /**
     * An entry in the settings dropdown for a given permission. There are two options for each
     * permission: Allow and Block.
     */
    private static final class PageInfoPermissionEntry {
        public final String name;
        public final int type;
        public final ContentSetting setting;

        PageInfoPermissionEntry(String name, int type, ContentSetting setting) {
            this.name = name;
            this.type = type;
            this.setting = setting;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A TextView which truncates and displays a URL such that the origin is always visible.
     * The URL can be expanded by clicking on the it.
     */
    public static class ElidedUrlTextView extends AppCompatTextView {
        // The number of lines to display when the URL is truncated. This number
        // should still allow the origin to be displayed. NULL before
        // setUrlAfterLayout() is called.
        private Integer mTruncatedUrlLinesToDisplay;

        // The number of lines to display when the URL is expanded. This should be enough to display
        // at most two lines of the fragment if there is one in the URL.
        private Integer mFullLinesToDisplay;

        // If true, the text view will show the truncated text. If false, it
        // will show the full, expanded text.
        private boolean mIsShowingTruncatedText = true;

        // The profile to use when getting the end index for the origin.
        private Profile mProfile = null;

        // The maximum number of lines currently shown in the view
        private int mCurrentMaxLines = Integer.MAX_VALUE;

        /** Constructor for inflating from XML. */
        public ElidedUrlTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setMaxLines(int maxlines) {
            super.setMaxLines(maxlines);
            mCurrentMaxLines = maxlines;
        }

        /**
         * Find the number of lines of text which must be shown in order to display the character at
         * a given index.
         */
        private int getLineForIndex(int index) {
            Layout layout = getLayout();
            int endLine = 0;
            while (endLine < layout.getLineCount() && layout.getLineEnd(endLine) < index) {
                endLine++;
            }
            // Since endLine is an index, add 1 to get the number of lines.
            return endLine + 1;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMaxLines(Integer.MAX_VALUE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            assert mProfile != null : "setProfile() must be called before layout.";
            String urlText = getText().toString();

            // Lay out the URL in a StaticLayout that is the same size as our final
            // container.
            int originEndIndex = OmniboxUrlEmphasizer.getOriginEndIndex(urlText, mProfile);

            // Find the range of lines containing the origin.
            int originEndLine = getLineForIndex(originEndIndex);

            // Display an extra line so we don't accidentally hide the origin with
            // ellipses
            mTruncatedUrlLinesToDisplay = originEndLine + 1;

            // Find the line where the fragment starts. Since # is a reserved character, it is safe
            // to just search for the first # to appear in the url.
            int fragmentStartIndex = urlText.indexOf('#');
            if (fragmentStartIndex == -1) fragmentStartIndex = urlText.length();

            int fragmentStartLine = getLineForIndex(fragmentStartIndex);
            mFullLinesToDisplay = fragmentStartLine + 1;

            // If there is no origin (according to OmniboxUrlEmphasizer), make sure the fragment is
            // still hidden correctly.
            if (mFullLinesToDisplay < mTruncatedUrlLinesToDisplay) {
                mTruncatedUrlLinesToDisplay = mFullLinesToDisplay;
            }

            if (updateMaxLines()) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        /**
         * Sets the profile to use when calculating the end index of the origin.
         * Must be called before layout.
         *
         * @param profile The profile to use when coloring the URL.
         */
        public void setProfile(Profile profile) {
            mProfile = profile;
        }

        /**
         * Toggles truncating/expanding the URL text. If the URL text is not
         * truncated, has no effect.
         */
        public void toggleTruncation() {
            mIsShowingTruncatedText = !mIsShowingTruncatedText;
            updateMaxLines();
        }

        private boolean updateMaxLines() {
            int maxLines = mFullLinesToDisplay;
            if (mIsShowingTruncatedText) maxLines = mTruncatedUrlLinesToDisplay;
            if (maxLines != mCurrentMaxLines) {
                setMaxLines(maxLines);
                return true;
            }
            return false;
        }
    }

    // Delay enter to allow the triggering button to animate before we cover it.
    private static final int ENTER_START_DELAY = 100;
    private static final int FADE_DURATION = 200;
    private static final int FADE_IN_BASE_DELAY = 150;
    private static final int FADE_IN_DELAY_OFFSET = 20;
    private static final int CLOSE_CLEANUP_DELAY = 10;

    private static final int MAX_TABLET_DIALOG_WIDTH_DP = 400;

    private final Context mContext;
    private final Profile mProfile;
    private final WebContents mWebContents;
    private final WindowAndroid mWindowAndroid;
    private final Tab mTab;

    // A pointer to the C++ object for this UI.
    private long mNativeWebsiteSettingsPopup;

    // The outer container, filled with the layout from website_settings.xml.
    private final LinearLayout mContainer;

    // UI elements in the dialog.
    private final ElidedUrlTextView mUrlTitle;
    private final TextView mConnectionSummary;
    private final TextView mConnectionMessage;
    private final LinearLayout mPermissionsList;
    private final Button mInstantAppButton;
    private final Button mSiteSettingsButton;
    private final Button mOpenOnlineButton;

    // The dialog the container is placed in.
    private final Dialog mDialog;

    // Animation which is currently running, if there is one.
    private AnimatorSet mCurrentAnimation = null;

    private boolean mDismissWithoutAnimation;

    // The full URL from the URL bar, which is copied to the user's clipboard when they select 'Copy
    // URL'.
    private String mFullUrl;

    // A parsed version of mFullUrl. Is null if the URL is invalid/cannot be
    // parsed.
    private URI mParsedUrl;

    // Whether or not this page is an internal chrome page (e.g. the
    // chrome://settings page).
    private boolean mIsInternalPage;

    // The security level of the page (a valid ConnectionSecurityLevel).
    private int mSecurityLevel;

    // Permissions available to be displayed in mPermissionsList.
    private List<PageInfoPermissionEntry> mDisplayedPermissions;

    // Creation date of an offline copy, if web contents contains an offline page.
    private String mOfflinePageCreationDate;

    // The name of the content publisher, if any.
    private String mContentPublisher;

    // The intent associated with the instant app for this URL (or null if one does not exist).
    private Intent mInstantAppIntent;

    /**
     * Creates the WebsiteSettingsPopup, but does not display it. Also initializes the corresponding
     * C++ object and saves a pointer to it.
     * @param activity                 Activity which is used for showing a popup.
     * @param tab                      Tab for which the pop up is shown.
     * @param offlinePageCreationDate  Date when the offline page was created.
     * @param publisher                The name of the content publisher, if any.
     */
    private WebsiteSettingsPopup(Activity activity, Tab tab, String offlinePageCreationDate,
            String publisher) {
        mContext = activity;
        mProfile = tab.getProfile();
        mWebContents = tab.getWebContents();
        mTab = tab;
        if (offlinePageCreationDate != null) {
            mOfflinePageCreationDate = offlinePageCreationDate;
        }
        mWindowAndroid = ContentViewCore.fromWebContents(mWebContents).getWindowAndroid();
        mContentPublisher = publisher;

        // Find the container and all it's important subviews.
        mContainer = (LinearLayout) LayoutInflater.from(mContext).inflate(
                R.layout.website_settings, null);
        mContainer.setVisibility(View.INVISIBLE);
        mContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(
                    View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                // Trigger the entrance animations once the main container has been laid out and has
                // a height.
                mContainer.removeOnLayoutChangeListener(this);
                mContainer.setVisibility(View.VISIBLE);
                createAllAnimations(true).start();
            }
        });

        mUrlTitle = (ElidedUrlTextView) mContainer.findViewById(R.id.website_settings_url);
        mUrlTitle.setProfile(mProfile);
        mUrlTitle.setOnClickListener(this);
        // Long press the url text to copy it to the clipboard.
        mUrlTitle.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) mContext
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("url", mFullUrl);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(mContext, R.string.url_copied, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mConnectionSummary = (TextView) mContainer
                .findViewById(R.id.website_settings_connection_summary);
        mConnectionMessage = (TextView) mContainer
                .findViewById(R.id.website_settings_connection_message);
        mPermissionsList = (LinearLayout) mContainer
                .findViewById(R.id.website_settings_permissions_list);

        mInstantAppButton =
                (Button) mContainer.findViewById(R.id.website_settings_instant_app_button);
        mInstantAppButton.setOnClickListener(this);

        mSiteSettingsButton =
                (Button) mContainer.findViewById(R.id.website_settings_site_settings_button);
        mSiteSettingsButton.setOnClickListener(this);

        mOpenOnlineButton =
                (Button) mContainer.findViewById(R.id.website_settings_open_online_button);
        mOpenOnlineButton.setOnClickListener(this);

        mDisplayedPermissions = new ArrayList<PageInfoPermissionEntry>();

        // Hide the permissions list for sites with no permissions.
        setVisibilityOfPermissionsList(false);

        // Work out the URL and connection message and status visibility.
        mFullUrl = mWebContents.getVisibleUrl();
        if (isShowingOfflinePage()) {
            mFullUrl = OfflinePageUtils.stripSchemeFromOnlineUrl(mFullUrl);
        }

        try {
            mParsedUrl = new URI(mFullUrl);
            mIsInternalPage = UrlUtilities.isInternalScheme(mParsedUrl);
        } catch (URISyntaxException e) {
            mParsedUrl = null;
            mIsInternalPage = false;
        }
        mSecurityLevel = SecurityStateModel.getSecurityLevelForWebContents(mWebContents);

        SpannableStringBuilder urlBuilder = new SpannableStringBuilder(mFullUrl);
        OmniboxUrlEmphasizer.emphasizeUrl(urlBuilder, mContext.getResources(), mProfile,
                mSecurityLevel, mIsInternalPage, true, true);
        mUrlTitle.setText(urlBuilder);

        if (mParsedUrl == null || mParsedUrl.getScheme() == null
                || !(mParsedUrl.getScheme().equals("http")
                           || mParsedUrl.getScheme().equals("https"))) {
            mSiteSettingsButton.setVisibility(View.GONE);
        }

        if (isShowingOfflinePage()) {
            boolean isConnected = OfflinePageUtils.isConnected();
            RecordHistogram.recordBooleanHistogram(
                    "OfflinePages.WebsiteSettings.OpenOnlineButtonVisible", isConnected);
            if (!isConnected) mOpenOnlineButton.setVisibility(View.GONE);
        } else {
            mOpenOnlineButton.setVisibility(View.GONE);
        }

        mInstantAppIntent = (mIsInternalPage || isShowingOfflinePage()) ? null
                : InstantAppsHandler.getInstance().getInstantAppIntentForUrl(mFullUrl);
        if (mInstantAppIntent == null) mInstantAppButton.setVisibility(View.GONE);

        // Create the dialog.
        mDialog = new Dialog(mContext) {
            private void superDismiss() {
                super.dismiss();
            }

            @Override
            public void dismiss() {
                if (DeviceFormFactor.isTablet(mContext) || mDismissWithoutAnimation) {
                    // Dismiss the dialog without any custom animations on tablet.
                    super.dismiss();
                } else {
                    Animator animator = createAllAnimations(false);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // onAnimationEnd is called during the final frame of the animation.
                            // Delay the cleanup by a tiny amount to give this frame a chance to be
                            // displayed before we destroy the dialog.
                            mContainer.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    superDismiss();
                                }
                            }, CLOSE_CLEANUP_DELAY);
                        }
                    });
                    animator.start();
                }
            }
        };
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setCanceledOnTouchOutside(true);

        // On smaller screens, place the dialog at the top of the screen, and remove its border.
        if (!DeviceFormFactor.isTablet(mContext)) {
            Window window = mDialog.getWindow();
            window.setGravity(Gravity.TOP);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // This needs to come after other member initialization.
        mNativeWebsiteSettingsPopup = nativeInit(this, mWebContents);
        final WebContentsObserver webContentsObserver = new WebContentsObserver(mWebContents) {
            @Override
            public void navigationEntryCommitted() {
                // If a navigation is committed (e.g. from in-page redirect), the data we're showing
                // is stale so dismiss the dialog.
                mDialog.dismiss();
            }

            @Override
            public void wasHidden() {
                // The web contents were hidden (potentially by loading another URL via an intent),
                // so dismiss the dialog).
                mDialog.dismiss();
            }

            @Override
            public void destroy() {
                super.destroy();
                // Force the dialog to close immediately in case the destroy was from Chrome
                // quitting.
                mDismissWithoutAnimation = true;
                mDialog.dismiss();
            }
        };
        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                assert mNativeWebsiteSettingsPopup != 0;
                webContentsObserver.destroy();
                nativeDestroy(mNativeWebsiteSettingsPopup);
                mNativeWebsiteSettingsPopup = 0;
            }
        });

        showDialog();
    }

    /**
     * Sets the visibility of the permissions list, which contains padding and borders that should
     * not be shown if a site has no permissions.
     *
     * @param isVisible Whether to show or hide the dialog area.
     */
    private void setVisibilityOfPermissionsList(boolean isVisible) {
        int visibility = isVisible ? View.VISIBLE : View.GONE;
        mPermissionsList.setVisibility(visibility);
    }

    /**
     * Finds the Image resource of the icon to use for the given permission.
     *
     * @param permission A valid ContentSettingsType that can be displayed in the PageInfo dialog to
     *                   retrieve the image for.
     * @return The resource ID of the icon to use for that permission.
     */
    private int getImageResourceForPermission(int permission) {
        int icon = ContentSettingsResources.getIcon(permission);
        assert icon != 0 : "Icon requested for invalid permission: " + permission;
        return icon;
    }

    /**
     * Whether to show a 'Details' link to the connection info popup. The link is only shown for
     * HTTPS connections.
     */
    private boolean isConnectionDetailsLinkVisible() {
        return mContentPublisher == null && !isShowingOfflinePage() && mParsedUrl != null
                && mParsedUrl.getScheme() != null && mParsedUrl.getScheme().equals("https");
    }

    private boolean hasAndroidPermission(int contentSettingType) {
        String androidPermission = PrefServiceBridge.getAndroidPermissionForContentSetting(
                contentSettingType);
        return androidPermission == null || mWindowAndroid.hasPermission(androidPermission);
    }

    /**
     * Adds a new row for the given permission.
     *
     * @param name The title of the permission to display to the user.
     * @param type The ContentSettingsType of the permission.
     * @param currentSettingValue The ContentSetting value of the currently selected setting.
     */
    @CalledByNative
    private void addPermissionSection(String name, int type, int currentSettingValue) {
        // We have at least one permission, so show the lower permissions area.
        setVisibilityOfPermissionsList(true);
        mDisplayedPermissions.add(new PageInfoPermissionEntry(name, type, ContentSetting
                .fromInt(currentSettingValue)));
    }

    /**
     * Update the permissions view based on the contents of mDisplayedPermissions.
     */
    @CalledByNative
    private void updatePermissionDisplay() {
        mPermissionsList.removeAllViews();
        for (PageInfoPermissionEntry permission : mDisplayedPermissions) {
            addReadOnlyPermissionSection(permission);
        }
    }

    private void addReadOnlyPermissionSection(PageInfoPermissionEntry permission) {
        View permissionRow = LayoutInflater.from(mContext).inflate(
                R.layout.website_settings_permission_row, null);

        ImageView permissionIcon = (ImageView) permissionRow.findViewById(
                R.id.website_settings_permission_icon);
        permissionIcon.setImageResource(getImageResourceForPermission(permission.type));

        if (permission.setting == ContentSetting.ALLOW) {
            int warningTextResource = 0;

            // If warningTextResource is non-zero, then the view must be tagged with either
            // permission_intent_override or permission_type.
            LocationUtils locationUtils = LocationUtils.getInstance();
            if (permission.type == ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION
                    && !locationUtils.isSystemLocationSettingEnabled()) {
                warningTextResource = R.string.page_info_android_location_blocked;
                permissionRow.setTag(R.id.permission_intent_override,
                        locationUtils.getSystemLocationSettingsIntent());
            } else if (!hasAndroidPermission(permission.type)) {
                warningTextResource = R.string.page_info_android_permission_blocked;
                permissionRow.setTag(R.id.permission_type,
                        PrefServiceBridge.getAndroidPermissionForContentSetting(permission.type));
            }

            if (warningTextResource != 0) {
                TextView permissionUnavailable = (TextView) permissionRow.findViewById(
                        R.id.website_settings_permission_unavailable_message);
                permissionUnavailable.setVisibility(View.VISIBLE);
                permissionUnavailable.setText(warningTextResource);

                permissionIcon.setImageResource(R.drawable.exclamation_triangle);
                permissionIcon.setColorFilter(ApiCompatibilityUtils.getColor(
                        mContext.getResources(), R.color.website_settings_popup_text_link));

                permissionRow.setOnClickListener(this);
            }
        }

        TextView permissionStatus = (TextView) permissionRow.findViewById(
                R.id.website_settings_permission_status);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString nameString = new SpannableString(permission.name);
        final StyleSpan boldSpan = new StyleSpan(android.graphics.Typeface.BOLD);
        nameString.setSpan(boldSpan, 0, nameString.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        builder.append(nameString);
        builder.append(" – ");  // en-dash.
        String status_text = "";
        switch (permission.setting) {
            case ALLOW:
                status_text = mContext.getString(R.string.page_info_permission_allowed);
                break;
            case BLOCK:
                status_text = mContext.getString(R.string.page_info_permission_blocked);
                break;
            default:
                assert false : "Invalid setting " + permission.setting + " for permission "
                        + permission.type;
        }
        builder.append(status_text);
        permissionStatus.setText(builder);
        mPermissionsList.addView(permissionRow);
    }

    /**
     * Sets the connection security summary and detailed description strings. These strings may be
     * overridden based on the state of the Android UI.
     */
    @CalledByNative
    private void setSecurityDescription(String summary, String details) {
        // Display the appropriate connection message.
        SpannableStringBuilder messageBuilder = new SpannableStringBuilder();
        if (mContentPublisher != null) {
            messageBuilder.append(
                    mContext.getString(R.string.page_info_domain_hidden, mContentPublisher));
        } else if (isShowingOfflinePage()) {
            messageBuilder.append(String.format(
                    mContext.getString(R.string.page_info_connection_offline),
                    mOfflinePageCreationDate));
        } else {
            if (!TextUtils.equals(summary, details)) {
                mConnectionSummary.setVisibility(View.VISIBLE);
                mConnectionSummary.setText(summary);
            }
            messageBuilder.append(details);
        }

        if (isConnectionDetailsLinkVisible()) {
            messageBuilder.append(" ");
            SpannableString detailsText = new SpannableString(
                    mContext.getString(R.string.page_info_details_link));
            final ForegroundColorSpan blueSpan = new ForegroundColorSpan(
                    ApiCompatibilityUtils.getColor(mContext.getResources(),
                            R.color.website_settings_popup_text_link));
            detailsText.setSpan(
                    blueSpan, 0, detailsText.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            messageBuilder.append(detailsText);
        }
        mConnectionMessage.setText(messageBuilder);
        if (isConnectionDetailsLinkVisible()) mConnectionMessage.setOnClickListener(this);
    }

    /**
     * Displays the WebsiteSettingsPopup.
     */
    private void showDialog() {
        if (!DeviceFormFactor.isTablet(mContext)) {
            // On smaller screens, make the dialog fill the width of the screen.
            ScrollView scrollView = new ScrollView(mContext);
            scrollView.addView(mContainer);
            mDialog.addContentView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));

            // This must be called after addContentView, or it won't fully fill to the edge.
            Window window = mDialog.getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            // On larger screens, make the dialog centered in the screen and have a maximum width.
            ScrollView scrollView = new ScrollView(mContext) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final int maxDialogWidthInPx = (int) (MAX_TABLET_DIALOG_WIDTH_DP
                            * mContext.getResources().getDisplayMetrics().density);
                    if (MeasureSpec.getSize(widthMeasureSpec) > maxDialogWidthInPx) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxDialogWidthInPx,
                                MeasureSpec.EXACTLY);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };

            scrollView.addView(mContainer);
            mDialog.addContentView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }

        mDialog.show();
    }

    /**
     * Dismiss the popup, and then run a task after the animation has completed (if there is one).
     */
    private void runAfterDismiss(Runnable task) {
        mDialog.dismiss();
        if (DeviceFormFactor.isTablet(mContext)) {
            task.run();
        } else {
            mContainer.postDelayed(task, FADE_DURATION + CLOSE_CLEANUP_DELAY);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == mSiteSettingsButton) {
            // Delay while the WebsiteSettingsPopup closes.
            runAfterDismiss(new Runnable() {
                @Override
                public void run() {
                    recordAction(WebsiteSettingsAction.WEBSITE_SETTINGS_SITE_SETTINGS_OPENED);
                    Bundle fragmentArguments =
                            SingleWebsitePreferences.createFragmentArgsForSite(mFullUrl);
                    fragmentArguments.putParcelable(SingleWebsitePreferences.EXTRA_WEB_CONTENTS,
                            mWebContents);
                    Intent preferencesIntent = PreferencesLauncher.createIntentForSettingsPage(
                            mContext, SingleWebsitePreferences.class.getName());
                    preferencesIntent.putExtra(
                            Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArguments);
                    mContext.startActivity(preferencesIntent);
                }
            });
        } else if (view == mInstantAppButton) {
            try {
                mContext.startActivity(mInstantAppIntent);
                RecordUserAction.record("Android.InstantApps.LaunchedFromWebsiteSettingsPopup");
            } catch (ActivityNotFoundException e) {
                mInstantAppButton.setEnabled(false);
            }
        } else if (view == mUrlTitle) {
            // Expand/collapse the displayed URL title.
            mUrlTitle.toggleTruncation();
        } else if (view == mConnectionMessage) {
            runAfterDismiss(new Runnable() {
                @Override
                public void run() {
                    if (!mWebContents.isDestroyed()) {
                        recordAction(
                                WebsiteSettingsAction.WEBSITE_SETTINGS_SECURITY_DETAILS_OPENED);
                        ConnectionInfoPopup.show(mContext, mWebContents);
                    }
                }
            });
        } else if (view.getId() == R.id.website_settings_permission_row) {
            final Object intentOverride = view.getTag(R.id.permission_intent_override);

            if (intentOverride == null && mWindowAndroid != null) {
                // Try and immediately request missing Android permissions where possible.
                final String permissionType = (String) view.getTag(R.id.permission_type);
                if (mWindowAndroid.canRequestPermission(permissionType)) {
                    final String[] permissionRequest = new String[] {permissionType};
                    mWindowAndroid.requestPermissions(permissionRequest, new PermissionCallback() {
                        @Override
                        public void onRequestPermissionsResult(
                                String[] permissions, int[] grantResults) {
                            if (grantResults.length > 0
                                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                updatePermissionDisplay();
                            }
                        }
                    });
                    return;
                }
            }

            runAfterDismiss(new Runnable() {
                @Override
                public void run() {
                    Intent settingsIntent;
                    if (intentOverride != null) {
                        settingsIntent = (Intent) intentOverride;
                    } else {
                        settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        settingsIntent.setData(Uri.parse("package:" + mContext.getPackageName()));
                    }
                    settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(settingsIntent);
                }
            });
        } else if (view == mOpenOnlineButton) {
            runAfterDismiss(new Runnable() {
                @Override
                public void run() {
                    // Attempt to reload to an online version of the viewed offline web page. This
                    // attempt might fail if the user is offline, in which case an offline copy will
                    // be reloaded.
                    RecordHistogram.recordBooleanHistogram(
                            "OfflinePages.WebsiteSettings.ConnectedWhenOpenOnlineButtonClicked",
                            OfflinePageUtils.isConnected());
                    OfflinePageUtils.reload(mTab);
                }
            });
        }
    }

    /**
     * Create a list of all the views which we want to individually fade in.
     */
    private List<View> collectAnimatableViews() {
        List<View> animatableViews = new ArrayList<View>();
        animatableViews.add(mUrlTitle);
        if (mConnectionSummary.getVisibility() == View.VISIBLE) {
            animatableViews.add(mConnectionSummary);
        }
        animatableViews.add(mConnectionMessage);
        animatableViews.add(mInstantAppButton);
        for (int i = 0; i < mPermissionsList.getChildCount(); i++) {
            animatableViews.add(mPermissionsList.getChildAt(i));
        }
        animatableViews.add(mSiteSettingsButton);

        return animatableViews;
    }

    /**
     * Create an animator to fade an individual dialog element.
     */
    private Animator createInnerFadeAnimator(final View view, int position, boolean isEnter) {
        ObjectAnimator alphaAnim;

        if (isEnter) {
            view.setAlpha(0f);
            alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f);
            alphaAnim.setStartDelay(FADE_IN_BASE_DELAY + FADE_IN_DELAY_OFFSET * position);
        } else {
            alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f);
        }

        alphaAnim.setDuration(FADE_DURATION);
        return alphaAnim;
    }

    /**
     * Create an animator to slide in the entire dialog from the top of the screen.
     */
    private Animator createDialogSlideAnimator(boolean isEnter) {
        final float animHeight = -1f * mContainer.getHeight();
        ObjectAnimator translateAnim;
        if (isEnter) {
            mContainer.setTranslationY(animHeight);
            translateAnim = ObjectAnimator.ofFloat(mContainer, View.TRANSLATION_Y, 0f);
            translateAnim.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);
        } else {
            translateAnim = ObjectAnimator.ofFloat(mContainer, View.TRANSLATION_Y, animHeight);
            translateAnim.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        }
        translateAnim.setDuration(FADE_DURATION);
        return translateAnim;
    }

    /**
     * Create animations for showing/hiding the popup.
     *
     * Tablets use the default Dialog fade-in instead of sliding in manually.
     */
    private Animator createAllAnimations(boolean isEnter) {
        AnimatorSet animation = new AnimatorSet();
        AnimatorSet.Builder builder = null;
        Animator startAnim;

        if (DeviceFormFactor.isTablet(mContext)) {
            // The start time of the entire AnimatorSet is the start time of the first animation
            // added to the Builder. We use a blank AnimatorSet on tablet as an easy way to
            // co-ordinate this start time.
            startAnim = new AnimatorSet();
        } else {
            startAnim = createDialogSlideAnimator(isEnter);
        }

        if (isEnter) startAnim.setStartDelay(ENTER_START_DELAY);
        builder = animation.play(startAnim);

        List<View> animatableViews = collectAnimatableViews();
        for (int i = 0; i < animatableViews.size(); i++) {
            View view = animatableViews.get(i);
            Animator anim = createInnerFadeAnimator(view, i, isEnter);
            builder.with(anim);
        }

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimation = null;
            }
        });
        if (mCurrentAnimation != null) mCurrentAnimation.cancel();
        mCurrentAnimation = animation;
        return animation;
    }

    private void recordAction(int action) {
        if (mNativeWebsiteSettingsPopup != 0) {
            nativeRecordWebsiteSettingsAction(mNativeWebsiteSettingsPopup, action);
        }
    }

    /**
     * Whether website dialog is displayed for an offline page.
     */
    private boolean isShowingOfflinePage() {
        return mOfflinePageCreationDate != null;
    }

    /**
     * Shows a WebsiteSettings dialog for the provided Tab. The popup adds itself to the view
     * hierarchy which owns the reference while it's visible.
     *
     * @param activity Activity which is used for launching a dialog.
     * @param tab The tab hosting the web contents for which to show Website information. This
     *            information is retrieved for the visible entry.
     * @param contentPublisher The name of the publisher of the content.
     * @param source Determines the source that triggered the popup.
     */
    public static void show(final Activity activity, final Tab tab, final String contentPublisher,
            @OpenedFromSource int source) {
        if (source == OPENED_FROM_MENU) {
            RecordUserAction.record("MobileWebsiteSettingsOpenedFromMenu");
        } else if (source == OPENED_FROM_TOOLBAR) {
            RecordUserAction.record("MobileWebsiteSettingsOpenedFromToolbar");
        } else {
            assert false : "Invalid source passed";
        }

        String offlinePageCreationDate = null;

        OfflinePageItem offlinePage = tab.getOfflinePage();
        if (offlinePage != null) {
            // Get formatted creation date of the offline page.
            Date creationDate = new Date(offlinePage.getCreationTimeMs());
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
            offlinePageCreationDate = df.format(creationDate);
        }

        new WebsiteSettingsPopup(activity, tab, offlinePageCreationDate, contentPublisher);
    }

    private static native long nativeInit(WebsiteSettingsPopup popup, WebContents webContents);

    private native void nativeDestroy(long nativeWebsiteSettingsPopupAndroid);

    private native void nativeRecordWebsiteSettingsAction(
            long nativeWebsiteSettingsPopupAndroid, int action);
}
