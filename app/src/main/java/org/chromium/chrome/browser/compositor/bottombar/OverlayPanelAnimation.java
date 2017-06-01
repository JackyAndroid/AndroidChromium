// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.createAnimation;

import android.content.Context;
import android.view.animation.Interpolator;

import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animation;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.chrome.browser.util.MathUtils;

/**
 * Base abstract class for animating the Overlay Panel.
 */
public abstract class OverlayPanelAnimation extends OverlayPanelBase
        implements Animatable<OverlayPanelAnimation.Property> {

    /**
     * Animation properties.
     */
    protected enum Property {
        PANEL_HEIGHT
    }

    /**
     * The base duration of animations in milliseconds. This value is based on
     * the Kennedy specification for slow animations.
     */
    public static final long BASE_ANIMATION_DURATION_MS = 218;

    /** The maximum animation duration in milliseconds. */
    public static final long MAXIMUM_ANIMATION_DURATION_MS = 350;

    /** The minimum animation duration in milliseconds. */
    private static final long MINIMUM_ANIMATION_DURATION_MS = Math.round(7 * 1000 / 60);

    /** Average animation velocity in dps per second. */
    private static final float INITIAL_ANIMATION_VELOCITY_DP_PER_SECOND = 1750f;

    /** The PanelState to which the Panel is being animated. */
    private PanelState mAnimatingState;

    /** The StateChangeReason for which the Panel is being animated. */
    private StateChangeReason mAnimatingStateReason;

    /** The animation set. */
    private ChromeAnimation<Animatable<?>> mLayoutAnimations;

    /** The {@link LayoutUpdateHost} used to request a new frame to be updated and rendered. */
    private final LayoutUpdateHost mUpdateHost;

    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     * @param updateHost The {@link LayoutUpdateHost} used to request updates in the Layout.
     */
    public OverlayPanelAnimation(Context context, LayoutUpdateHost updateHost) {
        super(context);
        mUpdateHost = updateHost;
    }

    // ============================================================================================
    // Animation API
    // ============================================================================================

    /**
     * Animates the Overlay Panel to its maximized state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void maximizePanel(StateChangeReason reason) {
        animatePanelToState(PanelState.MAXIMIZED, reason);
    }

    /**
     * Animates the Overlay Panel to its intermediary state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void expandPanel(StateChangeReason reason) {
        animatePanelToState(PanelState.EXPANDED, reason);
    }

    /**
     * Animates the Overlay Panel to its peeked state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void peekPanel(StateChangeReason reason) {
        updateBasePageTargetY();

        // TODO(pedrosimonetti): Implement custom animation with the following values.
        // int SEARCH_BAR_ANIMATION_DURATION_MS = 218;
        // float SEARCH_BAR_SLIDE_OFFSET_DP = 40;
        // float mSearchBarHeightDp;
        // setTranslationY(mIsShowingFirstRunFlow
        //      ? mSearchBarHeightDp : SEARCH_BAR_SLIDE_OFFSET_DP);
        // setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);
        animatePanelToState(PanelState.PEEKED, reason);
    }

    @Override
    protected void closePanel(StateChangeReason reason, boolean animate) {
        if (animate) {
            // Only animates the closing action if not doing that already.
            if (mAnimatingState != PanelState.CLOSED) {
                animatePanelToState(PanelState.CLOSED, reason);
            }
        } else {
            resizePanelToState(PanelState.CLOSED, reason);
        }
    }

    @Override
    protected void handleSizeChanged(float width, float height, float previousWidth) {
        if (!isShowing()) return;

        boolean wasFullWidthSizePanel = doesMatchFullWidthCriteria(previousWidth);
        boolean isFullWidthSizePanel = isFullWidthSizePanel();
        // We support resize from any full width to full width, or from narrow width to narrow width
        // when the width does not change (as when the keyboard is shown/hidden).
        boolean isPanelResizeSupported = isFullWidthSizePanel && wasFullWidthSizePanel
                || !isFullWidthSizePanel && !wasFullWidthSizePanel && width == previousWidth;

        // TODO(pedrosimonetti): See crbug.com/568351.
        // We can't keep the panel opened after a viewport size change when the panel's
        // ContentView needs to be resized to a non-default size. The panel provides
        // different desired MeasureSpecs when full-width vs narrow-width
        // (See {@link OverlayPanel#createNewOverlayPanelContentInternal()}).
        // When the activity is resized, ContentViewClient asks for the MeasureSpecs
        // before the panel is notified of the size change, resulting in the panel's
        // ContentView being laid out incorrectly.
        if (isPanelResizeSupported) {
            if (mAnimatingState != PanelState.UNDEFINED) {
                // If the size changes when an animation is happening, then we need to restart the
                // animation, because the size of the Panel might have changed as well.
                animatePanelToState(mAnimatingState, mAnimatingStateReason);
            } else {
                updatePanelForSizeChange();
            }
        } else {
            // TODO(pedrosimonetti): Find solution that does not require async handling.
            // NOTE(pedrosimonetti): Should close the Panel asynchronously because
            // we might be in the middle of laying out the CompositorViewHolder
            // View. See {@link CompositorViewHolder#onLayout()}. Closing the Panel
            // has the effect of destroying the Views used by the Panel (which are
            // children of the CompositorViewHolder), and if we do that synchronously
            // it will cause a crash in {@link FrameLayout#layoutChildren()}.
            mContainerView.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    closePanel(StateChangeReason.UNKNOWN, false);
                }
            });
        }
    }

    /**
     * Updates the Panel so it preserves its state when the size changes.
     */
    protected void updatePanelForSizeChange() {
        resizePanelToState(getPanelState(), StateChangeReason.UNKNOWN);
    }

    /**
     * Animates the Overlay Panel to a given |state| with a default duration.
     *
     * @param state The state to animate to.
     * @param reason The reason for the change of panel state.
     */
    private void animatePanelToState(PanelState state, StateChangeReason reason) {
        animatePanelToState(state, reason, BASE_ANIMATION_DURATION_MS);
    }

    /**
     * Animates the Overlay Panel to a given |state| with a custom |duration|.
     *
     * @param state The state to animate to.
     * @param reason The reason for the change of panel state.
     * @param duration The animation duration in milliseconds.
     */
    protected void animatePanelToState(PanelState state, StateChangeReason reason, long duration) {
        mAnimatingState = state;
        mAnimatingStateReason = reason;

        final float height = getPanelHeightFromState(state);
        animatePanelTo(height, duration);
    }

    /**
     * Resizes the Overlay Panel to a given |state|.
     *
     * @param state The state to resize to.
     * @param reason The reason for the change of panel state.
     */
    protected void resizePanelToState(PanelState state, StateChangeReason reason) {
        cancelHeightAnimation();

        final float height = getPanelHeightFromState(state);
        setPanelHeight(height);
        setPanelState(state, reason);
        requestUpdate();
    }

    // ============================================================================================
    // Animation Helpers
    // ============================================================================================

    /**
     * Animates the Panel to its nearest state.
     */
    protected void animateToNearestState() {
        // Calculate the nearest state from the current position, and then calculate the duration
        // of the animation that will start with a desired initial velocity and move the desired
        // amount of dps (displacement).
        final PanelState nearestState = findNearestPanelStateFromHeight(getHeight(), 0.0f);
        final float displacement = getPanelHeightFromState(nearestState) - getHeight();
        final long duration = calculateAnimationDuration(
                INITIAL_ANIMATION_VELOCITY_DP_PER_SECOND, displacement);

        animatePanelToState(nearestState, StateChangeReason.SWIPE, duration);
    }

    /**
     * Animates the Panel to its projected state, given a particular vertical |velocity|.
     *
     * @param velocity The velocity of the gesture in dps per second.
     */
    protected void animateToProjectedState(float velocity) {
        PanelState projectedState = getProjectedState(velocity);

        final float displacement = getPanelHeightFromState(projectedState) - getHeight();
        final long duration = calculateAnimationDuration(velocity, displacement);

        animatePanelToState(projectedState, StateChangeReason.FLING, duration);
    }

    /**
     * @param velocity The given velocity.
     * @return The projected state the Panel will be if the given velocity is applied.
     */
    protected PanelState getProjectedState(float velocity) {
        final float kickY = calculateAnimationDisplacement(velocity, BASE_ANIMATION_DURATION_MS);
        final float projectedHeight = getHeight() - kickY;

        // Calculate the projected state the Panel will be at the end of the fling movement and the
        // duration of the animation given the current velocity and the projected displacement.
        PanelState projectedState = findNearestPanelStateFromHeight(projectedHeight, velocity);

        return projectedState;
    }

    /**
     * Calculates the animation displacement given the |initialVelocity| and a
     * desired |duration|.
     *
     * @param initialVelocity The initial velocity of the animation in dps per second.
     * @param duration The desired duration of the animation in milliseconds.
     * @return The animation displacement in dps.
     */
    protected float calculateAnimationDisplacement(float initialVelocity, float duration) {
        // NOTE(pedrosimonetti): This formula assumes the deceleration curve is
        // quadratic (t^2),
        // hence the displacement formula should be:
        // displacement = initialVelocity * duration / 2
        //
        // We are also converting the duration from milliseconds to seconds,
        // which explains why
        // we are dividing by 2000 (2 * 1000) instead of 2.
        return initialVelocity * duration / 2000;
    }

    /**
     * Calculates the animation duration given the |initialVelocity| and a
     * desired |displacement|.
     *
     * @param initialVelocity The initial velocity of the animation in dps per second.
     * @param displacement The displacement of the animation in dps.
     * @return The animation duration in milliseconds.
     */
    private long calculateAnimationDuration(float initialVelocity, float displacement) {
        // NOTE(pedrosimonetti): This formula assumes the deceleration curve is
        // quadratic (t^2),
        // hence the duration formula should be:
        // duration = 2 * displacement / initialVelocity
        //
        // We are also converting the duration from seconds to milliseconds,
        // which explains why
        // we are multiplying by 2000 (2 * 1000) instead of 2.
        return MathUtils.clamp(Math.round(Math.abs(2000 * displacement / initialVelocity)),
                MINIMUM_ANIMATION_DURATION_MS, MAXIMUM_ANIMATION_DURATION_MS);
    }

    /**
     * Cancels any height animation in progress.
     */
    protected void cancelHeightAnimation() {
        cancelAnimation(this, Property.PANEL_HEIGHT);
    }

    // ============================================================================================
    // Layout Integration
    // ============================================================================================

    /**
     * Requests a new frame to be updated and rendered.
     */
    protected void requestUpdate() {
        // NOTE(pedrosimonetti): mUpdateHost will be null in the ContextualSearchEventFilterTest,
        // so we always need to check if it's null before calling requestUpdate.
        if (mUpdateHost != null) {
            mUpdateHost.requestUpdate();
        }
    }

    // ============================================================================================
    // Animation Framework
    // ============================================================================================

    /**
     * Animates the Overlay Panel to a given |height| with a custom |duration|.
     *
     * @param height The height to animate to.
     * @param duration The animation duration in milliseconds.
     */
    private void animatePanelTo(float height, long duration) {
        animateProperty(Property.PANEL_HEIGHT, getHeight(), height, duration);
    }

    /**
     * Animates the Overlay Panel.
     *
     * @param property The property which will be animated.
     * @param start The initial value.
     * @param end The final value.
     * @param duration The animation duration in milliseconds.
     */
    protected void animateProperty(Property property, float start, float end, long duration) {
        if (duration > 0) {
            if (animationIsRunning()) {
                cancelAnimation(this, property);
            }
            addToAnimation(this, property, start, end, duration, 0);
        }
    }

    /**
     * Sets a property for an animation.
     *
     * @param prop The property to update.
     * @param value New value of the property.
     */
    @Override
    public void setProperty(Property prop, float value) {
        if (prop == Property.PANEL_HEIGHT) {
            setPanelHeight(value);
        }
    }

    @Override
    public void onPropertyAnimationFinished(Property prop) {}

    /**
     * Steps the animation forward and updates all the animated values.
     * @param time      The current time of the app in ms.
     * @param jumpToEnd Whether to finish the animation.
     * @return          Whether the animation was finished.
     */
    public boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        boolean finished = true;
        if (mLayoutAnimations != null) {
            if (jumpToEnd) {
                finished = mLayoutAnimations.finished();
                mLayoutAnimations.updateAndFinish();
            } else {
                finished = mLayoutAnimations.update(time);
            }

            if (finished || jumpToEnd) {
                mLayoutAnimations = null;
                onAnimationFinished();
            }
            requestUpdate();
        }
        return finished;
    }

    /**
     * Called when layout-specific actions are needed after the animation finishes.
     */
    protected void onAnimationStarted() {
    }

    /**
     * Called when layout-specific actions are needed after the animation finishes.
     */
    protected void onAnimationFinished() {
        // If animating to a particular PanelState, and after completing
        // resizing the Panel to its desired state, then the Panel's state
        // should be updated. This method also is called when an animation
        // is cancelled (which can happen by a subsequent gesture while
        // an animation is happening). That's why the actual height should
        // be checked.
        // TODO(mdjones): Move animations not directly related to the panel's state into their
        // own animation handler (i.e. peek promo, G sprite, etc.). See https://crbug.com/617307.
        if (mAnimatingState != null && mAnimatingState != PanelState.UNDEFINED
                && getHeight() == getPanelHeightFromState(mAnimatingState)) {
            setPanelState(mAnimatingState, mAnimatingStateReason);
        }

        mAnimatingState = PanelState.UNDEFINED;
        mAnimatingStateReason = StateChangeReason.UNKNOWN;
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}
     * and adds it to the animation.
     * Automatically sets the start value at the beginning of the animation.
     */
    public <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime) {
        addToAnimation(object, prop, start, end, duration, startTime, false);
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}
     * and adds it to the animation. Uses a deceleration interpolator by default.
     */
    public <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime, boolean setStartValueAfterDelay) {
        addToAnimation(object, prop, start, end, duration, startTime, setStartValueAfterDelay,
                ChromeAnimation.getDecelerateInterpolator());
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}
     * and adds it to the animation.
     *
     * @param <T>                     The Enum type of the Property being used
     * @param object                  The object being animated
     * @param prop                    The property being animated
     * @param start                   The starting value of the animation
     * @param end                     The ending value of the animation
     * @param duration                The duration of the animation in ms
     * @param startTime               The start time in ms
     * @param setStartValueAfterDelay See {@link Animation#setStartValueAfterStartDelay(boolean)}
     * @param interpolator            The interpolator to use for the animation
     */
    public <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime, boolean setStartValueAfterDelay,
            Interpolator interpolator) {
        ChromeAnimation.Animation<Animatable<?>> component = createAnimation(object, prop, start,
                end, duration, startTime, setStartValueAfterDelay, interpolator);
        addToAnimation(component);
    }

    /**
     * Appends an Animation to the current animation set and starts it immediately.  If the set is
     * already finished or doesn't exist, the animation set is also started.
     */
    protected void addToAnimation(ChromeAnimation.Animation<Animatable<?>> component) {
        if (mLayoutAnimations == null || mLayoutAnimations.finished()) {
            onAnimationStarted();
            mLayoutAnimations = new ChromeAnimation<Animatable<?>>();
            mLayoutAnimations.start();
        }
        component.start();
        mLayoutAnimations.add(component);
        requestUpdate();
    }

    /**
     * @return whether or not the animation is currently being run.
     */
    public boolean animationIsRunning() {
        return mLayoutAnimations != null && !mLayoutAnimations.finished();
    }

    /**
     * Cancels any animation for the given object and property.
     * @param object The object being animated.
     * @param prop   The property to search for.
     */
    public <T extends Enum<?>> void cancelAnimation(Animatable<T> object, T prop) {
        if (mLayoutAnimations != null) {
            mLayoutAnimations.cancel(object, prop);
        }
    }
}
