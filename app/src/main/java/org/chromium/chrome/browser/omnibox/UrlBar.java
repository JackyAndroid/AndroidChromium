// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.omnibox.LocationBarLayout.OmniboxLivenessListener;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.widget.VerticallyFixedEditText;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.UiUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * The URL text entry view for the Omnibox.
 */
public class UrlBar extends VerticallyFixedEditText {
    private static final String TAG = "UrlBar";

    // TextView becomes very slow on long strings, so we limit maximum length
    // of what is displayed to the user, see limitDisplayableLength().
    private static final int MAX_DISPLAYABLE_LENGTH = 4000;
    private static final int MAX_DISPLAYABLE_LENGTH_LOW_END = 1000;

    /** The contents of the URL that precede the path/query after being formatted. */
    private String mFormattedUrlLocation;

    /** The contents of the URL that precede the path/query before formatting. */
    private String mOriginalUrlLocation;

    /** Overrides the text announced during accessibility events. */
    private String mAccessibilityTextOverride;

    private TextWatcher mLocationBarTextWatcher;

    private boolean mShowKeyboardOnWindowFocus;

    private boolean mFirstDrawComplete;

    /**
     * The text direction of the URL or query: LAYOUT_DIRECTION_LOCALE, LAYOUT_DIRECTION_LTR, or
     * LAYOUT_DIRECTION_RTL.
     * */
    private int mUrlDirection;

    private UrlBarDelegate mUrlBarDelegate;

    private UrlDirectionListener mUrlDirectionListener;

    private final AutocompleteSpan mAutocompleteSpan;

    /**
     * The gesture detector is used to detect long presses. Long presses require special treatment
     * because the URL bar has custom touch event handling. See: {@link #onTouchEvent}.
     */
    private final GestureDetector mGestureDetector;
    private boolean mFocused;
    private boolean mAllowFocus = true;

    private final ColorStateList mDarkHintColor;
    private final int mDarkDefaultTextColor;
    private final int mDarkHighlightColor;

    private final int mLightHintColor;
    private final int mLightDefaultTextColor;
    private final int mLightHighlightColor;

    private Boolean mUseDarkColors;

    private AccessibilityManager mAccessibilityManager;
    private boolean mDisableTextAccessibilityEvents;

    /**
     * Whether default TextView scrolling should be disabled because autocomplete has been added.
     * This allows the user entered text to be shown instead of the end of the autocomplete.
     */
    private boolean mDisableTextScrollingFromAutocomplete;

    private OmniboxLivenessListener mOmniboxLivenessListener;

    private long mFirstFocusTimeMs;

    private boolean mInBatchEditMode;
    private boolean mSelectionChangedInBatchMode;

    private boolean mIsPastedText;
    // Used as a hint to indicate the text may contain an ellipsize span.  This will be true if an
    // ellispize span was applied the last time the text changed.  A true value here does not
    // guarantee that the text does contain the span currently as newly set text may have cleared
    // this (and it the value will only be recalculated after the text has been changed).
    private boolean mDidEllipsizeTextHint;

    /**
     * Implement this to get updates when the direction of the text in the URL bar changes.
     * E.g. If the user is typing a URL, then erases it and starts typing a query in Arabic,
     * the direction will change from left-to-right to right-to-left.
     */
    interface UrlDirectionListener {
        /**
         * Called whenever the layout direction of the UrlBar changes.
         * @param layoutDirection the new direction: android.view.View.LAYOUT_DIRECTION_LTR or
         *                        android.view.View.LAYOUT_DIRECTION_RTL
         */
        public void onUrlDirectionChanged(int layoutDirection);
    }

    /**
     * Delegate used to communicate with the content side and the parent layout.
     */
    public interface UrlBarDelegate {
        /**
         * @return The current active {@link Tab}.
         */
        Tab getCurrentTab();

        /**
         * Notify the linked {@link TextWatcher} to ignore any changes made in the UrlBar text.
         * @param ignore Whether the changes should be ignored.
         */
        void setIgnoreURLBarModification(boolean ignore);

        /**
         * Called at the beginning of the focus change event before the underlying TextView
         * behavior is triggered.
         * @param gainFocus Whether the URL is gaining focus or not.
         */
        void onUrlPreFocusChanged(boolean gainFocus);

