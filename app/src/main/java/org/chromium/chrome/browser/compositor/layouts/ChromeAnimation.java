// Copyright 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.os.SystemClock;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class to handle simple animation sets.  This can animate any object passed in by overriding
 * the ChromeAnimation.Animation object.
 *
 * @param <T> The type of Object being animated by this ChromeAnimation set.
 */
@SuppressWarnings("unchecked")
public class ChromeAnimation<T> {
    /**
     * The amount we jump into the animation for the first frame.  We do this as we assume
     * the object be animated is already resting in the initial value specified.  To avoid wasting
     * a frame of animation on something that will look exactly the same, we jump into the
     * animation by this frame offset (calculated by the desired time required to draw one frame
     * at 60 FPS).
     */
    private static final int FIRST_FRAME_OFFSET_MS = 1000 / 60;

    /**
     * Can be used to slow down created animations for debugging purposes.
     */
    private static final int ANIMATION_MULTIPLIER = 1;

    private final AtomicBoolean mFinishCalled = new AtomicBoolean();
    private final ArrayList<Animation<T>> mAnimations = new ArrayList<Animation<T>>();
    private long mCurrentTime;

    // Keep a reference to one of each standard interpolator to avoid allocations.
    private static AccelerateInterpolator sAccelerateInterpolator;
    private static LinearInterpolator sLinearInterpolator;
    private static DecelerateInterpolator sDecelerateInterpolator;
    private static final Object sLock = new Object();

    /**
     * @return The default acceleration interpolator. No allocation.
     */
    public static AccelerateInterpolator getAccelerateInterpolator() {
        synchronized (sLock) {
            if (sAccelerateInterpolator == null) {
                sAccelerateInterpolator = new AccelerateInterpolator();
            }
        }
        return sAccelerateInterpolator;
    }

    /**
     * @return The default linear interpolator. No allocation.
     */
    public static LinearInterpolator getLinearInterpolator() {
        synchronized (sLock) {
            if (sLinearInterpolator == null) {
                sLinearInterpolator = new LinearInterpolator();
            }
        }
        return sLinearInterpolator;
    }

    /**
     * @return The default deceleration interpolator. No allocation.
     */
    public static DecelerateInterpolator getDecelerateInterpolator() {
        synchronized (sLock) {
            if (sDecelerateInterpolator == null) {
                sDecelerateInterpolator = new DecelerateInterpolator();
            }
        }
        return sDecelerateInterpolator;
    }

    /**
     * Adds a ChromeAnimation.Animation instance to this ChromeAnimation set.  This Animation will
     * be managed by this ChromeAnimation from now on.
     *
     * @param a The ChromeAnimation.Animation object to be controlled and updated by this
     *         ChromeAnimation.
     */
    public void add(ChromeAnimation.Animation<T> a) {
        mAnimations.add(a);
    }

    /**
     * Starts all of the Animation instances in this ChromeAnimation.  This sets up the appropriate
     * state so that calls to update() can properly track how much time has passed and set the
     * initial values for each Animation.
     */
    public void start() {
        mFinishCalled.set(false);
        mCurrentTime = 0;
        for (int i = 0; i < mAnimations.size(); ++i) {
            Animation<T> a = mAnimations.get(i);
            a.start();
        }
    }

    /**
     * Aborts all animations of a specific type. This does not call finish on the animation.
     * So the animation will not reach its final value.
     * @param object   object to find animations to be aborted. If null, matches all the animations.
     * @param property property to find animations to be aborted.
     */
    public <V extends Enum<?>> void cancel(T object, V property) {
        for (int  i = mAnimations.size() - 1; i >= 0; i--) {
            Animation<T> animation = mAnimations.get(i);
            if ((object == null || animation.getAnimatedObject() == object)
                    && animation.checkProperty(property)) {
                mAnimations.remove(i);
            }
        }
    }

    /**
     * Forces each Animation to finish itself, setting the properties to the final value of the
     * Animation.
     */
    public void updateAndFinish() {
        for (int i = 0; i < mAnimations.size(); ++i) {
            mAnimations.get(i).updateAndFinish();
        }
        finishInternal();
    }

    /**
     * Updates each Animation based on how much time has passed.  Each animation gets passed the
     * delta since the last call to update() and can appropriately interpolate their values. The
     * time reference is implicitly the actual uptime of the app.
     *
     * @return Whether or not this ChromeAnimation is finished animating.
     */
    public boolean update() {
        return update(SystemClock.uptimeMillis());
    }

