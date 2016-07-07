// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.WebContents;

/**
 * A helper class for using the DOM Distiller.
 */
@JNINamespace("android")
public class DomDistillerTabUtils {

    private DomDistillerTabUtils() {
    }

    /**
     * Creates a new WebContents and navigates the {@link WebContents} to view the URL of the
     * current page, while in the background starts distilling the current page. This method takes
     * ownership over the old WebContents after swapping in the new one.
     *
     * @param webContents the WebContents to distill.
     */
    public static void distillCurrentPageAndView(WebContents webContents) {
        nativeDistillCurrentPageAndView(webContents);
    }

    /**
     * Starts distillation in the source @{link WebContents} while navigating the destination
     * {@link WebContents} to view the distilled content. This does not take ownership of any
     * of the WebContents.
     *
     * @param sourceWebContents the WebContents to distill.
     * @param destinationWebContents the WebContents to display the distilled content in.
     */
    public static void distillAndView(
            WebContents sourceWebContents, WebContents destinationWebContents) {
        nativeDistillAndView(sourceWebContents, destinationWebContents);
    }
    /**
     * Returns the formatted version of the original URL of a distillation, given the original URL.
     *
     * @param url The original URL.
     * @return the formatted URL of the original page.
     */
    public static String getFormattedUrlFromOriginalDistillerUrl(String url) {
        return nativeGetFormattedUrlFromOriginalDistillerUrl(url);
    }

    /**
     * Detect if any heuristic is being used to determine if a page is distillable. On the native
     * side, this is testing if the heuristic is not "NONE".
     *
     * @return True if heuristics are being used to detect distillable pages.
     */
    public static boolean isDistillerHeuristicsEnabled() {
        return nativeIsDistillerHeuristicsEnabled();
    }

    private static native void nativeDistillCurrentPageAndView(WebContents webContents);
    private static native void nativeDistillAndView(
            WebContents sourceWebContents, WebContents destinationWebContents);
    private static native String nativeGetFormattedUrlFromOriginalDistillerUrl(String url);
    private static native boolean nativeIsDistillerHeuristicsEnabled();
}
