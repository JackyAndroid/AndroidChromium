// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import static org.chromium.chrome.browser.toolbar.ToolbarPhone.URL_FOCUS_CHANGE_ANIMATION_DURATION_MS;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.annotation.IntDef;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CollectionUtil;
import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.WindowDelegate;
import org.chromium.chrome.browser.appmenu.AppMenuButtonHelper;
import org.chromium.chrome.browser.ntp.NativePageFactory;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.ntp.NewTabPage.FakeboxDelegate;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.omnibox.AutocompleteController.OnSuggestionsReceivedListener;
import org.chromium.chrome.browser.omnibox.OmniboxResultsAdapter.OmniboxResultItem;
import org.chromium.chrome.browser.omnibox.OmniboxResultsAdapter.OmniboxSuggestionDelegate;
import org.chromium.chrome.browser.omnibox.VoiceSuggestionProvider.VoiceResult;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.omnibox.geo.GeolocationSnackbarController;
import org.chromium.chrome.browser.pageinfo.WebsiteSettingsPopup;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ActionModeController;
import org.chromium.chrome.browser.toolbar.ActionModeController.ActionBarDelegate;
import org.chromium.chrome.browser.toolbar.ToolbarActionModeCallback;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;
import org.chromium.chrome.browser.toolbar.ToolbarPhone;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.KeyNavigationUtil;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.chrome.browser.widget.TintedImageButton;
import org.chromium.chrome.browser.widget.animation.AnimatorProperties;
import org.chromium.chrome.browser.widget.animation.CancelAwareAnimatorListener;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This class represents the location bar where the user types in URLs and
 * search terms.
 */