        /**
         * Called to notify that back key has been pressed while the focus in on the url bar.
         */
        void backKeyPressed();

        /**
         * @return Whether the light security theme should be used.
         */
        boolean shouldEmphasizeHttpsScheme();
    }

    public UrlBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources resources = getResources();

        mDarkDefaultTextColor =
                ApiCompatibilityUtils.getColor(resources, R.color.url_emphasis_default_text);
        mDarkHintColor = getHintTextColors();
        mDarkHighlightColor = getHighlightColor();

        mLightDefaultTextColor =
                ApiCompatibilityUtils.getColor(resources, R.color.url_emphasis_light_default_text);
        mLightHintColor =
                ApiCompatibilityUtils.getColor(resources, R.color.locationbar_light_hint_text);
        mLightHighlightColor = ApiCompatibilityUtils.getColor(resources,
                R.color.locationbar_light_selection_color);

        setUseDarkTextColors(true);

        mUrlDirection = LAYOUT_DIRECTION_LOCALE;
        mAutocompleteSpan = new AutocompleteSpan();

        // The URL Bar is derived from an text edit class, and as such is focusable by
        // default. This means that if it is created before the first draw of the UI it
        // will (as the only focusable element of the UI) get focus on the first draw.
        // We react to this by greying out the tab area and bringing up the keyboard,
        // which we don't want to do at startup. Prevent this by disabling focus until
        // the first draw.
        setFocusable(false);
        setFocusableInTouchMode(false);

