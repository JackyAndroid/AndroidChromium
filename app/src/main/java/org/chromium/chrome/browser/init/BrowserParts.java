// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

/**
 * Interface that any {@link AsyncInitializationActivity} can use to interact with this delegate
 * during start up. Functions called by
 * {@link ChromeBrowserInitializer#handlePreNativeStartup(BrowserParts)} are called in the order
 * they are listed.
 */
public interface BrowserParts {
    /**
     * Called during {@link ChromeBrowserInitializer#handlePreNativeStartup(BrowserParts)}.
     * This should consist of java only calls that will not take too much time.
     */
    void preInflationStartup();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePreNativeStartup(BrowserParts)}.
     * It should include a call to setContentView and also should start loading libraries using
     * {@link NativeInitializationController#startBackgroundTasks()}
     */
    void setContentViewAndLoadLibrary();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePreNativeStartup(BrowserParts)}.
     * Early setup after the view hierarchy has been inflated and the background tasks has been
     * initialized. No native calls.
     */
    void postInflationStartup();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePostNativeStartup(BrowserParts)}.
     * Optionaly preconnect to the URL specified in the launch intent, if any. The
     * preconnection is done asynchronously in the native library.
     */
    void maybePreconnect();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePostNativeStartup(BrowserParts)}.
     * Initialize the compositor related classes.
     */
    void initializeCompositor();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePostNativeStartup(BrowserParts)}.
     * Initialize the tab state restoring tabs or creating new tabs.
     */
    void initializeState();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePostNativeStartup(BrowserParts)}.
     * Carry out remaining activity specific tasks for initialization
     */
    void finishNativeInitialization();

    /**
     * Called during {@link ChromeBrowserInitializer#handlePostNativeStartup(BrowserParts)} if
     * there was an error during startup.
     */
    void onStartupFailure();

    /**
     * @return Whether the activity this delegate represents has been destoyed.
     */
    boolean isActivityDestroyed();

    /**
     * @return Whether the activity is marked itself to be closed.
     */
    boolean isActivityFinishing();

    /**
     * @return Whether GPU process needs to be started during the startup.
     */
    boolean shouldStartGpuProcess();
}
