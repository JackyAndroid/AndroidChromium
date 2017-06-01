// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

/**
 * Contains various math utilities used throughout Chrome Mobile.
 */
public class MathUtils {

    private MathUtils() {}

    /**
     * Returns the passed in value if it resides within the specified range (inclusive).  If not,
     * it will return the closest boundary from the range.  The ordering of the boundary values does
     * not matter.
     *
     * @param value The value to be compared against the range.
     * @param a First boundary range value.
     * @param b Second boundary range value.
     * @return The passed in value if it is within the range, otherwise the closest boundary value.
     */
    public static int clamp(int value, int a, int b) {
        int min = (a > b) ? b : a;
        int max = (a > b) ? a : b;
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        return value;
    }

    /**
     * Returns the passed in value if it resides within the specified range (inclusive).  If not,
     * it will return the closest boundary from the range.  The ordering of the boundary values does
     * not matter.
     *
     * @param value The value to be compared against the range.
     * @param a First boundary range value.
     * @param b Second boundary range value.
     * @return The passed in value if it is within the range, otherwise the closest boundary value.
     */
    public static long clamp(long value, long a, long b) {
        long min = (a > b) ? b : a;
        long max = (a > b) ? a : b;
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        return value;
    }

    /**
     * Returns the passed in value if it resides within the specified range (inclusive).  If not,
     * it will return the closest boundary from the range.  The ordering of the boundary values does
     * not matter.
     *
     * @param value The value to be compared against the range.
     * @param a First boundary range value.
     * @param b Second boundary range value.
     * @return The passed in value if it is within the range, otherwise the closest boundary value.
     */
    public static float clamp(float value, float a, float b) {
        float min = (a > b) ? b : a;
        float max = (a > b) ? a : b;
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        return value;
    }

    /**
     * Computes a%b that is positive. Note that result of % operation is not always positive.
     * @return a%b >= 0 ? a%b : a%b + b
     */
    public static int positiveModulo(int a, int b) {
        int mod = a % b;
        return mod >= 0 ? mod : mod + b;
    }

    /**
     * Moves {@code value} forward to {@code target} based on {@code speed}.
     * @param value  The current value.
     * @param target The target value.
     * @param speed  How far to move {@code value} to {@code target}.  0 doesn't move it at all.  1
     *               moves it to {@code target}.
     * @return       The new interpolated value.
     */
    public static float interpolate(float value, float target, float speed) {
        return (value + (target - value) * speed);
    }

    /**
     * Smooth a value between 0 and 1.
     * @param t The value to smooth.
     * @return  The smoothed value between 0 and 1.
     */
    public static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Scales the provided dimension such that it is just large enough to fit
     * the target width and height.
     *
     * @param dimensions The dimensions to scale
     * @param targetWidth The target width
     * @param targetHeight The target height
     * @return The scale factor applied to dimensions
     */
    public static float scaleToFitTargetSize(
            int [] dimensions, int targetWidth, int targetHeight) {
        if (dimensions.length < 2 || dimensions[0] <= 0 || dimensions[1] <= 0) {
            throw new IllegalArgumentException(
                    "Expected dimensions to have length >= 2 && dimensions[0] > 0 && "
                    + "dimensions[1] > 0");
        }
        float scale = Math.max(
                (float) targetWidth / dimensions[0],
                (float) targetHeight / dimensions[1]);
        dimensions[0] *= scale;
        dimensions[1] *= scale;
        return scale;
    }

    /**
     * Flips {@code value} iff {@code flipSign} is {@code true}.
     * @param value    The value to flip.
     * @param flipSign Whether or not to flip the value.
     * @return         {@code value} iff {@code flipSign} is {@code false}, otherwise negative
     *                 {@code value}.
     */
    public static int flipSignIf(int value, boolean flipSign) {
        return flipSign ? -value : value;
    }

    /**
     * Flips {@code value} iff {@code flipSign} is {@code true}.
     * @param value    The value to flip.
     * @param flipSign Whether or not to flip the value.
     * @return         {@code value} iff {@code flipSign} is {@code false}, otherwise negative
     *                 {@code value}.
     */
    public static float flipSignIf(float value, boolean flipSign) {
        return flipSign ? -value : value;
    }

    /**
     * Compares two long values. Same as {@link Long#compare}, but available on all API levels.
     *
     * TODO(newt): replace this with Long.compare() once Chrome only supports API level 19+.
     */
    public static int compareLongs(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }
}