        mGestureDetector = new GestureDetector(
                getContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        performLongClick();
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        requestFocus();
                        return true;
                    }
                });
        mGestureDetector.setOnDoubleTapListener(null);

        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    /**
     * Specifies whether the URL bar should use dark text colors or light colors.
     * @param useDarkColors Whether the text colors should be dark (i.e. appropriate for use
     *                      on a light background).
     */
    public void setUseDarkTextColors(boolean useDarkColors) {
        if (mUseDarkColors != null && mUseDarkColors.booleanValue() == useDarkColors) return;

        mUseDarkColors = useDarkColors;
        if (mUseDarkColors) {
            setTextColor(mDarkDefaultTextColor);
            setHighlightColor(mDarkHighlightColor);
        } else {
            setTextColor(mLightDefaultTextColor);
            setHighlightColor(mLightHighlightColor);
        }

        // Note: Setting the hint text color only takes effect if there is not text in the URL bar.
        //       To get around this, set the URL to empty before setting the hint color and revert
        //       back to the previous text after.
        boolean hasNonEmptyText = false;
        Editable text = getText();
        if (!TextUtils.isEmpty(text)) {
            setText("");
            hasNonEmptyText = true;
        }
        if (useDarkColors) {
            setHintTextColor(mDarkHintColor);
        } else {
            setHintTextColor(mLightHintColor);
        }
        if (hasNonEmptyText) setText(text);

        if (!hasFocus()) {
            deEmphasizeUrl();
            emphasizeUrl();
        }
    }

    /**
     * @return The search query text (non-null).
     */
    public String getQueryText() {
        return getEditableText() != null ? getEditableText().toString() : "";
    }

    /**
     * @return Whether the current cursor position is at the end of the user typed text (i.e.
     *         at the beginning of the inline autocomplete text if present otherwise the very
     *         end of the current text).
     */
    public boolean isCursorAtEndOfTypedText() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        int expectedSelectionStart = getText().getSpanStart(mAutocompleteSpan);
        int expectedSelectionEnd = getText().length();
        if (expectedSelectionStart < 0) {
            expectedSelectionStart = expectedSelectionEnd;
        }

        return selectionStart == expectedSelectionStart && selectionEnd == expectedSelectionEnd;
    }

    /**
     * @return Whether the URL is currently in batch edit mode triggered by an IME.  No external
     *         text changes should be triggered while this is true.
     */
    // isInBatchEditMode is a package protected method on TextView, so we intentionally chose
    // a different name.
    public boolean isHandlingBatchInput() {
        return mInBatchEditMode;
    }

    /**
     * @return The user text without the autocomplete text.
     */
    public String getTextWithoutAutocomplete() {
        int autoCompleteIndex = getText().getSpanStart(mAutocompleteSpan);
        if (autoCompleteIndex < 0) {
            return getQueryText();
        } else {
            return getQueryText().substring(0, autoCompleteIndex);
        }
    }

    /** @return Whether any autocomplete information is specified on the current text. */
    @VisibleForTesting
    protected boolean hasAutocomplete() {
        return getText().getSpanStart(mAutocompleteSpan) >= 0
                || mAutocompleteSpan.mAutocompleteText != null
                || mAutocompleteSpan.mUserText != null;
    }

    @Override
    public void onBeginBatchEdit() {
        super.onBeginBatchEdit();
        mInBatchEditMode = true;
    }

    @Override
    public void onEndBatchEdit() {
        super.onEndBatchEdit();
        mInBatchEditMode = false;
        limitDisplayableLength();
        if (mSelectionChangedInBatchMode) {
            validateSelection(getSelectionStart(), getSelectionEnd());
            mSelectionChangedInBatchMode = false;
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (!mInBatchEditMode) {
            validateSelection(selStart, selEnd);
        } else {
            mSelectionChangedInBatchMode = true;
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    private void validateSelection(int selStart, int selEnd) {
        int spanStart = getText().getSpanStart(mAutocompleteSpan);
        int spanEnd = getText().getSpanEnd(mAutocompleteSpan);
        if (spanStart >= 0 && (spanStart != selStart || spanEnd != selEnd)) {
            // On selection changes, the autocomplete text has been accepted by the user or needs
            // to be deleted below.
            mAutocompleteSpan.clearSpan();

            // The autocomplete text will be deleted any time the selection occurs entirely before
            // the start of the autocomplete text.  This is required because certain keyboards will
            // insert characters temporarily when starting a key entry gesture (whether it be
            // swyping a word or long pressing to get a special character).  When this temporary
            // character appears, Chrome may decide to append some autocomplete, but the keyboard
            // will then remove this temporary character only while leaving the autocomplete text
            // alone.  See crbug/273763 for more details.
            if (selEnd <= spanStart) getText().delete(spanStart, getText().length());
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        mFocused = focused;
        mUrlBarDelegate.onUrlPreFocusChanged(focused);
        if (!focused) mAutocompleteSpan.clearSpan();
        super.onFocusChanged(focused, direction, previouslyFocusedRect);

        if (focused && mFirstFocusTimeMs == 0) {
            mFirstFocusTimeMs = SystemClock.elapsedRealtime();
            if (mOmniboxLivenessListener != null) mOmniboxLivenessListener.onOmniboxFocused();
        }

        if (focused) StartupMetrics.getInstance().recordFocusedOmnibox();
    }

    /**
     * @return The elapsed realtime timestamp in ms of the first time the url bar was focused,
     *         0 if never.
     */
    public long getFirstFocusTime() {
        return mFirstFocusTimeMs;
    }

    /**
     * Sets whether this {@link UrlBar} should be focusable.
     */
    public void setAllowFocus(boolean allowFocus) {
        mAllowFocus = allowFocus;
        if (mFirstDrawComplete) {
            setFocusable(allowFocus);
            setFocusableInTouchMode(allowFocus);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.GONE && isFocused()) mShowKeyboardOnWindowFocus = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            if (mShowKeyboardOnWindowFocus && isFocused()) {
                // Without the call to post(..), the keyboard was not getting shown when the
                // window regained focus despite this being the final call in the view system
                // flow.
                post(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.showKeyboard(UrlBar.this);
                    }
                });
            }
            mShowKeyboardOnWindowFocus = false;
        }
    }

    @Override
    public View focusSearch(int direction) {
        if (direction == View.FOCUS_BACKWARD
                && mUrlBarDelegate.getCurrentTab().getView() != null) {
            return mUrlBarDelegate.getCurrentTab().getView();
        } else {
            return super.focusSearch(direction);
        }
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                // Tell the framework to start tracking this event.
                getKeyDispatcherState().startTracking(event, this);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                getKeyDispatcherState().handleUpEvent(event);
                if (event.isTracking() && !event.isCanceled()) {
                    mUrlBarDelegate.backKeyPressed();
                    return true;
                }
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mFocused) {
            mGestureDetector.onTouchEvent(event);
            return true;
        }

        Tab currentTab = mUrlBarDelegate.getCurrentTab();
        if (event.getAction() == MotionEvent.ACTION_DOWN && currentTab != null) {
            // Make sure to hide the current ContentView ActionBar.
            ContentViewCore viewCore = currentTab.getContentViewCore();
            if (viewCore != null) viewCore.hideSelectActionMode();
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean bringPointIntoView(int offset) {
        if (mDisableTextScrollingFromAutocomplete) return false;
        return super.bringPointIntoView(offset);
    }

    @Override
    public boolean onPreDraw() {
        boolean retVal = super.onPreDraw();
        if (mDisableTextScrollingFromAutocomplete) {
            // super.onPreDraw will put the selection at the end of the text selection, but
            // in the case of autocomplete we want the last typed character to be shown, which
            // is the start of selection.
            mDisableTextScrollingFromAutocomplete = false;
            bringPointIntoView(getSelectionStart());
            retVal = true;
        }
        return retVal;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!mFirstDrawComplete) {
            mFirstDrawComplete = true;

            // We have now avoided the first draw problem (see the comment in
            // the constructor) so we want to make the URL bar focusable so that
            // touches etc. activate it.
            setFocusable(mAllowFocus);
            setFocusableInTouchMode(mAllowFocus);

            // The URL bar will now react correctly to a focus change event
            if (mOmniboxLivenessListener != null) {
                mOmniboxLivenessListener.onOmniboxInteractive();
            }
        }

        // Notify listeners if the URL's direction has changed.
        updateUrlDirection();
    }

    /**
     * If the direction of the URL has changed, update mUrlDirection and notify the
     * UrlDirectionListeners.
     */
    private void updateUrlDirection() {
        Layout layout = getLayout();
        if (layout == null) return;

        int urlDirection;
        if (length() == 0) {
            urlDirection = LAYOUT_DIRECTION_LOCALE;
        } else if (layout.getParagraphDirection(0) == Layout.DIR_LEFT_TO_RIGHT) {
            urlDirection = LAYOUT_DIRECTION_LTR;
        } else {
            urlDirection = LAYOUT_DIRECTION_RTL;
        }

        if (urlDirection != mUrlDirection) {
            mUrlDirection = urlDirection;
            if (mUrlDirectionListener != null) {
                mUrlDirectionListener.onUrlDirectionChanged(urlDirection);
            }
        }
    }

    /**
     * @return The text direction of the URL, e.g. LAYOUT_DIRECTION_LTR.
     */
    public int getUrlDirection() {
        return mUrlDirection;
    }

    /**
     * Sets the listener for changes in the url bar's layout direction. Also calls
     * onUrlDirectionChanged() immediately on the listener.
     *
     * @param listener The UrlDirectionListener to receive callbacks when the url direction changes,
     *     or null to unregister any previously registered listener.
     */
    public void setUrlDirectionListener(UrlDirectionListener listener) {
        mUrlDirectionListener = listener;
        if (mUrlDirectionListener != null) {
            mUrlDirectionListener.onUrlDirectionChanged(mUrlDirection);
        }
    }

    void setLocationBarTextWatcher(TextWatcher locationBarTextWatcher) {
        mLocationBarTextWatcher = locationBarTextWatcher;
    }

    /**
     * Set the url delegate to handle communication from the {@link UrlBar} to the rest of the UI.
     * @param delegate The {@link UrlBarDelegate} to be used.
     */
    public void setDelegate(UrlBarDelegate delegate) {
        mUrlBarDelegate = delegate;
    }

    /**
     * Set {@link OmniboxLivenessListener} to be used for receiving interaction related messages
     * during startup.
     * @param listener The listener to use for sending the messages.
     */
    @VisibleForTesting
    public void setOmniboxLivenessListener(OmniboxLivenessListener listener) {
        mOmniboxLivenessListener = listener;
    }

    /**
     * Signal {@link OmniboxLivenessListener} that the omnibox is completely operational now.
     */
    @VisibleForTesting
    public void onOmniboxFullyFunctional() {
        if (mOmniboxLivenessListener != null) mOmniboxLivenessListener.onOmniboxFullyFunctional();
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    builder.append(clipData.getItemAt(i).coerceToText(getContext()));
                }
                String pasteString = OmniboxViewUtil.sanitizeTextForPaste(builder.toString());

                int min = 0;
                int max = getText().length();

                if (isFocused()) {
                    final int selStart = getSelectionStart();
                    final int selEnd = getSelectionEnd();

                    min = Math.max(0, Math.min(selStart, selEnd));
                    max = Math.max(0, Math.max(selStart, selEnd));
                }

                Selection.setSelection(getText(), max);
                getText().replace(min, max, pasteString);
                mIsPastedText = true;
                return true;
            }
        }

        if (mOriginalUrlLocation == null || mFormattedUrlLocation == null) {
            return super.onTextContextMenuItem(id);
        }

        int selectedStartIndex = getSelectionStart();
        int selectedEndIndex = getSelectionEnd();

        // If we are copying/cutting the full previously formatted URL, reset the URL
        // text before initiating the TextViews handling of the context menu.
        String currentText = getText().toString();
        if (selectedStartIndex == 0
                && (id == android.R.id.cut || id == android.R.id.copy)
                && currentText.startsWith(mFormattedUrlLocation)
                && selectedEndIndex >= mFormattedUrlLocation.length()) {
            String newText = mOriginalUrlLocation
                    + currentText.substring(mFormattedUrlLocation.length());
            selectedEndIndex = selectedEndIndex - mFormattedUrlLocation.length()
                    + mOriginalUrlLocation.length();
            mUrlBarDelegate.setIgnoreURLBarModification(true);
            setText(newText);
            setSelection(0, selectedEndIndex);
            boolean retVal = super.onTextContextMenuItem(id);
            if (getText().toString().equals(newText)) {
                setText(currentText);
                setSelection(getText().length());
            }
            mUrlBarDelegate.setIgnoreURLBarModification(false);
            return retVal;
        }
        return super.onTextContextMenuItem(id);
    }

    /**
     * Sets the text content of the URL bar.
     *
     * @param url The original URL (or generic text) that can be used for copy/cut/paste.
     * @param formattedUrl Formatted URL for user display. Null if there isn't one.
     * @return Whether the visible text has changed.
     */
    public boolean setUrl(String url, String formattedUrl) {
        if (!TextUtils.isEmpty(formattedUrl)) {
            try {
                URL javaUrl = new URL(url);
                mFormattedUrlLocation =
                        getUrlContentsPrePath(formattedUrl, javaUrl.getHost());
                mOriginalUrlLocation =
                        getUrlContentsPrePath(url, javaUrl.getHost());
            } catch (MalformedURLException mue) {
                mOriginalUrlLocation = null;
                mFormattedUrlLocation = null;
            }
        } else {
            mOriginalUrlLocation = null;
            mFormattedUrlLocation = null;
            formattedUrl = url;
        }

        Editable previousText = getEditableText();
        setText(formattedUrl);

        if (!isFocused()) scrollToTLD();

        return !TextUtils.equals(previousText, getEditableText());
    }

    /**
     * Autocompletes the text on the url bar and selects the text that was not entered by the
     * user. Using append() instead of setText() to preserve the soft-keyboard layout.
     * @param userText user The text entered by the user.
     * @param inlineAutocompleteText The suggested autocompletion for the user's text.
     */
    public void setAutocompleteText(CharSequence userText, CharSequence inlineAutocompleteText) {
        boolean emptyAutocomplete = TextUtils.isEmpty(inlineAutocompleteText);

        if (!emptyAutocomplete) mDisableTextScrollingFromAutocomplete = true;

        int autocompleteIndex = userText.length();

        String previousText = getQueryText();
        CharSequence newText = TextUtils.concat(userText, inlineAutocompleteText);

        mUrlBarDelegate.setIgnoreURLBarModification(true);
        mDisableTextAccessibilityEvents = true;

        if (!TextUtils.equals(previousText, newText)) {
            // The previous text may also have included autocomplete text, so we only
            // append the new autocomplete text that has changed.
            if (TextUtils.indexOf(newText, previousText) == 0) {
                append(newText.subSequence(previousText.length(), newText.length()));
            } else {
                setUrl(newText.toString(), null);
            }
        }

        if (getSelectionStart() != autocompleteIndex
                || getSelectionEnd() != getText().length()) {
            setSelection(autocompleteIndex, getText().length());

            if (inlineAutocompleteText.length() != 0) {
                // Sending a TYPE_VIEW_TEXT_SELECTION_CHANGED accessibility event causes the
                // previous TYPE_VIEW_TEXT_CHANGED event to be swallowed. As a result the user
                // hears the autocomplete text but *not* the text they typed. Instead we send a
                // TYPE_ANNOUNCEMENT event, which doesn't swallow the text-changed event.
                announceForAccessibility(inlineAutocompleteText);
            }
        }

        if (emptyAutocomplete) {
            mAutocompleteSpan.clearSpan();
        } else {
            mAutocompleteSpan.setSpan(userText, inlineAutocompleteText);
        }

        mUrlBarDelegate.setIgnoreURLBarModification(false);
        mDisableTextAccessibilityEvents = false;
    }

    /**
     * Overrides the text announced when focusing on the field for accessibility.  This value will
     * be cleared automatically when the text content changes for this view.
     * @param accessibilityOverride The text to be announced instead of the current text value
     *                              (or null if the text content should be read).
     */
    public void setAccessibilityTextOverride(String accessibilityOverride) {
        mAccessibilityTextOverride = accessibilityOverride;
    }

    private void scrollToTLD() {
        Editable url = getText();
        if (url == null || url.length() < 1) return;
        String urlString = url.toString();
        URL javaUrl;
        try {
            javaUrl = new URL(urlString);
        } catch (MalformedURLException mue) {
            return;
        }
        String host = javaUrl.getHost();
        if (host == null || host.isEmpty()) return;
        int hostStart = urlString.indexOf(host);
        int hostEnd = hostStart + host.length();
        setSelection(hostEnd);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (!mInBatchEditMode) limitDisplayableLength();
        mIsPastedText = false;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        mDisableTextScrollingFromAutocomplete = false;

        // Avoid setting the same text to the URL bar as it will mess up the scroll/cursor
        // position.
        // Setting the text is also quite expensive, so only do it when the text has changed
        // (since we apply spans when the URL is not focused, we only optimize this when the
        // URL is being edited).
        if (!TextUtils.equals(getEditableText(), text)) {
            super.setText(text, type);
            mAccessibilityTextOverride = null;
        }

        // Verify the autocomplete is still valid after the text change.
        if (mAutocompleteSpan != null
                && mAutocompleteSpan.mUserText != null
                && mAutocompleteSpan.mAutocompleteText != null) {
            if (getText().getSpanStart(mAutocompleteSpan) < 0) {
                mAutocompleteSpan.clearSpan();
            } else {
                Editable editableText = getEditableText();
                CharSequence previousUserText = mAutocompleteSpan.mUserText;
                CharSequence previousAutocompleteText = mAutocompleteSpan.mAutocompleteText;
                if (editableText.length()
                        < (previousUserText.length() + previousAutocompleteText.length())) {
                    mAutocompleteSpan.clearSpan();
                } else if (TextUtils.indexOf(getText(), previousUserText) != 0
                        || TextUtils.indexOf(getText(), previousAutocompleteText)
                        != previousUserText.length()) {
                    mAutocompleteSpan.clearSpan();
                }
            }
        }
    }

    private void limitDisplayableLength() {
        // To limit displayable length we replace middle portion of the string with ellipsis.
        // That affects only presentation of the text, and doesn't affect other aspects like
        // copying to the clipboard, getting text with getText(), etc.
        final int maxLength = SysUtils.isLowEndDevice()
                ? MAX_DISPLAYABLE_LENGTH_LOW_END : MAX_DISPLAYABLE_LENGTH;

        Editable text = getText();
        int textLength = text.length();
        if (textLength <= maxLength) {
            if (mDidEllipsizeTextHint) {
                EllipsisSpan[] spans = text.getSpans(0, textLength, EllipsisSpan.class);
                if (spans != null && spans.length > 0) {
                    assert spans.length == 1 : "Should never apply more than a single EllipsisSpan";
                    for (int i = 0; i < spans.length; i++) {
                        text.removeSpan(spans[i]);
                    }
                }
            }
            mDidEllipsizeTextHint = false;
            return;
        }

        mDidEllipsizeTextHint = true;

        int spanLeft = text.nextSpanTransition(0, textLength, EllipsisSpan.class);
        if (spanLeft != textLength) return;

        spanLeft = maxLength / 2;
        text.setSpan(EllipsisSpan.INSTANCE, spanLeft, textLength - spanLeft,
                Editable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    /**
     * Returns the portion of the URL that precedes the path/query section of the URL.
     *
     * @param url The url to be used to find the preceding portion.
     * @param host The host to be located in the URL to determine the location of the path.
     * @return The URL contents that precede the path (or the passed in URL if the host is
     *         not found).
     */
    private static String getUrlContentsPrePath(String url, String host) {
        String urlPrePath = url;
        int hostIndex = url.indexOf(host);
        if (hostIndex >= 0) {
            int pathIndex = url.indexOf('/', hostIndex);
            if (pathIndex > 0) {
                urlPrePath = url.substring(0, pathIndex);
            } else {
                urlPrePath = url;
            }
        }
        return urlPrePath;
    }

    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (mDisableTextAccessibilityEvents) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                return;
            }
        }
        super.sendAccessibilityEventUnchecked(event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        if (mAccessibilityTextOverride != null) {
            info.setText(mAccessibilityTextOverride);
        }
    }

    @VisibleForTesting
    InputConnectionWrapper mInputConnection = new InputConnectionWrapper(null, true) {
        private final char[] mTempSelectionChar = new char[1];

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            Editable currentText = getText();
            if (currentText == null) return super.commitText(text, newCursorPosition);

            int selectionStart = Selection.getSelectionStart(currentText);
            int selectionEnd = Selection.getSelectionEnd(currentText);
            int autocompleteIndex = currentText.getSpanStart(mAutocompleteSpan);
            // If the text being committed is a single character that matches the next character
            // in the selection (assumed to be the autocomplete text), we only move the text
            // selection instead clearing the autocomplete text causing flickering as the
            // autocomplete text will appear once the next suggestions are received.
            //
            // To be confident that the selection is an autocomplete, we ensure the selection
            // is at least one character and the end of the selection is the end of the
            // currently entered text.
            if (newCursorPosition == 1 && selectionStart > 0 && selectionStart != selectionEnd
                    && selectionEnd >= currentText.length()
                    && autocompleteIndex == selectionStart
                    && text.length() == 1) {
                currentText.getChars(selectionStart, selectionStart + 1, mTempSelectionChar, 0);
                if (mTempSelectionChar[0] == text.charAt(0)) {

                    // Since the text isn't changing, TalkBack won't read out the typed characters.
                    // To work around this, explicitly send an accessibility event. crbug.com/416595
                    if (mAccessibilityManager != null && mAccessibilityManager.isEnabled()) {
                        AccessibilityEvent event = AccessibilityEvent.obtain(
                                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
                        event.setFromIndex(selectionStart);
                        event.setRemovedCount(0);
                        event.setAddedCount(1);
                        event.setBeforeText(currentText.toString().substring(0, selectionStart));
                        sendAccessibilityEventUnchecked(event);
                    }

                    if (mLocationBarTextWatcher != null) {
                        mLocationBarTextWatcher.beforeTextChanged(currentText, 0, 0, 0);
                    }
                    setAutocompleteText(
                            currentText.subSequence(0, selectionStart + 1),
                            currentText.subSequence(selectionStart + 1, selectionEnd));
                    if (mLocationBarTextWatcher != null) {
                        mLocationBarTextWatcher.afterTextChanged(currentText);
                    }
                    return true;
                }
            }
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            Editable currentText = getText();
            int autoCompleteSpanStart = currentText.getSpanStart(mAutocompleteSpan);
            if (autoCompleteSpanStart >= 0) {
                int composingEnd = BaseInputConnection.getComposingSpanEnd(currentText);

                // On certain device/keyboard combinations, the composing regions are specified
                // with a noticeable delay after the initial character is typed, and in certain
                // circumstances it does not check that the current state of the text matches the
                // expectations of it's composing region.
                // For example, you can be typing:
                //   chrome://f
                // Chrome will autocomplete to:
                //   chrome://f[lags]
                // And after the autocomplete has been set, the keyboard will set the composing
                // region to the last character and it assumes it is 'f' as it was the last
                // character the keyboard sent.  If we commit this composition, the text will
                // look like:
                //   chrome://flag[f]
                // And if we use the autocomplete clearing logic below, it will look like:
                //   chrome://f[f]
                // To work around this, we see if the composition matches all the characters prior
                // to the autocomplete and just readjust the composing region to be that subset.
                //
                // See crbug.com/366732
                if (composingEnd == currentText.length()
                        && autoCompleteSpanStart >= text.length()
                        && TextUtils.equals(
                                currentText.subSequence(
                                        autoCompleteSpanStart - text.length(),
                                        autoCompleteSpanStart),
                                text)) {
                    setComposingRegion(
                            autoCompleteSpanStart - text.length(), autoCompleteSpanStart);
                }

                // Once composing text is being modified, the autocomplete text has been accepted
                // or has to be deleted.
                mAutocompleteSpan.clearSpan();
                Selection.setSelection(currentText, autoCompleteSpanStart);
                currentText.delete(autoCompleteSpanStart, currentText.length());
            }
            return super.setComposingText(text, newCursorPosition);
        }
    };

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        mInputConnection.setTarget(super.onCreateInputConnection(outAttrs));
        return mInputConnection;
    }

    /**
     * Emphasize the TLD and second domain of the URL.
     */
    public void emphasizeUrl() {
        Editable url = getText();
        if (OmniboxUrlEmphasizer.hasEmphasisSpans(url) || hasFocus()) {
            return;
        }

        if (url.length() < 1) {
            return;
        }

        // We retrieve the domain and registry from the full URL (the url bar shows a simplified
        // version of the URL).
        Tab currentTab = mUrlBarDelegate.getCurrentTab();
        if (currentTab == null || currentTab.getProfile() == null) return;

        boolean isInternalPage = false;
        try {
            String tabUrl = currentTab.getUrl();
            isInternalPage = UrlUtilities.isInternalScheme(new URI(tabUrl));
        } catch (URISyntaxException e) {
            // Ignore as this only is for applying color
        }

        OmniboxUrlEmphasizer.emphasizeUrl(url, getResources(), currentTab.getProfile(),
                currentTab.getSecurityLevel(), isInternalPage,
                mUseDarkColors, mUrlBarDelegate.shouldEmphasizeHttpsScheme());
    }

    /**
     * Reset the modifications done to emphasize the TLD and second domain of the URL.
     */
    public void deEmphasizeUrl() {
        OmniboxUrlEmphasizer.deEmphasizeUrl(getText());
    }

    /**
     * @return Whether the current UrlBar input has been pasted from the clipboard.
     */
    public boolean isPastedText() {
        return mIsPastedText;
    }

    /**
     * Simple span used for tracking the current autocomplete state.
     */
    private class AutocompleteSpan {
        private CharSequence mUserText;
        private CharSequence mAutocompleteText;

        /**
         * Adds the span to the current text.
         * @param userText The user entered text.
         * @param autocompleteText The autocomplete text being appended.
         */
        public void setSpan(CharSequence userText, CharSequence autocompleteText) {
            Editable text = getText();
            text.removeSpan(this);
            mAutocompleteText = autocompleteText;
            mUserText = userText;
            text.setSpan(
                    this,
                    userText.length(),
                    text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        /** Removes this span from the current text and clears the internal state. */
        public void clearSpan() {
            getText().removeSpan(this);
            mAutocompleteText = null;
            mUserText = null;
        }
    }

    /**
     * Span that displays ellipsis instead of the text. Used to hide portion of
     * very large string to get decent performance from TextView.
     */
    private static class EllipsisSpan extends ReplacementSpan {
        private static final String ELLIPSIS = "...";

        public static final EllipsisSpan INSTANCE = new EllipsisSpan();

        @Override
        public int getSize(Paint paint, CharSequence text,
                int start, int end, Paint.FontMetricsInt fm) {
            return (int) paint.measureText(ELLIPSIS);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {
            canvas.drawText(ELLIPSIS, x, y, paint);
        }
    }
}