public class LocationBarLayout extends FrameLayout implements OnClickListener,
        OnSuggestionsReceivedListener, LocationBar, FakeboxDelegate,
        WindowAndroid.IntentCallback {

    // Delay triggering the omnibox results upon key press to allow the location bar to repaint
    // with the new characters.
    private static final long OMNIBOX_SUGGESTION_START_DELAY_MS = 30;

    private static final int OMNIBOX_CONTAINER_BACKGROUND_FADE_MS = 250;

    // Delay showing the geolocation snackbar when the omnibox is focused until the keyboard is
    // hopefully visible.
    private static final int GEOLOCATION_SNACKBAR_SHOW_DELAY_MS = 750;

    // The minimum confidence threshold that will result in navigating directly to a voice search
    // response (as opposed to treating it like a typed string in the Omnibox).
    private static final float VOICE_SEARCH_CONFIDENCE_NAVIGATE_THRESHOLD = 0.9f;

    private static final int CONTENT_OVERLAY_COLOR = 0xA6000000;
    private static final int OMNIBOX_RESULTS_BG_COLOR = 0xFFF5F5F6;
    private static final int OMNIBOX_INCOGNITO_RESULTS_BG_COLOR = 0xFF323232;

    /**
     * URI schemes that ContentView can handle.
     *
     * Copied from UrlUtilities.java.  UrlUtilities uses a URI to check for schemes, which
     * is more strict than Uri and causes the path stripping to fail.
     *
     * The following additions have been made: "chrome", "ftp".
     */
    private static final HashSet<String> ACCEPTED_SCHEMES = CollectionUtil.newHashSet(
            "about", "data", "file", "ftp", "http", "https", "inline", "javascript", "chrome");
    private static final HashSet<String> UNSUPPORTED_SCHEMES_TO_SPLIT =
            CollectionUtil.newHashSet("file", "javascript", "data");

    protected ImageView mNavigationButton;
    protected TintedImageButton mSecurityButton;
    protected TextView mVerboseStatusTextView;
    protected TintedImageButton mDeleteButton;
    protected TintedImageButton mMicButton;
    protected UrlBar mUrlBar;
    private ActionModeController mActionModeController = null;

    private AutocompleteController mAutocomplete;

    protected ToolbarDataProvider mToolbarDataProvider;
    private UrlFocusChangeListener mUrlFocusChangeListener;

    protected boolean mNativeInitialized;

    private final List<Runnable> mDeferredNativeRunnables = new ArrayList<Runnable>();

    // The type of the navigation button currently showing.
    private NavigationButtonType mNavigationButtonType;

    // The type of the security icon currently active.
    private int mSecurityIconResource;

    private final OmniboxResultsAdapter mSuggestionListAdapter;
    private OmniboxSuggestionsList mSuggestionList;

    private final List<OmniboxResultItem> mSuggestionItems;

    /**
     * The text shown in the URL bar (user text + inline autocomplete) after the most recent set of
     * omnibox suggestions was received. When the user presses enter in the omnibox, this value is
     * compared to the URL bar text to determine whether the first suggestion is still valid.
     */
    private String mUrlTextAfterSuggestionsReceived;

    private boolean mIgnoreOmniboxItemSelection = true;

    private String mOriginalUrl = "";

    private WindowAndroid mWindowAndroid;
    private WindowDelegate mWindowDelegate;

    private Runnable mRequestSuggestions;

    private ViewGroup mOmniboxResultsContainer;
    private ObjectAnimator mFadeInOmniboxBackgroundAnimator;
    private ObjectAnimator mFadeOutOmniboxBackgroundAnimator;
    private Animator mOmniboxBackgroundAnimator;

    private boolean mSuggestionsShown;
    private boolean mUrlHasFocus;
    protected boolean mUrlFocusChangeInProgress;
    private boolean mUrlFocusedFromFakebox;
    private boolean mUrlFocusedWithoutAnimations;

    private boolean mVoiceSearchEnabled;

    // Set to true when the user has started typing new input in the omnibox, set to false
    // when the omnibox loses focus or becomes empty.
    private boolean mHasStartedNewOmniboxEditSession;
    // The timestamp (using SystemClock.elapsedRealtime()) at the point when the user started
    // modifying the omnibox with new input.
    private long mNewOmniboxEditSessionTimestamp = -1;

    @LocationBarButtonType private int mLocationBarButtonType;

    private AnimatorSet mLocationBarIconActiveAnimator;
    private AnimatorSet mSecurityButtonShowAnimator;
    private AnimatorSet mNavigationIconShowAnimator;

    private OmniboxPrerender mOmniboxPrerender;

    private boolean mSuggestionModalShown;
    private boolean mUseDarkColors;
    private boolean mIsEmphasizingHttpsScheme;

    // True if the user has just selected a suggestion from the suggestion list. This suppresses
    // the recording of the dismissal of the suggestion list. (The list is only considered to have
    // been dismissed if the user didn't choose one of the suggestions shown.) This signal is used
    // instead of a parameter to hideSuggestions because that method is often called from multiple
    // code paths in a not necessarily obvious or even deterministic order.
    private boolean mSuggestionSelectionInProgress;

    private ToolbarActionModeCallback mDefaultActionModeCallbackForTextEdit;

    private Runnable mShowSuggestions;

    /**
     * Listener for receiving the messages related with interacting with the omnibox during startup.
     */
    public interface OmniboxLivenessListener {
        /**
         * Called after the first draw when the omnibox can receive touch events.
         */
        void onOmniboxInteractive();

        /**
         * Called when the native libraries are loaded and listeners with native components
         * have been initialized.
         */
        void onOmniboxFullyFunctional();

        /**
         * Called when the omnibox is focused.
         */
        void onOmniboxFocused();
    }

    /**
     * Class to handle input from a hardware keyboard when the focus is on the URL bar. In
     * particular, handle navigating the suggestions list from the keyboard.
     */
    private final class UrlBarKeyListener implements OnKeyListener {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (KeyNavigationUtil.isGoDown(event)
                    && mSuggestionList != null
                    && mSuggestionList.isShown()) {
                int suggestionCount = mSuggestionListAdapter.getCount();
                if (mSuggestionList.getSelectedItemPosition() < suggestionCount - 1) {
                    if (suggestionCount > 0) mIgnoreOmniboxItemSelection = false;
                } else {
                    // Do not pass down events when the last item is already selected as it will
                    // dismiss the suggestion list.
                    return true;
                }

                if (mSuggestionList.getSelectedItemPosition()
                        == ListView.INVALID_POSITION) {
                    // When clearing the selection after a text change, state is not reset
                    // correctly so hitting down again will cause it to start from the previous
                    // selection point. We still have to send the key down event to let the list
                    // view items take focus, but then we select the first item explicitly.
                    boolean result = mSuggestionList.onKeyDown(keyCode, event);
                    mSuggestionList.setSelection(0);
                    return result;
                } else {
                    return mSuggestionList.onKeyDown(keyCode, event);
                }
            } else if (KeyNavigationUtil.isGoUp(event)
                    && mSuggestionList != null
                    && mSuggestionList.isShown()) {
                if (mSuggestionList.getSelectedItemPosition() != 0
                        && mSuggestionListAdapter.getCount() > 0) {
                    mIgnoreOmniboxItemSelection = false;
                }
                return mSuggestionList.onKeyDown(keyCode, event);
            } else if (KeyNavigationUtil.isGoRight(event)
                    && mSuggestionList != null
                    && mSuggestionList.isShown()
                    && mSuggestionList.getSelectedItemPosition()
                            != ListView.INVALID_POSITION) {
                OmniboxResultItem selectedItem =
                        (OmniboxResultItem) mSuggestionListAdapter.getItem(
                                mSuggestionList.getSelectedItemPosition());
                // Set the UrlBar text to empty, so that it will trigger a text change when we
                // set the text to the suggestion again.
                setUrlBarText("", null);
                mUrlBar.setText(selectedItem.getSuggestion().getFillIntoEdit());
                mSuggestionList.setSelection(0);
                mUrlBar.setSelection(mUrlBar.getText().length());
                return true;
            } else if (KeyNavigationUtil.isEnter(event)
                    && LocationBarLayout.this.getVisibility() == VISIBLE) {
                UiUtils.hideKeyboard(mUrlBar);
                mSuggestionSelectionInProgress = true;
                final String urlText = mUrlBar.getQueryText();
                if (mNativeInitialized) {
                    findMatchAndLoadUrl(urlText);
                } else {
                    mDeferredNativeRunnables.add(new Runnable() {
                        @Override
                        public void run() {
                            findMatchAndLoadUrl(urlText);
                        }
                    });
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    // Tell the framework to start tracking this event.
                    getKeyDispatcherState().startTracking(event, this);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    getKeyDispatcherState().handleUpEvent(event);
                    if (event.isTracking() && !event.isCanceled()) {
                        backKeyPressed();
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    revertChanges();
                    return true;
                }
            }
            return false;
        }

        private void findMatchAndLoadUrl(String urlText) {
            int suggestionMatchPosition;
            OmniboxSuggestion suggestionMatch;
            boolean skipOutOfBoundsCheck = false;

            if (mSuggestionList != null
                    && mSuggestionList.isShown()
                    && mSuggestionList.getSelectedItemPosition()
                    != ListView.INVALID_POSITION) {
                // Bluetooth keyboard case: the user highlighted a suggestion with the arrow
                // keys, then pressed enter.
                suggestionMatchPosition = mSuggestionList.getSelectedItemPosition();
                OmniboxResultItem selectedItem =
                        (OmniboxResultItem) mSuggestionListAdapter.getItem(suggestionMatchPosition);
                suggestionMatch = selectedItem.getSuggestion();
            } else if (!mSuggestionItems.isEmpty()
                    && urlText.equals(mUrlTextAfterSuggestionsReceived)) {
                // Common case: the user typed something, received suggestions, then pressed enter.
                suggestionMatch = mSuggestionItems.get(0).getSuggestion();
                suggestionMatchPosition = 0;
            } else {
                // Less common case: there are no valid omnibox suggestions. This can happen if the
                // user tapped the URL bar to dismiss the suggestions, then pressed enter. This can
                // also happen if the user presses enter before any suggestions have been received
                // from the autocomplete controller.
                suggestionMatch = mAutocomplete.classify(urlText);
                suggestionMatchPosition = 0;
                // Classify matches don't propagate to java, so skip the OOB check.
                skipOutOfBoundsCheck = true;

                // If urlText couldn't be classified, bail.
                if (suggestionMatch == null) return;
            }

            String suggestionMatchUrl = updateSuggestionUrlIfNeeded(suggestionMatch,
                        suggestionMatchPosition, skipOutOfBoundsCheck);


            // It's important to use the page transition from the suggestion or we might end
            // up saving generated URLs as typed URLs, which would then pollute the subsequent
            // omnibox results. There is one special case where the suggestion text was pasted,
            // where we want the transition type to be LINK.
            int transition = suggestionMatch.getType() == OmniboxSuggestionType.URL_WHAT_YOU_TYPED
                    && mUrlBar.isPastedText() ? PageTransition.LINK
                            : suggestionMatch.getTransition();

            loadUrlFromOmniboxMatch(suggestionMatchUrl, transition, suggestionMatchPosition,
                    suggestionMatch.getType());
        }
    }

    /**
     * Specifies the types of buttons shown to signify different types of navigation elements.
     */
    protected enum NavigationButtonType {
        PAGE,
        MAGNIFIER,
        EMPTY,
    }

    /** Specifies which button should be shown in location bar, if any. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BUTTON_TYPE_NONE, BUTTON_TYPE_SECURITY_ICON, BUTTON_TYPE_NAVIGATION_ICON})
    public @interface LocationBarButtonType {}
    /** No button should be shown. */
    public static final int BUTTON_TYPE_NONE = 0;
    /** Security button should be shown (includes offline icon). */
    public static final int BUTTON_TYPE_SECURITY_ICON = 1;
    /** Navigation button should be shown. */
    public static final int BUTTON_TYPE_NAVIGATION_ICON = 2;

    /**
     * @param outRect Populated with a {@link Rect} that represents the {@link Tab} specific content
     *                of this {@link LocationBar}.
     */
    public void getContentRect(Rect outRect) {
        outRect.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
    }

    /**
     * A widget for showing a list of omnibox suggestions.
     */
    @VisibleForTesting
    public class OmniboxSuggestionsList extends ListView {
        private final int mSuggestionHeight;
        private final int mSuggestionAnswerHeight;
        private final View mAnchorView;

        private final int[] mTempPosition = new int[2];
        private final Rect mTempRect = new Rect();

        private final int mBackgroundVerticalPadding;

        private float mMaxRequiredWidth;
        private float mMaxMatchContentsWidth;

        /**
         * Constructs a new list designed for containing omnibox suggestions.
         * @param context Context used for contained views.
         */
        public OmniboxSuggestionsList(Context context) {
            super(context, null, android.R.attr.dropDownListViewStyle);
            setDivider(null);
            setFocusable(true);
            setFocusableInTouchMode(true);

            mSuggestionHeight = context.getResources().getDimensionPixelOffset(
                    R.dimen.omnibox_suggestion_height);
            mSuggestionAnswerHeight = context.getResources().getDimensionPixelOffset(
                    R.dimen.omnibox_suggestion_answer_height);

            int paddingTop = context.getResources().getDimensionPixelOffset(
                    R.dimen.omnibox_suggestion_list_padding_top);
            int paddingBottom = context.getResources().getDimensionPixelOffset(
                    R.dimen.omnibox_suggestion_list_padding_bottom);
            ApiCompatibilityUtils.setPaddingRelative(this, 0, paddingTop, 0, paddingBottom);

            Drawable background = getSuggestionPopupBackground();
            setBackground(background);
            background.getPadding(mTempRect);

            mBackgroundVerticalPadding =
                    mTempRect.top + mTempRect.bottom + getPaddingTop() + getPaddingBottom();

            mAnchorView = LocationBarLayout.this.getRootView().findViewById(R.id.toolbar);
        }

        private void show() {
            updateLayoutParams();
            if (getVisibility() != VISIBLE) {
                mIgnoreOmniboxItemSelection = true;  // Reset to default value.
                setVisibility(VISIBLE);
                if (getSelectedItemPosition() != 0) setSelection(0);
            }
        }

        /**
         * Invalidates all of the suggestion views in the list.  Only applicable when this
         * is visible.
         */
        public void invalidateSuggestionViews() {
            if (!isShown()) return;
            ListView suggestionsList = mSuggestionList;
            for (int i = 0; i < suggestionsList.getChildCount(); i++) {
                if (suggestionsList.getChildAt(i) instanceof SuggestionView) {
                    suggestionsList.getChildAt(i).postInvalidateOnAnimation();
                }
            }
        }

        /**
         * Updates the maximum widths required to render the suggestions.
         * This is needed for infinite suggestions where we try to vertically align the leading
         * ellipsis.
         */
        public void resetMaxTextWidths() {
            mMaxRequiredWidth = 0;
            mMaxMatchContentsWidth = 0;
        }

        /**
         * Updates the max text width values for the suggestions.
         * @param requiredWidth a new required width.
         * @param matchContentsWidth a new match contents width.
         */
        public void updateMaxTextWidths(float requiredWidth, float matchContentsWidth) {
            mMaxRequiredWidth = Math.max(mMaxRequiredWidth, requiredWidth);
            mMaxMatchContentsWidth = Math.max(mMaxMatchContentsWidth, matchContentsWidth);
        }

        /**
         * @return max required width for the suggestions.
         */
        public float getMaxRequiredWidth() {
            return mMaxRequiredWidth;
        }

        /**
         * @return max match contents width for the suggestions.
         */
        public float getMaxMatchContentsWidth() {
            return mMaxMatchContentsWidth;
        }

        private void updateLayoutParams() {
            boolean updateLayout = false;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
            if (layoutParams == null) {
                layoutParams = new FrameLayout.LayoutParams(0, 0);
                setLayoutParams(layoutParams);
            }

            // Compare the relative positions of the anchor view to the list parent view to
            // determine the offset to apply to the suggestions list.  By using layout positioning,
            // this avoids issues where getLocationInWindow can be inaccurate on certain devices.
            View contentView =
                    LocationBarLayout.this.getRootView().findViewById(android.R.id.content);
            ViewUtils.getRelativeLayoutPosition(contentView, mAnchorView, mTempPosition);
            int anchorX = mTempPosition[0];
            int anchorY = mTempPosition[1];

            ViewUtils.getRelativeLayoutPosition(contentView, (View) getParent(), mTempPosition);
            int parentY = mTempPosition[1];

            int anchorBottomRelativeToContent = anchorY + mAnchorView.getMeasuredHeight();
            int desiredTopMargin = anchorBottomRelativeToContent - parentY;
            if (layoutParams.topMargin != desiredTopMargin) {
                layoutParams.topMargin = desiredTopMargin;
                updateLayout = true;
            }

            int contentLeft = contentView.getLeft();
            int anchorLeftRelativeToContent = anchorX - contentLeft;
            if (layoutParams.leftMargin != anchorLeftRelativeToContent) {
                layoutParams.leftMargin = anchorLeftRelativeToContent;
                updateLayout = true;
            }

            getWindowDelegate().getWindowVisibleDisplayFrame(mTempRect);
            int decorHeight = getWindowDelegate().getDecorViewHeight();
            int availableViewportHeight = Math.min(mTempRect.height(), decorHeight);
            int availableListHeight = availableViewportHeight - anchorBottomRelativeToContent;
            int desiredHeight = Math.min(availableListHeight, getIdealHeight());
            if (layoutParams.height != desiredHeight) {
                layoutParams.height = desiredHeight;
                updateLayout = true;
            }

            int desiredWidth = getDesiredWidth();
            if (layoutParams.width != desiredWidth) {
                layoutParams.width = desiredWidth;
                updateLayout = true;
            }

            if (updateLayout) requestLayout();
        }

        private int getIdealHeight() {
            int idealListSize = mBackgroundVerticalPadding;
            for (int i = 0; i < mSuggestionItems.size(); i++) {
                OmniboxResultItem item = mSuggestionItems.get(i);
                if (!TextUtils.isEmpty(item.getSuggestion().getAnswerContents())) {
                    int num = SuggestionView.parseNumAnswerLines(
                            item.getSuggestion().getAnswer().getSecondLine().getTextFields());
                    if (num > 1) {
                        idealListSize += mSuggestionAnswerHeight * 2;
                    } else {
                        idealListSize += mSuggestionAnswerHeight;
                    }
                } else {
                    idealListSize += mSuggestionHeight;
                }
            }
            return idealListSize;
        }

        private int getDesiredWidth() {
            return mAnchorView.getWidth();
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (!hasWindowFocus && !mSuggestionModalShown) hideSuggestions();
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            // In ICS, the selected view is not marked as selected despite calling setSelection(0),
            // so we bootstrap this after the children have been laid out.
            if (!isInTouchMode() && getSelectedView() != null) {
                getSelectedView().setSelected(true);
            }
        }

        private void updateSuggestionsLayoutDirection(int layoutDirection) {
            if (!isShown()) return;

            for (int i = 0; i < getChildCount(); i++) {
                View childView = getChildAt(i);
                if (!(childView instanceof SuggestionView)) continue;
                ApiCompatibilityUtils.setLayoutDirection(childView, layoutDirection);
            }
        }
    }

    public LocationBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.location_bar, this, true);
        mNavigationButton = (ImageView) findViewById(R.id.navigation_button);
        assert mNavigationButton != null : "Missing navigation type view.";

        mNavigationButtonType = DeviceFormFactor.isTablet(context)
                ? NavigationButtonType.PAGE : NavigationButtonType.EMPTY;

        mSecurityButton = (TintedImageButton) findViewById(R.id.security_button);
        mSecurityIconResource = 0;

        mVerboseStatusTextView = (TextView) findViewById(R.id.location_bar_verbose_status);

        mDeleteButton = (TintedImageButton) findViewById(R.id.delete_button);

        mUrlBar = (UrlBar) findViewById(R.id.url_bar);
        // The HTC Sense IME will attempt to autocomplete words in the Omnibox when Prediction is
        // enabled.  We want to disable this feature and rely on the Omnibox's implementation.
        // Their IME does not respect ~TYPE_TEXT_FLAG_AUTO_COMPLETE nor any of the other InputType
        // options I tried, but setting the filter variation prevents it.  Sadly, it also removes
        // the .com button, but the prediction was buggy as it would autocomplete words even when
        // typing at the beginning of the omnibox text when other content was present (messing up
        // what was previously there).  See bug: http://b/issue?id=6200071
        String defaultIme = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (defaultIme != null && defaultIme.contains("com.htc.android.htcime")) {
            mUrlBar.setInputType(mUrlBar.getInputType() | InputType.TYPE_TEXT_VARIATION_FILTER);
        }
        mUrlBar.setDelegate(this);

        mSuggestionItems = new ArrayList<OmniboxResultItem>();
        mSuggestionListAdapter = new OmniboxResultsAdapter(getContext(), this, mSuggestionItems);

        mMicButton = (TintedImageButton) findViewById(R.id.mic_button);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mUrlBar.setCursorVisible(false);

        mLocationBarButtonType = getLocationBarButtonToShow();
        mNavigationButton.setVisibility(
                mLocationBarButtonType == BUTTON_TYPE_NAVIGATION_ICON ? VISIBLE : INVISIBLE);
        mSecurityButton.setVisibility(
                mLocationBarButtonType == BUTTON_TYPE_SECURITY_ICON ? VISIBLE : INVISIBLE);

        setLayoutTransition(null);

        AnimatorListenerAdapter iconChangeAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == mSecurityButtonShowAnimator) {
                    mNavigationButton.setVisibility(INVISIBLE);
                } else if (animation == mNavigationIconShowAnimator) {
                    mSecurityButton.setVisibility(INVISIBLE);
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
                if (animation == mSecurityButtonShowAnimator) {
                    mSecurityButton.setVisibility(VISIBLE);
                } else if (animation == mNavigationIconShowAnimator) {
                    mNavigationButton.setVisibility(VISIBLE);
                }
            }
        };

        mSecurityButtonShowAnimator = new AnimatorSet();
        mSecurityButtonShowAnimator.playTogether(
                ObjectAnimator.ofFloat(mNavigationButton, ALPHA, 0),
                ObjectAnimator.ofFloat(mSecurityButton, ALPHA, 1));
        mSecurityButtonShowAnimator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        mSecurityButtonShowAnimator.addListener(iconChangeAnimatorListener);

        mNavigationIconShowAnimator = new AnimatorSet();
        mNavigationIconShowAnimator.playTogether(
                ObjectAnimator.ofFloat(mNavigationButton, ALPHA, 1),
                ObjectAnimator.ofFloat(mSecurityButton, ALPHA, 0));
        mNavigationIconShowAnimator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        mNavigationIconShowAnimator.addListener(iconChangeAnimatorListener);

        mUrlBar.setOnKeyListener(new UrlBarKeyListener());

        // mLocationBar's direction is tied to this UrlBar's text direction. Icons inside the
        // location bar, e.g. lock, refresh, X, should be reversed if UrlBar's text is RTL.
        mUrlBar.setUrlDirectionListener(new UrlBar.UrlDirectionListener() {
            @Override
            public void onUrlDirectionChanged(int layoutDirection) {
                ApiCompatibilityUtils.setLayoutDirection(LocationBarLayout.this, layoutDirection);

                if (mSuggestionList != null) {
                    mSuggestionList.updateSuggestionsLayoutDirection(layoutDirection);
                }
            }
        });

        mUrlBar.setSelectAllOnFocus(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateLayoutParams();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean retVal = super.dispatchKeyEvent(event);
        if (retVal && mUrlHasFocus && mUrlFocusedWithoutAnimations
                && event.getAction() == KeyEvent.ACTION_DOWN && event.isPrintingKey()
                && event.hasNoModifiers()) {
            handleUrlFocusAnimation(mUrlHasFocus);
        }
        return retVal;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mUrlHasFocus && mUrlFocusedWithoutAnimations
                && newConfig.keyboard != Configuration.KEYBOARD_QWERTY) {
            // If we lose the hardware keyboard and the focus animations were not run, then the
            // user has not typed any text, so we will just clear the focus instead.
            setUrlBarFocus(false);
        }
    }

    @Override
    public void initializeControls(WindowDelegate windowDelegate,
            ActionBarDelegate actionBarDelegate, WindowAndroid windowAndroid) {
        mWindowDelegate = windowDelegate;

        mActionModeController = new ActionModeController(getContext(), actionBarDelegate);
        mActionModeController.setCustomSelectionActionModeCallback(
                new ToolbarActionModeCallback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        boolean retVal = super.onCreateActionMode(mode, menu);
                        mode.getMenuInflater().inflate(R.menu.textselectionmenu, menu);
                        return retVal;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        if (item.getItemId() == R.id.copy_url) {
                            ClipboardManager clipboard =
                                    (ClipboardManager) getContext().getSystemService(
                                            Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("url", mOriginalUrl);
                            clipboard.setPrimaryClip(clip);
                            mode.finish();
                            return true;
                        } else {
                            return super.onActionItemClicked(mode, item);
                        }
                    }
                });

        mWindowAndroid = windowAndroid;

        // If the user focused the omnibox prior to the native libraries being initialized,
        // autocomplete will not always be enabled, so we force it enabled in that case.
        mUrlBar.setIgnoreTextChangesForAutocomplete(false);
    }

    /**
     * @param focusable Whether the url bar should be focusable.
     */
    public void setUrlBarFocusable(boolean focusable) {
        if (mUrlBar == null) return;
        mUrlBar.setFocusable(focusable);
        mUrlBar.setFocusableInTouchMode(focusable);
    }

    /**
     * @return The WindowDelegate for the LocationBar. This should be used for all Window related
     * state queries.
     */
    protected WindowDelegate getWindowDelegate() {
        return mWindowDelegate;
    }

    /**
     * Handles native dependent initialization for this class.
     */
    @Override
    public void onNativeLibraryReady() {
        mNativeInitialized = true;

        mSecurityButton.setOnClickListener(this);
        mNavigationButton.setOnClickListener(this);
        mVerboseStatusTextView.setOnClickListener(this);
        updateMicButtonState();
        mDeleteButton.setOnClickListener(this);
        mMicButton.setOnClickListener(this);

        mAutocomplete = new AutocompleteController(this);

        mOmniboxPrerender = new OmniboxPrerender();

        for (Runnable deferredRunnable : mDeferredNativeRunnables) {
            post(deferredRunnable);
        }
        mDeferredNativeRunnables.clear();

        mUrlBar.onOmniboxFullyFunctional();

        updateCustomSelectionActionModeCallback();
        updateVisualsForState();
    }

    /**
     * @return Whether or not to animate icon changes.
     */
    protected boolean shouldAnimateIconChanges() {
        return mUrlHasFocus;
    }

    /**
     * Sets the autocomplete controller for the location bar.
     *
     * @param controller The controller that will handle autocomplete/omnibox suggestions.
     * @note Only used for testing.
     */
    @VisibleForTesting
    public void setAutocompleteController(AutocompleteController controller) {
        if (mAutocomplete != null) stopAutocomplete(true);
        mAutocomplete = controller;
    }

    /**
     * Updates the profile used for generating autocomplete suggestions.
     * @param profile The profile to be used.
     */
    @Override
    public void setAutocompleteProfile(Profile profile) {
        // This will only be called once at least one tab exists, and the tab model is told to
        // update its state. During Chrome initialization the tab model update happens after the
        // call to onNativeLibraryReady, so this assert will not fire.
        assert mNativeInitialized :
                "Setting Autocomplete Profile before native side initialized";
        mAutocomplete.setProfile(profile);
        mOmniboxPrerender.initializeForProfile(profile);
    }

    @LocationBarButtonType private int getLocationBarButtonToShow() {
        boolean isOffline = getCurrentTab() != null && getCurrentTab().isOfflinePage();
        boolean isTablet = DeviceFormFactor.isTablet(getContext());

        if (mUrlHasFocus) {
            return isTablet ? BUTTON_TYPE_NAVIGATION_ICON : BUTTON_TYPE_NONE;
        }

        return getSecurityIconResource(getSecurityLevel(), !isTablet, isOffline) != 0
                ? BUTTON_TYPE_SECURITY_ICON
                : BUTTON_TYPE_NONE;
    }

    private void changeLocationBarIcon() {
        if (mLocationBarIconActiveAnimator != null && mLocationBarIconActiveAnimator.isRunning()) {
            mLocationBarIconActiveAnimator.cancel();
        }

        mLocationBarButtonType = getLocationBarButtonToShow();

        View viewToBeShown = null;
        switch (mLocationBarButtonType) {
            case BUTTON_TYPE_SECURITY_ICON:
                viewToBeShown = mSecurityButton;
                mLocationBarIconActiveAnimator = mSecurityButtonShowAnimator;
                break;
            case BUTTON_TYPE_NAVIGATION_ICON:
                viewToBeShown = mNavigationButton;
                mLocationBarIconActiveAnimator = mNavigationIconShowAnimator;
                break;
            case BUTTON_TYPE_NONE:
            default:
                mLocationBarIconActiveAnimator = null;
                return;
        }

        if (viewToBeShown.getVisibility() == VISIBLE && viewToBeShown.getAlpha() == 1) {
            return;
        }
        if (shouldAnimateIconChanges()) {
            mLocationBarIconActiveAnimator.setDuration(URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
        } else {
            mLocationBarIconActiveAnimator.setDuration(0);
        }
        mLocationBarIconActiveAnimator.start();
    }

    @Override
    public void setUrlBarFocus(boolean shouldBeFocused) {
        if (shouldBeFocused) {
            mUrlBar.requestFocus();
        } else {
            mUrlBar.clearFocus();
        }
    }

    @Override
    public boolean isUrlBarFocused() {
        return mUrlHasFocus;
    }

    @Override
    public void selectAll() {
        mUrlBar.selectAll();
    }

    @Override
    public void revertChanges() {
        if (!mUrlHasFocus) {
            setUrlToPageUrl();
        } else {
            Tab tab = mToolbarDataProvider.getTab();
            if (NativePageFactory.isNativePageUrl(tab.getUrl(), tab.isIncognito())) {
                setUrlBarText("", null);
            } else {
                setUrlBarText(
                        mToolbarDataProvider.getText(), getCurrentTabUrl());
                selectAll();
            }
            hideSuggestions();
            UiUtils.hideKeyboard(mUrlBar);
        }
    }

    @Override
    public long getFirstUrlBarFocusTime() {
        return mUrlBar.getFirstFocusTime();
    }

    /**
     * @return Whether the URL focus change is taking place, e.g. a focus animation is running on
     *         a phone device.
     */
    protected boolean isUrlFocusChangeInProgress() {
        return mUrlFocusChangeInProgress;
    }

    /**
     * @param inProgress Whether a URL focus change is taking place.
     */
    protected void setUrlFocusChangeInProgress(boolean inProgress) {
        mUrlFocusChangeInProgress = inProgress;
        if (!inProgress) {
            updateButtonVisibility();
        }
    }

    /**
     * Triggered when the URL input field has gained or lost focus.
     * @param hasFocus Whether the URL field has gained focus.
     */
    public void onUrlFocusChange(boolean hasFocus) {
        mUrlHasFocus = hasFocus;
        updateButtonVisibility();
        updateNavigationButton();
        Tab currentTab = getCurrentTab();
        if (hasFocus) {
            if (mNativeInitialized) RecordUserAction.record("FocusLocation");
            mUrlBar.deEmphasizeUrl();
        } else {
            mUrlFocusedFromFakebox = false;
            mUrlFocusedWithoutAnimations = false;
            hideSuggestions();

            // Focus change caused by a close-tab may result in an invalid current tab.
            if (currentTab != null) {
                setUrlToPageUrl();
                emphasizeUrl();
            }
        }

        if (getToolbarDataProvider().isUsingBrandColor()) {
            updateVisualsForState();
            if (mUrlHasFocus) mUrlBar.selectAll();
        }

        changeLocationBarIcon();
        updateVerboseStatusVisibility();
        updateLocationBarIconContainerVisibility();
        mUrlBar.setCursorVisible(hasFocus);

        if (!mUrlFocusedWithoutAnimations) handleUrlFocusAnimation(hasFocus);

        if (hasFocus && currentTab != null && !currentTab.isIncognito()) {
            if (mNativeInitialized
                    && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()) {
                GeolocationHeader.primeLocationForGeoHeader(getContext());
            } else {
                mDeferredNativeRunnables.add(new Runnable() {
                    @Override
                    public void run() {
                        if (TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()) {
                            GeolocationHeader.primeLocationForGeoHeader(getContext());
                        }
                    }
                });
            }
        }

        if (mNativeInitialized) {
            startZeroSuggest();
        } else {
            mDeferredNativeRunnables.add(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(mUrlBar.getQueryText())) {
                        startZeroSuggest();
                    }
                }
            });
        }

        if (!hasFocus) {
            mHasStartedNewOmniboxEditSession = false;
            mNewOmniboxEditSessionTimestamp = -1;
        }

        if (hasFocus && currentTab != null) {
            ChromeActivity activity = (ChromeActivity) mWindowAndroid.getActivity().get();
            if (activity != null) {
                GeolocationSnackbarController.maybeShowSnackbar(activity.getSnackbarManager(),
                        LocationBarLayout.this, currentTab.isIncognito(),
                        GEOLOCATION_SNACKBAR_SHOW_DELAY_MS);
            }
        }
    }

    /**
     * Handle and run any necessary animations that are triggered off focusing the UrlBar.
     * @param hasFocus Whether focus was gained.
     */
    protected void handleUrlFocusAnimation(boolean hasFocus) {
        if (hasFocus) mUrlFocusedWithoutAnimations = false;
        if (mUrlFocusChangeListener != null) mUrlFocusChangeListener.onUrlFocusChange(hasFocus);

        updateOmniboxResultsContainer();
        if (hasFocus) updateOmniboxResultsContainerBackground(true);
    }

    /**
     * Make a zero suggest request if native is loaded, the URL bar has focus, and the
     * current tab is not incognito.
     */
    private void startZeroSuggest() {
        // Reset "edited" state in the omnibox if zero suggest is triggered -- new edits
        // now count as a new session.
        mHasStartedNewOmniboxEditSession = false;
        mNewOmniboxEditSessionTimestamp = -1;
        Tab currentTab = getCurrentTab();
        if (mNativeInitialized
                && mUrlHasFocus
                && currentTab != null
                && !currentTab.isIncognito()) {
            mAutocomplete.startZeroSuggest(currentTab.getProfile(), mUrlBar.getQueryText(),
                    currentTab.getUrl(), mUrlFocusedFromFakebox);
        }
    }

    @Override
    public void onTextChangedForAutocomplete(final boolean textDeleted) {
        cancelPendingAutocompleteStart();

        updateButtonVisibility();
        updateNavigationButton();

        if (!mHasStartedNewOmniboxEditSession && mNativeInitialized) {
            mAutocomplete.resetSession();
            mHasStartedNewOmniboxEditSession = true;
            mNewOmniboxEditSessionTimestamp = SystemClock.elapsedRealtime();
        }

        if (!isInTouchMode() && mSuggestionList != null) {
            mSuggestionList.setSelection(0);
        }

        stopAutocomplete(false);
        if (TextUtils.isEmpty(mUrlBar.getTextWithoutAutocomplete())) {
            hideSuggestions();
            startZeroSuggest();
        } else {
            assert mRequestSuggestions == null : "Multiple omnibox requests in flight.";
            mRequestSuggestions = new Runnable() {
                @Override
                public void run() {
                    String textWithoutAutocomplete = mUrlBar.getTextWithoutAutocomplete();

                    boolean preventAutocomplete = textDeleted || !mUrlBar.shouldAutocomplete();
                    mRequestSuggestions = null;
                    if (getCurrentTab() == null) return;
                    mAutocomplete.start(
                            getCurrentTab().getProfile(),
                            getCurrentTab().getUrl(),
                            textWithoutAutocomplete, preventAutocomplete);
                }
            };
            if (mNativeInitialized) {
                postDelayed(mRequestSuggestions, OMNIBOX_SUGGESTION_START_DELAY_MS);
            } else {
                mDeferredNativeRunnables.add(mRequestSuggestions);
            }
        }

        // Occasionally, was seeing the selection in the URL not being cleared during
        // very rapid editing.  This is here to hopefully force a selection reset during
        // deletes.
        if (textDeleted) mUrlBar.setSelection(mUrlBar.getSelectionStart());
    }

    @Override
    public void setDefaultTextEditActionModeCallback(ToolbarActionModeCallback callback) {
        mDefaultActionModeCallbackForTextEdit = callback;
    }

    /**
     * If query in the omnibox, sets UrlBar's ActionModeCallback to show copy url button. Else,
     * it is set to the default one.
     */
    private void updateCustomSelectionActionModeCallback() {
        mUrlBar.setCustomSelectionActionModeCallback(mDefaultActionModeCallbackForTextEdit);
    }

    @Override
    public void requestUrlFocusFromFakebox(String pastedText) {
        mUrlFocusedFromFakebox = true;
        if (mUrlHasFocus && mUrlFocusedWithoutAnimations) {
            handleUrlFocusAnimation(mUrlHasFocus);
        } else {
            setUrlBarFocus(true);
        }

        if (pastedText != null) {
            // This must be happen after requestUrlFocus(), which changes the selection.
            mUrlBar.setUrl(pastedText, null);
            mUrlBar.setSelection(mUrlBar.getText().length());
        }
    }

    @Override
    public boolean isCurrentPage(NativePage nativePage) {
        assert nativePage != null;
        return nativePage == mToolbarDataProvider.getNewTabPageForCurrentTab();
    }

    @Override
    public void showUrlBarCursorWithoutFocusAnimations() {
        if (mUrlHasFocus || mUrlFocusedFromFakebox) return;

        mUrlFocusedWithoutAnimations = true;
        setUrlBarFocus(true);
    }

    /**
     * Sets the toolbar that owns this LocationBar.
     */
    @Override
    public void setToolbarDataProvider(ToolbarDataProvider toolbarDataProvider) {
        mToolbarDataProvider = toolbarDataProvider;

        mUrlBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, final boolean hasFocus) {
                onUrlFocusChange(hasFocus);
            }
        });
    }

    @Override
    public void setMenuButtonHelper(AppMenuButtonHelper helper) { }

    @Override
    public View getMenuAnchor() {
        return null;
    }

    /**
     * Sets the URL focus change listner that will be notified when the URL gains or loses focus.
     * @param listener The listener to be registered.
     */
    @Override
    public void setUrlFocusChangeListener(UrlFocusChangeListener listener) {
        mUrlFocusChangeListener = listener;
    }

    /**
     * @return The toolbar data provider.
     */
    @VisibleForTesting
    protected ToolbarDataProvider getToolbarDataProvider() {
        return mToolbarDataProvider;
    }

    private static NavigationButtonType suggestionTypeToNavigationButtonType(
            OmniboxSuggestion suggestion) {
        if (suggestion.isUrlSuggestion()) {
            return NavigationButtonType.PAGE;
        } else {
            return NavigationButtonType.MAGNIFIER;
        }
    }

    // Updates the navigation button based on the URL string
    private void updateNavigationButton() {
        boolean isTablet = DeviceFormFactor.isTablet(getContext());
        NavigationButtonType type = NavigationButtonType.EMPTY;
        if (isTablet && !mSuggestionItems.isEmpty()) {
            // If there are suggestions showing, show the icon for the default suggestion.
            type = suggestionTypeToNavigationButtonType(
                    mSuggestionItems.get(0).getSuggestion());
        } else if (isTablet) {
            type = NavigationButtonType.PAGE;
        }

        if (type != mNavigationButtonType) setNavigationButtonType(type);
    }

    private int getSecurityLevel() {
        if (getCurrentTab() == null) return ConnectionSecurityLevel.NONE;
        return getCurrentTab().getSecurityLevel();
    }

    /**
     * Determines the icon that should be displayed for the current security level.
     * @param securityLevel The security level for which the resource will be returned.
     * @param isSmallDevice Whether the device form factor is small (like a phone) or large
     * (like a tablet).
     * @return The resource ID of the icon that should be displayed, 0 if no icon should show.
     */
    public static int getSecurityIconResource(
            int securityLevel, boolean isSmallDevice, boolean isOfflinePage) {
        // Both conditions should be met, because isOfflinePage might take longer to be cleared.
        if (securityLevel == ConnectionSecurityLevel.NONE && isOfflinePage) {
            return R.drawable.offline_pin_round;
        }
        switch (securityLevel) {
            case ConnectionSecurityLevel.NONE:
            case ConnectionSecurityLevel.HTTP_SHOW_WARNING:
                return isSmallDevice ? 0 : R.drawable.omnibox_info;
            case ConnectionSecurityLevel.SECURITY_WARNING:
                return R.drawable.omnibox_info;
            case ConnectionSecurityLevel.DANGEROUS:
                return R.drawable.omnibox_https_invalid;
            case ConnectionSecurityLevel.SECURE:
            case ConnectionSecurityLevel.EV_SECURE:
                return R.drawable.omnibox_https_valid;
            default:
                assert false;
        }
        return 0;
    }

    /**
     * @param securityLevel The security level for which the color will be returned.
     * @param provider The {@link ToolbarDataProvider}.
     * @param resources The Resources for the Context.
     * @param isOmniboxOpaque Whether the omnibox is an opaque color.
     * @return The {@link ColorStateList} to use to tint the security state icon.
     */
    public static ColorStateList getColorStateList(int securityLevel, ToolbarDataProvider provider,
            Resources resources, boolean isOmniboxOpaque) {
        ColorStateList list = null;
        int color = provider.getPrimaryColor();
        boolean needLightIcon = ColorUtils.shouldUseLightForegroundOnBackground(color);
        if (provider.isIncognito() || needLightIcon) {
            // For a dark theme color, use light icons.
            list = ApiCompatibilityUtils.getColorStateList(resources, R.color.light_mode_tint);
        } else if (!ColorUtils.isUsingDefaultToolbarColor(resources, color) && !isOmniboxOpaque) {
            // For theme colors which are not dark and are also not
            // light enough to warrant an opaque URL bar, use dark
            // icons.
            list = ApiCompatibilityUtils.getColorStateList(resources, R.color.dark_mode_tint);
        } else {
            // For the default toolbar color, use a green or red icon.
            if (securityLevel == ConnectionSecurityLevel.DANGEROUS) {
                list = ApiCompatibilityUtils.getColorStateList(resources, R.color.google_red_700);
            } else if (securityLevel == ConnectionSecurityLevel.SECURE
                    || securityLevel == ConnectionSecurityLevel.EV_SECURE) {
                list = ApiCompatibilityUtils.getColorStateList(resources, R.color.google_green_700);
            } else {
                list = ApiCompatibilityUtils.getColorStateList(resources, R.color.dark_mode_tint);
            }
        }
        assert list != null : "Missing ColorStateList for Security Button.";
        return list;
    }

    /**
     * Updates the security icon displayed in the LocationBar.
     */
    @Override
    public void updateSecurityIcon(int securityLevel) {
        boolean isSmallDevice = !DeviceFormFactor.isTablet(getContext());
        boolean isOfflinePage = getCurrentTab() != null && getCurrentTab().isOfflinePage();
        int id = getSecurityIconResource(securityLevel, isSmallDevice, isOfflinePage);
        if (id == 0) {
            mSecurityButton.setImageDrawable(null);
        } else {
            // ImageView#setImageResource is no-op if given resource is the current one.
            mSecurityButton.setImageResource(id);
            mSecurityButton.setTint(getColorStateList(securityLevel, getToolbarDataProvider(),
                    getResources(), ColorUtils.shouldUseOpaqueTextboxBackground(
                            getToolbarDataProvider().getPrimaryColor())));
        }

        updateVerboseStatusVisibility();

        boolean shouldEmphasizeHttpsScheme = shouldEmphasizeHttpsScheme();
        if (mSecurityIconResource == id
                && mIsEmphasizingHttpsScheme == shouldEmphasizeHttpsScheme) {
            return;
        }
        mSecurityIconResource = id;

        changeLocationBarIcon();
        updateLocationBarIconContainerVisibility();
        // Since we emphasize the scheme of the URL based on the security type, we need to
        // refresh the emphasis.
        mUrlBar.deEmphasizeUrl();
        emphasizeUrl();
        mIsEmphasizingHttpsScheme = shouldEmphasizeHttpsScheme;
    }

    private void emphasizeUrl() {
        mUrlBar.emphasizeUrl();
    }

    @Override
    public boolean shouldEmphasizeHttpsScheme() {
        ToolbarDataProvider provider = getToolbarDataProvider();
        if (provider.isUsingBrandColor() || provider.isIncognito()) return false;
        return true;
    }

    /**
     * @return Whether the security button is currently being displayed.
     */
    @VisibleForTesting
    public boolean isSecurityButtonShown() {
        return mLocationBarButtonType == BUTTON_TYPE_SECURITY_ICON;
    }

    /**
     * Sets the type of the current navigation type and updates the UI to match it.
     * @param buttonType The type of navigation button to be shown.
     */
    private void setNavigationButtonType(NavigationButtonType buttonType) {
        if (!DeviceFormFactor.isTablet(getContext())) return;
        switch (buttonType) {
            case PAGE:
                Drawable page = ApiCompatibilityUtils.getDrawable(
                        getResources(), R.drawable.ic_omnibox_page);
                page.setColorFilter(mUseDarkColors
                        ? ApiCompatibilityUtils.getColor(getResources(), R.color.light_normal_color)
                        : Color.WHITE, PorterDuff.Mode.SRC_IN);
                mNavigationButton.setImageDrawable(page);
                break;
            case MAGNIFIER:
                mNavigationButton.setImageResource(R.drawable.ic_omnibox_magnifier);
                break;
            case EMPTY:
                mNavigationButton.setImageDrawable(null);
                break;
            default:
                assert false;
        }

        if (mNavigationButton.getVisibility() != VISIBLE) {
            mNavigationButton.setVisibility(VISIBLE);
        }
        mNavigationButtonType = buttonType;

        updateLocationBarIconContainerVisibility();
    }

    /**
     * Update visibility of the verbose status based on the button type and focus state of the
     * omnibox.
     */
    private void updateVerboseStatusVisibility() {
        // Because is offline page is cleared a bit slower, we also ensure that connection security
        // level is NONE.
        boolean verboseStatusVisible = !mUrlHasFocus && getCurrentTab() != null
                && getCurrentTab().isOfflinePage()
                && getSecurityLevel() == ConnectionSecurityLevel.NONE;

        int verboseStatusVisibility = verboseStatusVisible ? VISIBLE : GONE;

        mVerboseStatusTextView.setTextColor(ApiCompatibilityUtils.getColor(getResources(),
                mUseDarkColors ? R.color.locationbar_status_color
                        : R.color.locationbar_status_color_light));
        mVerboseStatusTextView.setVisibility(verboseStatusVisibility);

        View separator = findViewById(R.id.location_bar_verbose_status_separator);
        separator.setBackgroundColor(ApiCompatibilityUtils.getColor(getResources(), mUseDarkColors
                ? R.color.locationbar_status_separator_color
                : R.color.locationbar_status_separator_color_light));
        separator.setVisibility(verboseStatusVisibility);

        findViewById(R.id.location_bar_verbose_status_extra_space)
                .setVisibility(verboseStatusVisibility);
    }

    /**
     * Update the visibility of the location bar icon container based on the state of the
     * security and navigation icons.
     */
    protected void updateLocationBarIconContainerVisibility() {
        @LocationBarButtonType
        int buttonToShow = getLocationBarButtonToShow();
        findViewById(R.id.location_bar_icon)
                .setVisibility(buttonToShow != BUTTON_TYPE_NONE ? VISIBLE : GONE);
    }

    /**
     * Updates the layout params for the location bar start aligned views.
     */
    protected void updateLayoutParams() {
        int startMargin = 0;
        int urlContainerChildIndex = -1;
        for (int i = 0; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                LayoutParams childLayoutParams = (LayoutParams) childView.getLayoutParams();
                if (ApiCompatibilityUtils.getMarginStart(childLayoutParams) != startMargin) {
                    ApiCompatibilityUtils.setMarginStart(childLayoutParams, startMargin);
                    childView.setLayoutParams(childLayoutParams);
                }
                if (childView == mUrlBar) {
                    urlContainerChildIndex = i;
                    break;
                }
                int widthMeasureSpec;
                int heightMeasureSpec;
                if (childLayoutParams.width == LayoutParams.WRAP_CONTENT) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredWidth(), MeasureSpec.AT_MOST);
                } else if (childLayoutParams.width == LayoutParams.MATCH_PARENT) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredWidth(), MeasureSpec.EXACTLY);
                } else {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                            childLayoutParams.width, MeasureSpec.EXACTLY);
                }
                if (childLayoutParams.height == LayoutParams.WRAP_CONTENT) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredHeight(), MeasureSpec.AT_MOST);
                } else if (childLayoutParams.height == LayoutParams.MATCH_PARENT) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            getMeasuredHeight(), MeasureSpec.EXACTLY);
                } else {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                            childLayoutParams.height, MeasureSpec.EXACTLY);
                }
                childView.measure(widthMeasureSpec, heightMeasureSpec);
                startMargin += childView.getMeasuredWidth();
            }
        }

        assert urlContainerChildIndex != -1;
        int urlContainerMarginEnd = 0;
        for (int i = urlContainerChildIndex + 1; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            if (childView.getVisibility() != GONE) {
                LayoutParams childLayoutParams = (LayoutParams) childView.getLayoutParams();
                urlContainerMarginEnd = Math.max(urlContainerMarginEnd,
                        childLayoutParams.width
                                + ApiCompatibilityUtils.getMarginStart(childLayoutParams)
                                + ApiCompatibilityUtils.getMarginEnd(childLayoutParams));
            }
        }
        LayoutParams urlLayoutParams = (LayoutParams) mUrlBar.getLayoutParams();
        if (ApiCompatibilityUtils.getMarginEnd(urlLayoutParams) != urlContainerMarginEnd) {
            ApiCompatibilityUtils.setMarginEnd(urlLayoutParams, urlContainerMarginEnd);
            mUrlBar.setLayoutParams(urlLayoutParams);
        }
    }

    /**
     * @return Whether the delete button should be shown.
     */
    protected boolean shouldShowDeleteButton() {
        // Show the delete button at the end when the bar has focus and has some text.
        boolean hasText = !TextUtils.isEmpty(mUrlBar.getText());
        return hasText && (mUrlBar.hasFocus() || mUrlFocusChangeInProgress);
    }

    /**
     * Updates the display of the delete URL content button.
     */
    protected void updateDeleteButtonVisibility() {
        mDeleteButton.setVisibility(shouldShowDeleteButton() ? VISIBLE : GONE);
    }

    /**
     * @return The suggestion list popup containing the omnibox results (or
     *         null if it has not yet been created).
     */
    @VisibleForTesting
    public OmniboxSuggestionsList getSuggestionList() {
        return mSuggestionList;
    }

    /**
     * Initiates the mSuggestionListPopup.  Done on demand to not slow down
     * the initial inflation of the location bar.
     */
    private void initSuggestionList() {
        // Only called from onSuggestionsReceived(), which is a callback from a listener set up by
        // onNativeLibraryReady(), so this assert is safe.
        assert mNativeInitialized : "Trying to initialize suggestions list before native init";
        if (mSuggestionList != null) return;

        OnLayoutChangeListener suggestionListResizer = new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // On ICS, this update does not take affect unless it is posted to the end of the
                // current message queue.
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mSuggestionList.isShown()) mSuggestionList.updateLayoutParams();
                    }
                });
            }
        };
        getRootView().findViewById(R.id.control_container)
                .addOnLayoutChangeListener(suggestionListResizer);

        mSuggestionList = new OmniboxSuggestionsList(getContext());

        // Ensure the results container is initialized and add the suggestion list to it.
        initOmniboxResultsContainer();
        mOmniboxResultsContainer.addView(mSuggestionList);

        // Start with visibility GONE to ensure that show() is called. http://crbug.com/517438
        mSuggestionList.setVisibility(GONE);
        mSuggestionList.setAdapter(mSuggestionListAdapter);
        mSuggestionList.setClipToPadding(false);
        mSuggestionListAdapter.setSuggestionDelegate(new OmniboxSuggestionDelegate() {
            @Override
            public void onSelection(OmniboxSuggestion suggestion, int position) {
                mSuggestionSelectionInProgress = true;
                String suggestionMatchUrl = updateSuggestionUrlIfNeeded(
                        suggestion, position, false);
                loadUrlFromOmniboxMatch(suggestionMatchUrl, suggestion.getTransition(), position,
                        suggestion.getType());
                hideSuggestions();
                UiUtils.hideKeyboard(mUrlBar);
            }

            @Override
            public void onRefineSuggestion(OmniboxSuggestion suggestion) {
                stopAutocomplete(false);
                mUrlBar.setUrl(suggestion.getFillIntoEdit(), null);
                mUrlBar.setSelection(mUrlBar.getText().length());
                RecordUserAction.record("MobileOmniboxRefineSuggestion");
            }

            @Override
            public void onSetUrlToSuggestion(OmniboxSuggestion suggestion) {
                if (mIgnoreOmniboxItemSelection) return;
                setUrlBarText(suggestion.getFillIntoEdit(), null);
                mUrlBar.setSelection(mUrlBar.getText().length());
                mIgnoreOmniboxItemSelection = true;
            }

            @Override
            public void onDeleteSuggestion(int position) {
                if (mAutocomplete != null) mAutocomplete.deleteSuggestion(position);
            }

            @Override
            public void onGestureDown() {
                stopAutocomplete(false);
            }

            @Override
            public void onShowModal() {
                mSuggestionModalShown = true;
            }

            @Override
            public void onHideModal() {
                mSuggestionModalShown = false;
            }

            @Override
            public void onTextWidthsUpdated(float requiredWidth, float matchContentsWidth) {
                mSuggestionList.updateMaxTextWidths(requiredWidth, matchContentsWidth);
            }

            @Override
            public float getMaxRequiredWidth() {
                return mSuggestionList.getMaxRequiredWidth();
            }

            @Override
            public float getMaxMatchContentsWidth() {
                return mSuggestionList.getMaxMatchContentsWidth();
            }
        });
    }

    /**
     * @return The view that the suggestion popup should be anchored below.
     */
    protected View getSuggestionPopupAnchorView() {
        return this;
    }

    /**
     * @return The background for the omnibox suggestions popup.
     */
    protected Drawable getSuggestionPopupBackground() {
        int color = mToolbarDataProvider.isIncognito() ? OMNIBOX_INCOGNITO_RESULTS_BG_COLOR
                : OMNIBOX_RESULTS_BG_COLOR;
        if (!isHardwareAccelerated()) {
            // When HW acceleration is disabled, changing mSuggestionList' items somehow erases
            // mOmniboxResultsContainer' background from the area not covered by mSuggestionList.
            // To make sure mOmniboxResultsContainer is always redrawn, we make list background
            // color slightly transparent. This makes mSuggestionList.isOpaque() to return false,
            // and forces redraw of the parent view (mOmniboxResultsContainer).
            if (Color.alpha(color) == 255) {
                color = Color.argb(254, Color.red(color), Color.green(color), Color.blue(color));
            }
        }
        return new ColorDrawable(color);
    }

    /**
     * Handles showing/hiding the suggestions list.
     * @param visible Whether the suggestions list should be visible.
     */
    protected void setSuggestionsListVisibility(final boolean visible) {
        mSuggestionsShown = visible;
        if (mSuggestionList != null) {
            final boolean isShowing = mSuggestionList.isShown();
            if (visible && !isShowing) {
                mSuggestionList.show();
            } else if (!visible && isShowing) {
                mSuggestionList.setVisibility(GONE);
            }
        }
        updateOmniboxResultsContainer();
    }

    /**
     * Updates the URL we will navigate to from suggestion, if needed. This will update the search
     * URL to be of the corpus type if query in the omnibox is displayed and update aqs= parameter
     * on regular web search URLs.
     *
     * @param suggestion The chosen omnibox suggestion.
     * @param selectedIndex The index of the chosen omnibox suggestion.
     * @param skipCheck Whether to skip an out of bounds check.
     * @return The url to navigate to.
     */
    private String updateSuggestionUrlIfNeeded(OmniboxSuggestion suggestion, int selectedIndex,
            boolean skipCheck) {
        // Only called once we have suggestions, and don't have a listener though which we can
        // receive suggestions until the native side is ready, so this is safe
        assert mNativeInitialized
                : "updateSuggestionUrlIfNeeded called before native initialization";

        String updatedUrl = null;
        if (suggestion.getType() != OmniboxSuggestionType.VOICE_SUGGEST) {
            int verifiedIndex = -1;
            if (!skipCheck) {
                if (mSuggestionItems.size() > selectedIndex
                        && mSuggestionItems.get(selectedIndex).getSuggestion() == suggestion) {
                    verifiedIndex = selectedIndex;
                } else {
                    // Underlying omnibox results may have changed since the selection was made,
                    // find the suggestion item, if possible.
                    for (int i = 0; i < mSuggestionItems.size(); i++) {
                        if (suggestion.equals(mSuggestionItems.get(i).getSuggestion())) {
                            verifiedIndex = i;
                            break;
                        }
                    }
                }
            }

            // If we do not have the suggestion as part of our results, skip the URL update.
            if (verifiedIndex == -1) return suggestion.getUrl();

            // TODO(mariakhomenko): Ideally we want to update match destination URL with new aqs
            // for query in the omnibox and voice suggestions, but it's currently difficult to do.
            long elapsedTimeSinceInputChange = mNewOmniboxEditSessionTimestamp > 0
                    ? (SystemClock.elapsedRealtime() - mNewOmniboxEditSessionTimestamp) : -1;
            updatedUrl = mAutocomplete.updateMatchDestinationUrlWithQueryFormulationTime(
                    verifiedIndex, elapsedTimeSinceInputChange);
        }

        return updatedUrl == null ? suggestion.getUrl() : updatedUrl;
    }

    private void clearSuggestions(boolean notifyChange) {
        mSuggestionItems.clear();
        // Make sure to notify the adapter. If the ListView becomes out of sync
        // with its adapter and it has not been notified, it will throw an
        // exception when some UI events are propagated.
        if (notifyChange) mSuggestionListAdapter.notifyDataSetChanged();
    }

    /**
     * Hides the omnibox suggestion popup.
     *
     * <p>
     * Signals the autocomplete controller to stop generating omnibox suggestions.
     *
     * @see AutocompleteController#stop(boolean)
     */
    @Override
    public void hideSuggestions() {
        if (mAutocomplete == null) return;

        if (mShowSuggestions != null) removeCallbacks(mShowSuggestions);

        recordSuggestionsDismissed();

        stopAutocomplete(true);

        setSuggestionsListVisibility(false);
        clearSuggestions(true);
        updateNavigationButton();

        mSuggestionSelectionInProgress = false;
    }

    /**
     * Records a UMA action indicating that the user dismissed the suggestion list (e.g. pressed
     * the back button or the 'x' button in the Omnibox).  If there was an answer shown its type
     * is recorded.
     *
     * The action is not recorded if mSelectionInProgress is true.   This allows us to avoid
     * recording the action in the case where the user is selecting a suggestion which is not
     * considered a dismissal.
     */
    private void recordSuggestionsDismissed() {
        if (mSuggestionSelectionInProgress || mSuggestionItems.size() == 0) return;

        int answerTypeShown = 0;
        for (int i = 0; i < mSuggestionItems.size(); i++) {
            OmniboxSuggestion suggestion = mSuggestionItems.get(i).getSuggestion();
            if (suggestion.hasAnswer()) {
                try {
                    answerTypeShown = Integer.parseInt(suggestion.getAnswerType());
                } catch (NumberFormatException e) {
                    Log.e(getClass().getSimpleName(),
                            "Answer type in dismissed suggestions is not an int: "
                            + suggestion.getAnswerType());
                }
                break;
            }
        }
        RecordHistogram.recordSparseSlowlyHistogram(
                "Omnibox.SuggestionsDismissed.AnswerType", answerTypeShown);
    }

    /**
     * Signals the autocomplete controller to stop generating omnibox suggestions and cancels the
     * queued task to start the autocomplete controller, if any.
     *
     * @param clear Whether to clear the most recent autocomplete results.
     */
    private void stopAutocomplete(boolean clear) {
        if (mAutocomplete != null) mAutocomplete.stop(clear);
        cancelPendingAutocompleteStart();
    }

    /**
     * Cancels the queued task to start the autocomplete controller, if any.
     */
    @VisibleForTesting
    void cancelPendingAutocompleteStart() {
        if (mRequestSuggestions != null) {
            // There is a request for suggestions either waiting for the native side
            // to start, or on the message queue. Remove it from wherever it is.
            if (!mDeferredNativeRunnables.remove(mRequestSuggestions)) {
                removeCallbacks(mRequestSuggestions);
            }
            mRequestSuggestions = null;
        }
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // Don't restore the state of the location bar, it can lead to all kind of bad states with
        // the popup.
        // When we restore tabs, we focus the selected tab so the URL of the page shows.
    }

    /**
     * Performs a search query on the current {@link Tab}.  This calls
     * {@link TemplateUrlService#getUrlForSearchQuery(String)} to get a url based on {@code query}
     * and loads that url in the current {@link Tab}.
     * @param query The {@link String} that represents the text query that should be searched for.
     */
    @VisibleForTesting
    public void performSearchQueryForTest(String query) {
        if (TextUtils.isEmpty(query)) return;

        String queryUrl = TemplateUrlService.getInstance().getUrlForSearchQuery(query);

        if (!TextUtils.isEmpty(queryUrl)) {
            loadUrl(queryUrl, PageTransition.GENERATED);
        } else {
            setSearchQuery(query);
        }
    }

    /**
     * Sets the query string in the omnibox (ensuring the URL bar has focus and triggering
     * autocomplete for the specified query) as if the user typed it.
     *
     * @param query The query to be set in the omnibox.
     */
    public void setSearchQuery(final String query) {
        if (TextUtils.isEmpty(query)) return;

        if (!mNativeInitialized) {
            mDeferredNativeRunnables.add(new Runnable() {
                @Override
                public void run() {
                    setSearchQuery(query);
                }
            });
            return;
        }

        setUrlBarText(query, null);
        mUrlBar.setSelection(0, mUrlBar.getText().length());
        setUrlBarFocus(true);
        stopAutocomplete(false);
        if (getCurrentTab() != null) {
            mAutocomplete.start(
                    getCurrentTab().getProfile(), getCurrentTab().getUrl(), query, false);
        }
        post(new Runnable() {
            @Override
            public void run() {
                UiUtils.showKeyboard(mUrlBar);
            }
        });
    }

    /**
     * Whether {@code v} is a view (location icon, verbose status, ...) which can be clicked to
     * show the origin info dialog.
     */
    private boolean shouldShowPageInfoForView(View v) {
        return v == mSecurityButton || v == mNavigationButton || v == mVerboseStatusTextView;
    }

    @Override
    public void onClick(View v) {
        if (v == mDeleteButton) {
            if (!TextUtils.isEmpty(mUrlBar.getQueryText())) {
                setUrlBarText("", null);
                hideSuggestions();
                updateButtonVisibility();
            }

            startZeroSuggest();
            return;
        } else if (!mUrlHasFocus && shouldShowPageInfoForView(v)) {
            Tab currentTab = getCurrentTab();
            if (currentTab != null && currentTab.getWebContents() != null) {
                Activity activity = currentTab.getWindowAndroid().getActivity().get();
                if (activity != null) {
                    WebsiteSettingsPopup.show(
                            activity, currentTab, null, WebsiteSettingsPopup.OPENED_FROM_TOOLBAR);
                }
            }
        } else if (v == mMicButton) {
            RecordUserAction.record("MobileOmniboxVoiceSearch");
            startVoiceRecognition();
        } else if (v == mOmniboxResultsContainer) {
            // This will only be triggered when no suggestion items are selected in the container.
            setUrlBarFocus(false);
            updateOmniboxResultsContainerBackground(false);
        }
    }

    @Override
    public void onSuggestionsReceived(List<OmniboxSuggestion> newSuggestions,
            String inlineAutocompleteText) {
        // This is a callback from a listener that is set up by onNativeLibraryReady,
        // so can only be called once the native side is set up.
        assert mNativeInitialized : "Suggestions received before native side intialialized";

        if (getCurrentTab() == null) {
            // If the current tab is not available, drop the suggestions and hide the autocomplete.
            hideSuggestions();
            return;
        }

        String userText = mUrlBar.getTextWithoutAutocomplete();
        mUrlTextAfterSuggestionsReceived = userText + inlineAutocompleteText;

        boolean itemsChanged = false;
        boolean itemCountChanged = false;
        // If the length of the incoming suggestions matches that of those currently being shown,
        // replace them inline to allow transient entries to retain their proper highlighting.
        if (mSuggestionItems.size() == newSuggestions.size()) {
            for (int index = 0; index < newSuggestions.size(); index++) {
                OmniboxResultItem suggestionItem = mSuggestionItems.get(index);
                OmniboxSuggestion suggestion = suggestionItem.getSuggestion();
                OmniboxSuggestion newSuggestion = newSuggestions.get(index);
                // Determine whether the suggestions have changed. If not, save some time by not
                // redrawing the suggestions UI.
                if (suggestion.equals(newSuggestion)
                        && suggestion.getType() != OmniboxSuggestionType.SEARCH_SUGGEST_TAIL) {
                    if (suggestionItem.getMatchedQuery().equals(userText)) {
                        continue;
                    } else if (!suggestion.getDisplayText().startsWith(userText)
                            && !suggestion.getUrl().contains(userText)) {
                        continue;
                    }
                }
                mSuggestionItems.set(index, new OmniboxResultItem(newSuggestion, userText));
                itemsChanged = true;
            }
        } else {
            itemsChanged = true;
            itemCountChanged = true;
            clearSuggestions(false);
            for (int i = 0; i < newSuggestions.size(); i++) {
                mSuggestionItems.add(new OmniboxResultItem(newSuggestions.get(i), userText));
            }
        }

        if (mSuggestionItems.isEmpty()) {
            if (mSuggestionsShown) hideSuggestions();
            return;
        }

        if (mUrlBar.shouldAutocomplete()) {
            mUrlBar.setAutocompleteText(userText, inlineAutocompleteText);
        }

        // Show the suggestion list.
        initSuggestionList();  // It may not have been initialized yet.
        mSuggestionList.resetMaxTextWidths();

        // Handle the case where suggestions (in particular zero suggest) are received without the
        // URL focusing happening.
        if (mUrlFocusedWithoutAnimations && mUrlHasFocus) {
            handleUrlFocusAnimation(mUrlHasFocus);
        }

        if (itemsChanged) mSuggestionListAdapter.notifySuggestionsChanged();

        if (mUrlBar.hasFocus()) {
            final boolean updateLayoutParams = itemCountChanged;
            mShowSuggestions = new Runnable() {
                @Override
                public void run() {
                    setSuggestionsListVisibility(true);
                    if (updateLayoutParams) {
                        mSuggestionList.updateLayoutParams();
                    }
                    mShowSuggestions = null;
                }
            };
            if (!isUrlFocusChangeInProgress()) {
                mShowSuggestions.run();
            } else {
                postDelayed(mShowSuggestions, ToolbarPhone.URL_FOCUS_CHANGE_ANIMATION_DURATION_MS);
            }
        }

        // Update the navigation button to show the default suggestion's icon.
        updateNavigationButton();

        if (!CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_INSTANT)
                && PrivacyPreferencesManager.getInstance().shouldPrerender()) {
            mOmniboxPrerender.prerenderMaybe(
                    userText,
                    getOriginalUrl(),
                    mAutocomplete.getCurrentNativeAutocompleteResult(),
                    getCurrentTab().getProfile(),
                    getCurrentTab());
        }
    }

    private void backKeyPressed() {
        hideSuggestions();
        UiUtils.hideKeyboard(mUrlBar);
        // Revert the URL to match the current page.
        setUrlToPageUrl();
        // Focus the page.
        Tab currentTab = getCurrentTab();
        if (currentTab != null) currentTab.requestFocus();
    }

    /**
     * @return Returns the original url of the page.
     */
    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    /**
     * Given the URL display text, this will remove any path portion contained within.
     * @param displayText The text to strip the path from.
     * @return A pair where the first item is the text without any path content (if the path was
     *         successfully found), and the second item is the path content (or null if no path
     *         was found or parsing the path failed).
     * @see ToolbarDataProvider#getText()
     */
    // TODO(tedchoc): Move this logic into the original display text calculation.
    @VisibleForTesting
    public static Pair<String, String> splitPathFromUrlDisplayText(String displayText) {
        int pathSearchOffset = 0;
        Uri uri = Uri.parse(displayText);
        String scheme = uri.getScheme();
        if (!TextUtils.isEmpty(scheme)) {
            if (UNSUPPORTED_SCHEMES_TO_SPLIT.contains(scheme)) {
                return Pair.create(displayText, null);
            } else if (ACCEPTED_SCHEMES.contains(scheme)) {
                for (pathSearchOffset = scheme.length();
                        pathSearchOffset < displayText.length();
                        pathSearchOffset++) {
                    char c = displayText.charAt(pathSearchOffset);
                    if (c != ':' && c != '/') break;
                }
            }
        }
        int pathOffset = -1;
        if (pathSearchOffset < displayText.length()) {
            pathOffset = displayText.indexOf('/', pathSearchOffset);
        }
        if (pathOffset != -1) {
            String prePathText = displayText.substring(0, pathOffset);
            // If the '/' is the last character and the beginning of the path, then just drop
            // the path entirely.
            String pathText = pathOffset == displayText.length() - 1
                    ? null : displayText.substring(pathOffset);
            return Pair.create(prePathText, pathText);
        }
        return Pair.create(displayText, null);
    }

    /**
     * Sets the displayed URL to be the URL of the page currently showing.
     *
     * <p>The URL is converted to the most user friendly format (removing HTTP:// for example).
     *
     * <p>If the current tab is null, the URL text will be cleared.
     */
    @Override
    public void setUrlToPageUrl() {
        String url = getCurrentTabUrl();

        // If the URL is currently focused, do not replace the text they have entered with the URL.
        // Once they stop editing the URL, the current tab's URL will automatically be filled in.
        if (mUrlBar.hasFocus()) {
            if (mUrlFocusedWithoutAnimations && !NewTabPage.isNTPUrl(url)) {
                // If we did not run the focus animations, then the user has not typed any text.
                // So, clear the focus and accept whatever URL the page is currently attempting to
                // display.
                setUrlBarFocus(false);
            } else {
                return;
            }
        }

        if (getCurrentTab() == null) {
            setUrlBarText("", null);
            return;
        }

        // Profile may be null if switching to a tab that has not yet been initialized.
        Profile profile = getCurrentTab().getProfile();
        if (profile != null) mOmniboxPrerender.clear(profile);

        mOriginalUrl = url;

        if (NativePageFactory.isNativePageUrl(url, getCurrentTab().isIncognito())
                || NewTabPage.isNTPUrl(url)) {
            // Don't show anything for Chrome URLs.
            setUrlBarText(null, "");
            return;
        }

        if (setUrlBarText(url, mToolbarDataProvider.getText())) {
            mUrlBar.deEmphasizeUrl();
            emphasizeUrl();
        }
        updateCustomSelectionActionModeCallback();
    }

    /** Gets the URL of the web page in the tab. */
    private String getCurrentTabUrl() {
        Tab currentTab = getCurrentTab();
        if (currentTab == null) return "";
        return currentTab.getUrl().trim();
    }

    /**
     * Changes the text on the url bar
     * @param originalText The original text (URL or search terms) for copy/cut.
     * @param displayText The text (URL or search terms) for user display.
     * @return Whether the URL was changed as a result of this call.
     */
    private boolean setUrlBarText(String originalText, String displayText) {
        mUrlBar.setIgnoreTextChangesForAutocomplete(true);
        boolean urlChanged = mUrlBar.setUrl(originalText, displayText);
        mUrlBar.setIgnoreTextChangesForAutocomplete(false);
        return urlChanged;
    }

    private void loadUrlFromOmniboxMatch(String url, int transition, int matchPosition, int type) {
        // loadUrl modifies AutocompleteController's state clearing the native
        // AutocompleteResults needed by onSuggestionsSelected. Therefore,
        // loadUrl should should be invoked last.
        Tab currentTab = getCurrentTab();
        String currentPageUrl = getCurrentTabUrl();
        WebContents webContents = currentTab != null ? currentTab.getWebContents() : null;
        long elapsedTimeSinceModified = mNewOmniboxEditSessionTimestamp > 0
                ? (SystemClock.elapsedRealtime() - mNewOmniboxEditSessionTimestamp) : -1;
        mAutocomplete.onSuggestionSelected(matchPosition, type, currentPageUrl,
                mUrlFocusedFromFakebox, elapsedTimeSinceModified, mUrlBar.getAutocompleteLength(),
                webContents);
        loadUrl(url, transition);
    }

    private void loadUrl(String url, int transition) {
        Tab currentTab = getCurrentTab();

        // The code of the rest of this class ensures that this can't be called until the native
        // side is initialized
        assert mNativeInitialized : "Loading URL before native side initialized";

        if (currentTab != null
                && (currentTab.isNativePage() || NewTabPage.isNTPUrl(currentTab.getUrl()))) {
            NewTabPageUma.recordOmniboxNavigation(url, transition);
            // Passing in an empty string should not do anything unless the user is at the NTP.
            // Since the NTP has no url, pressing enter while clicking on the URL bar should refresh
            // the page as it does when you click and press enter on any other site.
            if (url.isEmpty()) url = currentTab.getUrl();
        }

        // Loads the |url| in the current ContentView and gives focus to the ContentView.
        if (currentTab != null && !url.isEmpty()) {
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            loadUrlParams.setVerbatimHeaders(
                    GeolocationHeader.getGeoHeader(getContext(), url, currentTab.isIncognito()));
            loadUrlParams.setTransitionType(transition | PageTransition.FROM_ADDRESS_BAR);
            currentTab.loadUrl(loadUrlParams);

            setUrlToPageUrl();
            RecordUserAction.record("MobileOmniboxUse");
            RecordUserAction.record("MobileTabClobbered");
        } else {
            setUrlToPageUrl();
        }

        if (currentTab != null) currentTab.requestFocus();

        // Prevent any upcoming omnibox suggestions from showing. We have to do this after we load
        // the URL as this will hide the suggestions and trigger a cancel of the prerendered page.
        stopAutocomplete(true);
    }

    /**
     * Update the location bar visuals based on a loading state change.
     * @param updateUrl Whether to update the URL as a result of the this call.
     */
    @Override
    public void updateLoadingState(boolean updateUrl) {
        if (updateUrl) setUrlToPageUrl();
        updateNavigationButton();
        updateSecurityIcon(getSecurityLevel());
    }

    /**
     * @return The Tab currently showing.
     */
    @Override
    public Tab getCurrentTab() {
        if (mToolbarDataProvider == null) return null;
        return mToolbarDataProvider.getTab();
    }

    private void initOmniboxResultsContainer() {
        if (mOmniboxResultsContainer != null) return;

        ViewStub overlayStub =
                (ViewStub) getRootView().findViewById(R.id.omnibox_results_container_stub);
        mOmniboxResultsContainer = (ViewGroup) overlayStub.inflate();
        mOmniboxResultsContainer.setBackgroundColor(CONTENT_OVERLAY_COLOR);
        mOmniboxResultsContainer.setOnClickListener(this);
    }

    private void updateOmniboxResultsContainer() {
        if (mSuggestionsShown || mUrlHasFocus) {
            initOmniboxResultsContainer();
            updateOmniboxResultsContainerVisibility(true);
        } else if (mOmniboxResultsContainer != null) {
            updateOmniboxResultsContainerBackground(false);
        }
    }

    private void updateOmniboxResultsContainerVisibility(boolean visible) {
        boolean currentlyVisible = mOmniboxResultsContainer.getVisibility() == VISIBLE;
        if (currentlyVisible == visible) return;

        ChromeActivity activity = (ChromeActivity) mWindowAndroid.getActivity().get();

        if (visible) {
            mOmniboxResultsContainer.setVisibility(VISIBLE);
            if (activity != null) activity.addViewObscuringAllTabs(mOmniboxResultsContainer);
        } else {
            mOmniboxResultsContainer.setVisibility(INVISIBLE);
            if (activity != null) activity.removeViewObscuringAllTabs(mOmniboxResultsContainer);
        }
    }

    /**
     * Set the background of the omnibox results container.
     * @param visible Whether the background should be made visible.
     */
    private void updateOmniboxResultsContainerBackground(boolean visible) {
        if (getToolbarDataProvider() == null) return;

        NewTabPage ntp = getToolbarDataProvider().getNewTabPageForCurrentTab();
        boolean locationBarShownInNTP = ntp != null && ntp.isLocationBarShownInNTP();
        if (visible) {
            if (locationBarShownInNTP) {
                mOmniboxResultsContainer.getBackground().setAlpha(0);
            } else {
                fadeInOmniboxResultsContainerBackground();
            }
        } else {
            if (locationBarShownInNTP) {
                updateOmniboxResultsContainerVisibility(false);
            } else {
                fadeOutOmniboxResultsContainerBackground();
            }
        }
    }

    /**
     * Trigger a fade in of the omnibox results background.
     */
    protected void fadeInOmniboxResultsContainerBackground() {
        if (mFadeInOmniboxBackgroundAnimator == null) {
            mFadeInOmniboxBackgroundAnimator = ObjectAnimator.ofInt(
                    getRootView().findViewById(R.id.omnibox_results_container).getBackground(),
                    AnimatorProperties.DRAWABLE_ALPHA_PROPERTY, 0, 255);
            mFadeInOmniboxBackgroundAnimator.setDuration(OMNIBOX_CONTAINER_BACKGROUND_FADE_MS);
            mFadeInOmniboxBackgroundAnimator.setInterpolator(
                    BakedBezierInterpolator.FADE_IN_CURVE);
        }
        runOmniboxResultsFadeAnimation(mFadeInOmniboxBackgroundAnimator);
    }

    private void fadeOutOmniboxResultsContainerBackground() {
        if (mFadeOutOmniboxBackgroundAnimator == null) {
            mFadeOutOmniboxBackgroundAnimator = ObjectAnimator.ofInt(
                    getRootView().findViewById(R.id.omnibox_results_container).getBackground(),
                    AnimatorProperties.DRAWABLE_ALPHA_PROPERTY, 255, 0);
            mFadeOutOmniboxBackgroundAnimator.setDuration(OMNIBOX_CONTAINER_BACKGROUND_FADE_MS);
            mFadeOutOmniboxBackgroundAnimator.setInterpolator(
                    BakedBezierInterpolator.FADE_OUT_CURVE);
            mFadeOutOmniboxBackgroundAnimator.addListener(new CancelAwareAnimatorListener() {
                @Override
                public void onEnd(Animator animator) {
                    updateOmniboxResultsContainerVisibility(false);
                }
            });
        }
        runOmniboxResultsFadeAnimation(mFadeOutOmniboxBackgroundAnimator);
    }

    private void runOmniboxResultsFadeAnimation(Animator fadeAnimation) {
        if (mOmniboxBackgroundAnimator == fadeAnimation
                && mOmniboxBackgroundAnimator.isRunning()) {
            return;
        } else if (mOmniboxBackgroundAnimator != null) {
            mOmniboxBackgroundAnimator.cancel();
        }
        mOmniboxBackgroundAnimator = fadeAnimation;
        mOmniboxBackgroundAnimator.start();
    }

    @Override
    public boolean isVoiceSearchEnabled() {
        if (mToolbarDataProvider == null) return false;
        if (mToolbarDataProvider.isIncognito()) return false;
        if (mWindowAndroid == null) return false;

        if (!mWindowAndroid.hasPermission(Manifest.permission.RECORD_AUDIO)
                && !mWindowAndroid.canRequestPermission(Manifest.permission.RECORD_AUDIO)) {
            return false;
        }

        return FeatureUtilities.isRecognitionIntentPresent(getContext(), true);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) updateMicButtonState();
    }

    /**
     * Call to update the visibility of the buttons inside the location bar.
     */
    protected void updateButtonVisibility() {
    }

    /**
     * Call to notify the location bar that the state of the voice search microphone button may
     * need to be updated.
     */
    @Override
    public void updateMicButtonState() {
        mVoiceSearchEnabled = isVoiceSearchEnabled();
        updateButtonVisibility();
    }

    /**
     * Updates the display of the mic button.
     * @param urlFocusChangePercent The completeion percentage of the URL focus change animation.
     */
    protected void updateMicButtonVisiblity(float urlFocusChangePercent) {
        boolean visible = !shouldShowDeleteButton();
        boolean showMicButton = mVoiceSearchEnabled && visible
                && (mUrlBar.hasFocus() || mUrlFocusChangeInProgress
                        || urlFocusChangePercent > 0f);
        mMicButton.setVisibility(showMicButton ? VISIBLE : GONE);
    }

    /**
     * Call to force the UI to update the state of various buttons based on whether or not the
     * current tab is incognito.
     */
    @Override
    public void updateVisualsForState() {
        if (updateUseDarkColors() || mIsEmphasizingHttpsScheme != shouldEmphasizeHttpsScheme()) {
            updateSecurityIcon(getSecurityLevel());
        }
        ColorStateList colorStateList = ApiCompatibilityUtils.getColorStateList(getResources(),
                mUseDarkColors ? R.color.dark_mode_tint : R.color.light_mode_tint);
        mMicButton.setTint(colorStateList);
        mDeleteButton.setTint(colorStateList);

        setNavigationButtonType(mNavigationButtonType);
        mUrlBar.setUseDarkTextColors(mUseDarkColors);

        if (mSuggestionList != null) {
            mSuggestionList.setBackground(getSuggestionPopupBackground());
        }
        mSuggestionListAdapter.setUseDarkColors(mUseDarkColors);
    }

    /**
     * Checks the current specs and updates {@link LocationBar#mUseDarkColors} if necessary.
     * @return Whether {@link LocationBar#mUseDarkColors} has been updated.
     */
    private boolean updateUseDarkColors() {
        Tab tab = getCurrentTab();
        boolean brandColorNeedsLightText = false;
        if (getToolbarDataProvider().isUsingBrandColor() && !mUrlHasFocus) {
            int currentPrimaryColor = getToolbarDataProvider().getPrimaryColor();
            brandColorNeedsLightText =
                    ColorUtils.shouldUseLightForegroundOnBackground(currentPrimaryColor);
        }

        boolean useDarkColors = tab == null || !(tab.isIncognito() || brandColorNeedsLightText);
        boolean hasChanged = useDarkColors != mUseDarkColors;
        mUseDarkColors = useDarkColors;

        return hasChanged;
    }

    /**
     * Triggers a voice recognition intent to allow the user to specify a search query.
     */
    @Override
    public void startVoiceRecognition() {
        Activity activity = mWindowAndroid.getActivity().get();
        if (activity == null) return;

        if (!mWindowAndroid.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            if (mWindowAndroid.canRequestPermission(Manifest.permission.RECORD_AUDIO)) {
                WindowAndroid.PermissionCallback callback =
                        new WindowAndroid.PermissionCallback() {
                    @Override
                    public void onRequestPermissionsResult(
                            String[] permissions, int[] grantResults) {
                        if (grantResults.length != 1) return;

                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            startVoiceRecognition();
                        } else {
                            updateMicButtonState();
                        }
                    }
                };
                mWindowAndroid.requestPermissions(
                        new String[] {Manifest.permission.RECORD_AUDIO}, callback);
            } else {
                updateMicButtonState();
            }
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                activity.getComponentName().flattenToString());
        intent.putExtra(RecognizerIntent.EXTRA_WEB_SEARCH_ONLY, true);

        if (mWindowAndroid.showCancelableIntent(intent, this, R.string.voice_search_error) < 0) {
            // Requery whether or not the recognition intent can be handled.
            FeatureUtilities.isRecognitionIntentPresent(activity, false);
            updateMicButtonState();
        }
    }

    // WindowAndroid.IntentCallback implementation:
    @Override
    public void onIntentCompleted(WindowAndroid window, int resultCode,
            ContentResolver contentResolver, Intent data) {
        if (resultCode != Activity.RESULT_OK) return;
        if (data.getExtras() == null) return;

        VoiceResult topResult = mAutocomplete.onVoiceResults(data.getExtras());
        if (topResult == null) return;

        String topResultQuery = topResult.getMatch();
        if (TextUtils.isEmpty(topResultQuery)) return;

        if (topResult.getConfidence() < VOICE_SEARCH_CONFIDENCE_NAVIGATE_THRESHOLD) {
            setSearchQuery(topResultQuery);
            return;
        }

        String url = AutocompleteController.nativeQualifyPartialURLQuery(topResultQuery);
        if (url == null) {
            url = TemplateUrlService.getInstance().getUrlForVoiceSearchQuery(
                    topResultQuery);
        }
        loadUrl(url, PageTransition.TYPED);
    }

    @Override
    public void onTabLoadingNTP(NewTabPage ntp) {
        ntp.setFakeboxDelegate(this);
    }

    @Override
    public View getContainerView() {
        return this;
    }

    @Override
    public void setTitleToPageTitle() { }

    @Override
    public void setShowTitle(boolean showTitle) { }
}