    /**
     * Updates each Animation based on how much time has passed.  Each animation gets passed the
     * delta since the last call to update() and can appropriately interpolate their values.
     *
     * @param time The current time of the app in ms.
     * @return     Whether or not this ChromeAnimation is finished animating.
     */
    public boolean update(long time) {
        if (mFinishCalled.get()) {
            return true;
        }
        if (mCurrentTime == 0) mCurrentTime = time - FIRST_FRAME_OFFSET_MS;
        long dtMs = time - mCurrentTime;
        mCurrentTime += dtMs;
        boolean finished = true;
        for (int i = 0; i < mAnimations.size(); ++i) {
            mAnimations.get(i).update(dtMs);
            finished &= mAnimations.get(i).finished();
        }

        if (finished) {
            updateAndFinish();
        }
        return false;
    }

    /**
     * @return Whether or not this ChromeAnimation is finished animating.
     */
    public boolean finished() {
        if (mFinishCalled.get()) {
            return true;
        }

        for (int i = 0; i < mAnimations.size(); ++i) {
            if (!mAnimations.get(i).finished()) {
                return false;
            }
        }

        return true;
    }

    private void finishInternal() {
        if (mFinishCalled.get()) return;

        finish();
        mFinishCalled.set(true);
    }

    /**
     * Callback to handle any necessary cleanups upon finishing the animation.
     *
     * <p>
     * Called as part of {@link #update()} if the end of the animation is reached or
     * {@link #updateAndFinish()}.
     */
    protected void finish() {
    }

    /**
     * A particular animation instance, meant to be managed by a ChromeAnimation object.
     *
     * @param <T> The type of Object being animated by this Animation instance.  This object should
     *         be accessed inside setProperty() where it can be manipulated by the new value.
     */
    public abstract static class Animation<T> {
        protected T mAnimatedObject;
        private float mStart;
        private float mEnd;

        private long mCurrentTime;
        private long mDuration;
        private long mStartDelay;
        private boolean mDelayStartValue;
        private Interpolator mInterpolator = getDecelerateInterpolator();

        /**
         * Creates a new Animation object with a custom Interpolator.
         *
         * @param t The object to be Animated.
         * @param start The starting value of the animation.
         * @param end The ending value of the animation.
         * @param duration The duration of the animation.  This does not include the startTime.
         *                 The duration must be strictly positive.
         * @param startTime The time at which this animation should start.
         * @param interpolator The Interpolator instance to use for animating the property from
         *         start to finish.
         */
        public Animation(T t, float start, float end, long duration,
                long startTime, Interpolator interpolator) {
            this(t, start, end, duration, startTime);
            mInterpolator = interpolator;
        }

        /**
         * Creates a new Animation object.
         *
         * @param t The object to be Animated.
         * @param start The starting value of the animation.
         * @param end The ending value of the animation.
         * @param duration The duration of the animation.  This does not include the startTime.
         *                 The duration must be strictly positive.
         * @param startTime The time at which this animation should start.
         */
        public Animation(T t, float start, float end, long duration,
                long startTime) {
            assert duration > 0;
            mAnimatedObject = t;
            mStart = start;
            mEnd = end;
            mDuration = duration * ANIMATION_MULTIPLIER;
            mStartDelay = startTime * ANIMATION_MULTIPLIER;
            mCurrentTime = 0;
        }

        /**
         * Returns the object being animated.
         */
        protected T getAnimatedObject() {
            return mAnimatedObject;
        }

        /**
         * @param delayStartValue Whether to delay setting the animation's initial value until
         *        after the specified start delay (Default = false).
         */
        public void setStartValueAfterStartDelay(boolean delayStartValue) {
            mDelayStartValue = delayStartValue;
        }

        /**
         * Sets the internal timer to be at the end of the animation and calls setProperty with the
         * final value.
         */
        public void updateAndFinish() {
            mCurrentTime = mDuration + mStartDelay;
            setProperty(mEnd);
        }

        /**
         * Updates the internal timer based on the time delta passed in.  The proper property value
         * is interpolated and passed to setProperty.
         *
         * @param dtMs The amount of time, in milliseconds, that this animation should be
         *         progressed.
         */
        public void update(long dtMs) {
            mCurrentTime += dtMs;

            // Bound our time here so that our scale never goes above 1.0.
            mCurrentTime = Math.min(mCurrentTime, mDuration + mStartDelay);

            if (mDelayStartValue && mCurrentTime < mStartDelay) {
                return;
            }

            // Figure out the relative fraction of time we need to animate.
            long relativeTime = Math.max(0, Math.min(mCurrentTime - mStartDelay,
                    mDuration));

            setProperty(mStart + (mEnd - mStart)
                    * mInterpolator.getInterpolation((float) relativeTime / (float) mDuration));
        }

