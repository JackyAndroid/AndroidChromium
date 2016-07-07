// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

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
 * Base abstract class for animating the Contextual Search Panel.
 */
public abstract class ContextualSearchPanelAnimation extends ContextualSearchPanelBase
        implements Animatable<ContextualSearchPanelAnimation.Property> {

    /**
     * Animation properties.
     */
    protected enum Property {
        PANEL_HEIGHT,
        PROMO_VISIBILITY,
        BOTTOM_BAR_TEXT_VISIBILITY
    }

    /**
     * The base duration of animations in milliseconds. This value is based on
     * the Kennedy specification for slow animations.
     */
    static final long BASE_ANIMATION_DURATION_MS = 218;

    /**
     * The maximum animation duration in milliseconds.
     */
    static final long MAXIMUM_ANIMATION_DURATION_MS = 350;

    /**
     * The minimum animation duration in milliseconds.
     */
    private static final long MINIMUM_ANIMATION_DURATION_MS = Math.round(7 * 1000 / 60);

    /**
     * Average animation velocity in dps per second.
     */
    private static final float INITIAL_ANIMATION_VELOCITY_DP_PER_SECOND = 1750f;

    /**
     * The PanelState to which the Panel is being animated.
     */
    private PanelState mAnimatingState;

    /**
     * The StateChangeReason for which the Panel is being animated.
     */
    private StateChangeReason mAnimatingStateReason;

    /**
     * The animation set.
     */
    private ChromeAnimation<Animatable<?>> mLayoutAnimations;

    /**
     * Whether the Promo's acceptance animation is running.
     */
    private boolean mIsAnimatingPromoAcceptance;

    /**
     * The {@link LayoutUpdateHost} used to request a new frame to be updated and rendered.
     */
    private final LayoutUpdateHost mUpdateHost;

    /**
     * Whether the panel's close animation is running.
     */
    private boolean mIsAnimatingPanelClosing;

    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     * @param updateHost The {@link LayoutUpdateHost} used to request updates in the Layout.
     */
    public ContextualSearchPanelAnimation(Context context, LayoutUpdateHost updateHost) {
        super(context);
        mUpdateHost = updateHost;
    }

    // ============================================================================================
    // Animation API
    // ============================================================================================

    /**
     * Notifies that the acceptance animation has finished.
     */
    protected void onPromoAcceptanceAnimationFinished() {
    }

    /**
     * Animates the Contextual Search Panel to its maximized state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void maximizePanel(StateChangeReason reason) {
        animatePanelToState(PanelState.MAXIMIZED, reason);
    }

    /**
     * Animates the Contextual Search Panel to its intermediary state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void expandPanel(StateChangeReason reason) {
        animatePanelToState(PanelState.EXPANDED, reason);
    }

    /**
     * Animates the Contextual Search Panel to its peeked state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected void peekPanel(StateChangeReason reason) {
        // Indicate to the Compositor that for now on the Panel should be
        // rendered, until it's closed.
        startShowing();

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
        if (!mIsAnimatingPanelClosing) {
            if (animate) {
                mIsAnimatingPanelClosing = true;
                animatePanelToState(PanelState.CLOSED, reason);
            } else {
                resizePanelToState(PanelState.CLOSED, reason);
            }
        }
    }

    /**
     * Animates the Contextual Search Panel to a given |state| with a default duration.
     *
     * @param state The state to animate to.
     * @param reason The reason for the change of panel state.
     */
    private void animatePanelToState(PanelState state, StateChangeReason reason) {
        animatePanelToState(state, reason, BASE_ANIMATION_DURATION_MS);
    }

    /**
     * Animates the Contextual Search Panel to a given |state| with a custom |duration|.
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
     * Resizes the Contextual Search Panel to a given |state|.
     *
     * @param state The state to resize to.
     * @param reason The reason for the change of panel state.
     */
    private void resizePanelToState(PanelState state, StateChangeReason reason) {
        cancelHeightAnimation();

        final float height = getPanelHeightFromState(state);
        setPanelHeight(height);
        setPanelState(state, reason);
        requestUpdate();
    }

    // ============================================================================================
    // Animation Helpers
    // ============================================================================================

    @Override
    protected void animatePromoAcceptance() {
        hidePromoView();
        mIsAnimatingPromoAcceptance = true;
        animateProperty(Property.PROMO_VISIBILITY, 1.f, 0.f, BASE_ANIMATION_DURATION_MS);
    }

    @Override
    protected void animateSearchTermResolution() {
        animateProperty(Property.BOTTOM_BAR_TEXT_VISIBILITY, 0.f, 1.f,
                MAXIMUM_ANIMATION_DURATION_MS);
    }

    @Override
    protected void cancelSearchTermResolutionAnimation() {
        if (animationIsRunning()) {
            cancelAnimation(this, Property.BOTTOM_BAR_TEXT_VISIBILITY);
        }
    }

    /**
     * Animates the Panel to its nearest state.
     */
    protected void animateToNearestState() {
        // Calculate the nearest state from the current position, and then calculate the duration
        // of the animation that will start with a desired initial velocity and move the desired
        // amount of dps (displacement).
        final PanelState nearestState = findNearestPanelStateFromHeight(getHeight());
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
        final float kickY = calculateAnimationDisplacement(velocity, BASE_ANIMATION_DURATION_MS);
        final float projectedHeight = getHeight() - kickY;

        // Calculate the projected state the Panel will be at the end of the fling movement and the
        // duration of the animation given the current velocity and the projected displacement.
        PanelState projectedState = findNearestPanelStateFromHeight(projectedHeight);

        // Prevent the fling gesture from moving the Panel from PEEKED to MAXIMIZED if the Panel
        // Promo is available and we are running in full screen panel mode. This is to make sure
        // the Promo will be visible, considering that the EXPANDED state is the only one that will
        // show the Promo in full screen panel mode. In narrow panel UI the Promo is visible in
        // maximized so this project state change is not needed.
        if (projectedState == PanelState.MAXIMIZED
                && getPanelState() == PanelState.PEEKED
                && isPromoVisible() && supportsExpandedState()) {
            projectedState = PanelState.EXPANDED;
        }

        final float displacement = getPanelHeightFromState(projectedState) - getHeight();
        final long duration = calculateAnimationDuration(velocity, displacement);

        animatePanelToState(projectedState, StateChangeReason.FLING, duration);
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
     * Animates the Contextual Search Panel to a given |height| with a custom |duration|.
     *
     * @param height The height to animate to.
     * @param duration The animation duration in milliseconds.
     */
    private void animatePanelTo(float height, long duration) {
        animateProperty(Property.PANEL_HEIGHT, getHeight(), height, duration);
    }

    /**
     * Animates the Contextual Search Panel.
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
        } else if (prop == Property.PROMO_VISIBILITY) {
            setPromoVisibilityForOptInAnimation(value);
        }
    }

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
        if (mIsAnimatingPromoAcceptance) {
            mIsAnimatingPromoAcceptance = false;
            onPromoAcceptanceAnimationFinished();
        }

        if (mIsAnimatingPanelClosing) {
            mIsAnimatingPanelClosing = false;
        }

        // If animating to a particular PanelState, and after completing
        // resizing the Panel to its desired state, then the Panel's state
        // should be updated. This method also is called when an animation
        // is cancelled (which can happen by a subsequent gesture while
        // an animation is happening). That's why the actual height should
        // be checked.
        if (mAnimatingState != PanelState.UNDEFINED
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
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
            float end, long duration, long startTime) {
        addToAnimation(object, prop, start, end, duration, startTime, false);
    }

    /**
     * Creates an {@link org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable}
     * and adds it to the animation. Uses a deceleration interpolator by default.
     */
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
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
    protected <T extends Enum<?>> void addToAnimation(Animatable<T> object, T prop, float start,
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
    protected boolean animationIsRunning() {
        return mLayoutAnimations != null && !mLayoutAnimations.finished();
    }

    /**
     * Cancels any animation for the given object and property.
     * @param object The object being animated.
     * @param prop   The property to search for.
     */
    protected <T extends Enum<?>> void cancelAnimation(Animatable<T> object, T prop) {
        if (mLayoutAnimations != null) {
            mLayoutAnimations.cancel(object, prop);
        }
    }
}
