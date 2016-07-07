// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.components;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;

/**
 * {@link CompositorButton} keeps track of state for buttons that are rendered
 * in the compositor.
 */
public class CompositorButton
        implements ChromeAnimation.Animatable<CompositorButton.Property>, VirtualView {
    /**
     * Animatable properties that can be used with a {@link ChromeAnimation.Animatable} on a
     * {@link CompositorButton}.
     */
    public enum Property {
        OPACITY,
    }

    // Precached bounds rect.
    private final RectF mBounds = new RectF();

    private int mResource;
    private int mPressedResource;
    private int mIncognitoResource;
    private int mIncognitoPressedResource;

    private float mOpacity;
    private float mClickSlop;
    private boolean mIsPressed;
    private boolean mIsVisible;
    private boolean mIsIncognito;
    private boolean mIsEnabled;
    private String mAccessibilityDescription;
    private String mAccessibilityDescriptionIncognito;

    private final RectF mCacheBounds = new RectF(); // Pre-allocated to avoid in-frame allocations.

    /**
     * Default constructor for {@link CompositorButton}
     * @param context   An Android context for fetching dimens.
     * @param width     The button width.
     * @param height    The button height.
     */
    public CompositorButton(Context context, float width, float height) {
        mBounds.set(0, 0, width, height);

        mOpacity = 1.f;
        mIsPressed = false;
        mIsVisible = true;
        mIsIncognito = false;
        mIsEnabled = true;

        Resources res = context.getResources();
        float sPxToDp = 1.0f / res.getDisplayMetrics().density;
        mClickSlop = res.getDimension(R.dimen.compositor_button_slop) * sPxToDp;
    }

    /**
     * Secondary constructor for {@link CompositorButton}
     * @param context   An Android context for fetching dimens.
     * @param bounds    A RectF that bounds the button.
     */
    public CompositorButton(Context context, RectF bounds) {
        this(context, bounds.width(), bounds.height());
        mBounds.set(bounds);
    }

    /**
     * A set of Android resources to supply to the compositor.
     * @param resource                  The default Android resource.
     * @param pressedResource           The pressed Android resource.
     * @param incognitoResource         The incognito Android resource.
     * @param incognitoPressedResource  The incognito pressed resource.
     */
    public void setResources(int resource, int presssedResource, int incognitoResource,
            int incognitoPressedResource) {
        mResource = resource;
        mPressedResource = presssedResource;
        mIncognitoResource = incognitoResource;
        mIncognitoPressedResource = incognitoPressedResource;
    }

    /**
     * @param description A string describing the resource.
     */
    public void setAccessibilityDescription(String description, String incognitoDescription) {
        mAccessibilityDescription = description;
        mAccessibilityDescriptionIncognito = incognitoDescription;
    }

    @Override
    public String getAccessibilityDescription() {
        return mIsIncognito ? mAccessibilityDescriptionIncognito : mAccessibilityDescription;
    }

    @Override
    public void getTouchTarget(RectF outTarget) {
        outTarget.set(mBounds);
        // Get the whole touchable region.
        outTarget.inset((int) -mClickSlop, (int) -mClickSlop);
    }

    /**
     * @return The the x offset of the button.
     */
    public float getX() {
        return mBounds.left;
    }

    /**
     * @param x The x offset of the button.
     */
    public void setX(float x) {
        mBounds.right = x + mBounds.width();
        mBounds.left = x;
    }

    /**
     * @return The y offset of the button.
     */
    public float getY() {
        return mBounds.top;
    }

    /**
     * @param y The y offset of the button.
     */
    public void setY(float y) {
        mBounds.bottom = y + mBounds.height();
        mBounds.top = y;
    }

    /**
     * @return The width of the button.
     */
    public float getWidth() {
        return mBounds.width();
    }

    /**
     * @param width The width of the button.
     */
    public void setWidth(float width) {
        mBounds.right = mBounds.left + width;
    }

    /**
     * @return The height of the button.
     */
    public float getHeight() {
        return mBounds.height();
    }

    /**
     * @param height The height of the button.
     */
    public void setHeight(float height) {
        mBounds.bottom = mBounds.top + height;
    }

    /**
     * @param bounds A {@link Rect} representing the location of the button.
     */
    public void setBounds(RectF bounds) {
        mBounds.set(bounds);
    }

    /**
     * @return The opacity of the button.
     */
    public float getOpacity() {
        return mOpacity;
    }

    /**
     * @param opacity The opacity of the button.
     */
    public void setOpacity(float opacity) {
        mOpacity = opacity;
    }

    /**
     * @return The pressed state of the button.
     */
    public boolean isPressed() {
        return mIsPressed;
    }

    /**
     * @param state The pressed state of the button.
     */
    public void setPressed(boolean state) {
        mIsPressed = state;
    }

    /**
     * @return The visiblity of the button.
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * @param state The visibility of the button.
     */
    public void setVisible(boolean state) {
        mIsVisible = state;
    }

    /**
     * @return The incognito state of the button.
     */
    public boolean isIncognito() {
        return mIsIncognito;
    }

    /**
     * @param state The incognito state of the button.
     */
    public void setIncognito(boolean state) {
        mIsIncognito = state;
    }

    /**
     * @return Whether or not the button can be interacted with.
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * @param state Whether or not the button can be interacted with.
     */
    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    /**
     * @param slop  The additional area outside of the button to be considered when
     *              checking click target bounds.
     */
    public void setClickSlop(float slop) {
        mClickSlop = slop;
    }

    /**
     * @return The Android resource id for this button based on it's state.
     */
    public int getResourceId() {
        if (isPressed()) {
            return isIncognito() ? mIncognitoPressedResource : mPressedResource;
        }
        return isIncognito() ? mIncognitoResource : mResource;
    }

    /**
     * @param x The x offset of the click.
     * @param y The y offset of the click.
     * @return Whether or not that click occurred inside of the button + slop area.
     */
    @Override
    public boolean checkClicked(float x, float y) {
        if (mOpacity < 1.f || !mIsVisible || !mIsEnabled) return false;

        mCacheBounds.set(mBounds);
        mCacheBounds.inset(-mClickSlop, -mClickSlop);
        return mCacheBounds.contains(x, y);
    }

    /**
     * Set state for a drag event.
     * @param x     The x offset of the event.
     * @param y     The y offset of the event.
     * @return      Whether or not the button is selected after the event.
     */
    public boolean drag(float x, float y) {
        if (!checkClicked(x, y)) {
            setPressed(false);
            return false;
        }
        return isPressed();
    }

    /**
     * Set state for an onDown event.
     * @param x     The x offset of the event.
     * @param y     The y offset of the event.
     * @return      Whether or not the close button was selected.
     */
    public boolean onDown(float x, float y) {
        if (checkClicked(x, y)) {
            setPressed(true);
            return true;
        }
        return false;
    }

    /**
     * @param x     The x offset of the event.
     * @param y     The y offset of the event.
     * @return      If the button was clicked or not.
     */
    public boolean click(float x, float y) {
        if (checkClicked(x, y)) {
            setPressed(false);
            return true;
        }
        return false;
    }

    /**
     * Set state for an onUpOrCancel event.
     * @return Whether or not the button was selected.
     */
    public boolean onUpOrCancel() {
        boolean state = isPressed();
        setPressed(false);
        return state;
    }

    /**
     * Callback for {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation
     * .Animatable}
     *
     * @param prop The property to set
     * @param val The value to set it to
     */
    @Override
    public void setProperty(Property prop, float val) {
        switch (prop) {
            case OPACITY:
                setOpacity(val);
                break;
            default:
                // Do nothing.
        }
    }
}