        /**
         * Starts the animation and calls setProperty() with the initial value.
         */
        public void start() {
            mCurrentTime = 0;
            update(0);
        }

        /**
         * @return Whether or not this current animation is finished.
         */
        public boolean finished() {
            if (mCurrentTime >= mDuration + mStartDelay) {
                return true;
            }
            return false;
        }

        /**
         * Checks if the given property is being animated.
         */
        public <V extends Enum<?>> boolean checkProperty(V prop) {
            return true;
        }

        /**
         * The abstract method that gets called with the new interpolated value based on the
         * current time.  This gives inheriting classes the chance to set a property on the
         * animating object.
         *
         * @param p The current animated value based on the current time and the Interpolator.
         */
        public abstract void setProperty(float p);
    }

    /**
     * Provides a interface for updating animatible properties.
     *
     * @param <T> The {@link Enum} of animatable properties.
     */
    public static interface Animatable<T extends Enum<?>> {

        /**
         * Updates an animatable property.
         *
         * @param prop The property to update
         * @param val The new value
         */
        public void setProperty(T prop, float val);

    }

    /**
     * An animation that can be applied to {@link ChromeAnimation.Animatable} objects.
     *
     * @param <V> The type of {@link ChromeAnimation.Animatable} object to animate.
     */
    public static class AnimatableAnimation<V extends Enum<?>> extends
            Animation<Animatable<V>> {

        private final V mProperty;

        /**
         * @param animatable The object being animated
         * @param property The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation.  This does not include the startTime.
         * @param startTime The time at which this animation should start.
         * @param interpolator The interpolator to use for the animation
         */
        public AnimatableAnimation(Animatable<V> animatable, V property, float start, float end,
                long duration, long startTime, Interpolator interpolator) {
            super(animatable, start, end, duration, startTime, interpolator);
            mProperty = property;
        }

        @Override
        public void setProperty(float p) {
            mAnimatedObject.setProperty(mProperty, p);
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param <T> The Enum type of the Property being used
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         */
        public static <T extends Enum<?>> void addAnimation(ChromeAnimation<Animatable<?>> set,
                Animatable<T> object, T prop, float start, float end, long duration,
                long startTime) {
            addAnimation(set, object, prop, start, end, duration, startTime, false);
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param <T> The Enum type of the Property being used
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         */
        public static <T extends Enum<?>> void addAnimation(ChromeAnimation<Animatable<?>> set,
                Animatable<T> object, T prop, float start, float end, long duration, long startTime,
                boolean setStartValueAfterStartDelay) {
            addAnimation(set, object, prop, start, end, duration, startTime,
                    setStartValueAfterStartDelay, getDecelerateInterpolator());
        }

        /**
         * Helper method to add an {@link ChromeAnimation.AnimatableAnimation}
         * to a {@link ChromeAnimation}
         *
         * @param <T> The Enum type of the Property being used
         * @param set The set to add the animation to
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         * @param interpolator The interpolator to use for the animation
         */
        public static <T extends Enum<?>> void addAnimation(ChromeAnimation<Animatable<?>> set,
                Animatable<T> object, T prop, float start, float end, long duration, long startTime,
                boolean setStartValueAfterStartDelay, Interpolator interpolator) {
            if (duration <= 0) return;
            Animation<Animatable<?>> animation = createAnimation(object, prop, start, end,
                    duration, startTime, setStartValueAfterStartDelay, interpolator);
            set.add(animation);
        }

        /**
         * Helper method to create an {@link ChromeAnimation.AnimatableAnimation}
         *
         * @param <T> The Enum type of the Property being used
         * @param object The object being animated
         * @param prop The property being animated
         * @param start The starting value of the animation
         * @param end The ending value of the animation
         * @param duration The duration of the animation in ms
         * @param startTime The start time in ms
         * @param setStartValueAfterStartDelay See
         *            {@link ChromeAnimation.Animation#setStartValueAfterStartDelay(boolean)}
         * @param interpolator The interpolator to use for the animation
         */
        public static <T extends Enum<?>> Animation<Animatable<?>> createAnimation(
                Animatable<T> object, T prop, float start, float end, long duration,
                long startTime, boolean setStartValueAfterStartDelay, Interpolator interpolator) {
            Animation<Animatable<?>> animation = new AnimatableAnimation(object, prop, start, end,
                    duration, startTime, interpolator);
            animation.setStartValueAfterStartDelay(setStartValueAfterStartDelay);
            return animation;
        }

        /**
         * Checks if the given property is being animated.
         */
        @Override
        public <V extends Enum<?>> boolean checkProperty(V prop) {
            return mProperty == prop;
        }
    }
}
