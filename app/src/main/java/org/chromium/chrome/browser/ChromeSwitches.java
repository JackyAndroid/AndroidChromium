// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * Contains all of the command line switches that are specific to the chrome/
 * portion of Chromium on Android.
 */
public abstract class ChromeSwitches {
    // Switches used from Java.  Please continue switch style used Chrome where
    // options-have-hypens and are_not_split_with_underscores.

    /** Testing: pretend that the switch value is the name of a child account. */
    public static final String CHILD_ACCOUNT = "child-account";

    /** Mimic a low end device */
    public static final String ENABLE_ACCESSIBILITY_TAB_SWITCHER =
            "enable-accessibility-tab-switcher";

    /** Whether fullscreen support is disabled (auto hiding controls, etc...). */
    public static final String DISABLE_FULLSCREEN = "disable-fullscreen";

    /** Show the undo bar for high end UI devices. */
    public static final String ENABLE_HIGH_END_UI_UNDO = "enable-high-end-ui-undo";

    /** Enable toolbar swipe to change tabs in document mode */
    public static final String ENABLE_TOOLBAR_SWIPE_IN_DOCUMENT_MODE =
            "enable-toolbar-swipe-in-document-mode";

    /** Whether instant is disabled. */
    public static final String DISABLE_INSTANT = "disable-instant";

    /** Enables StrictMode violation detection. By default this logs violations to logcat. */
    public static final String STRICT_MODE = "strict-mode";

    /** Don't restore persistent state from saved files on startup. */
    public static final String NO_RESTORE_STATE = "no-restore-state";

    /** Disable the First Run Experience. */
    public static final String DISABLE_FIRST_RUN_EXPERIENCE = "disable-fre";

    /** Force the crash dump to be uploaded regardless of preferences. */
    public static final String FORCE_CRASH_DUMP_UPLOAD = "force-dump-upload";

    /**
     * Force the crash dump NOT to be uploaded regardless of preferences.
     * This is intended for testing use, when command-line switches may be needed.
     * Overrides any other upload preference.
     */
    public static final String DISABLE_CRASH_DUMP_UPLOAD = "disable-dump-upload";

    /** Enable debug logs for the video casting feature. */
    public static final String ENABLE_CAST_DEBUG_LOGS = "enable-cast-debug";

    /** Prevent automatic reconnection to current Cast video when Chrome restarts. */
    public static final String DISABLE_CAST_RECONNECTION = "disable-cast-reconnection";

    /** Whether or not to enable the experimental tablet tab stack. */
    public static final String ENABLE_TABLET_TAB_STACK = "enable-tablet-tab-stack";

    /** Never forward URL requests to external intents. */
    public static final String DISABLE_EXTERNAL_INTENT_REQUESTS =
            "disable-external-intent-requests";

    /** Disable document mode. */
    public static final String DISABLE_DOCUMENT_MODE = "disable-document-mode";

    /** Disable Contextual Search. */
    public static final String DISABLE_CONTEXTUAL_SEARCH = "disable-contextual-search";

    /** Enable Contextual Search. */
    public static final String ENABLE_CONTEXTUAL_SEARCH = "enable-contextual-search";

    /** Enable Contextual Search for instrumentation testing. Not exposed to user. */
    public static final String ENABLE_CONTEXTUAL_SEARCH_FOR_TESTING =
            "enable-contextual-search-for-testing";

    // How many thumbnails should we allow in the cache (per tab stack)?
    public static final String THUMBNAILS = "thumbnails";

    // How many "approximated" thumbnails should we allow in the cache
    // (per tab stack)?  These take very low memory but have poor quality.
    public static final String APPROXIMATION_THUMBNAILS = "approximation-thumbnails";

    /**
     * Disable bottom infobar-like Reader Mode panel.
     */
    public static final String DISABLE_READER_MODE_BOTTOM_BAR = "disable-reader-mode-bottom-bar";

    /**
     * Disable Lo-Fi snackbar.
     */
    public static final String DISABLE_LOFI_SNACKBAR = "disable-lo-fi-snackbar";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Native Switches
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Enable enhanced bookmarks feature.
     * Native switch - switches::kEnhancedBookmarksExperiment
     */
    public static final String ENABLE_ENHANCED_BOOKMARKS = "enhanced-bookmarks-experiment";

    /** Enable the DOM Distiller. */
    public static final String ENABLE_DOM_DISTILLER = "enable-dom-distiller";

    /** Enable experimental web-platform features, such as Push Messaging. */
    public static final String EXPERIMENTAL_WEB_PLAFTORM_FEATURES =
            "enable-experimental-web-platform-features";

    /** Enable Reader Mode button animation. */
    public static final String ENABLE_READER_MODE_BUTTON_ANIMATION =
            "enable-dom-distiller-button-animation";

    /** Enable the native app banners. */
    public static final String ENABLE_APP_INSTALL_ALERTS = "enable-app-install-alerts";

    /**
     * Use sandbox Wallet environment for requestAutocomplete.
     * Native switch - autofill::switches::kWalletServiceUseSandbox.
     */
    public static final String USE_SANDBOX_WALLET_ENVIRONMENT = "wallet-service-use-sandbox";

    /**
     * Change Google base URL.
     * Native switch - switches::kGoogleBaseURL.
     */
    public static final String GOOGLE_BASE_URL = "google-base-url";

    /**
     * Use fake device for Media Stream to replace actual camera and microphone.
     * Native switch - switches::kUseFakeDeviceForMediaStream.
     */
    public static final String USE_FAKE_DEVICE_FOR_MEDIA_STREAM =
            "use-fake-device-for-media-stream";

    /**
     * Disable domain reliability
     * Native switch - switches::kDisableDomainReliability
     */
    public static final String DISABLE_DOMAIN_RELIABILITY = "disable-domain-reliability";

    /**
     * Enable use of Android's built-in spellchecker.
     * Native switch - switches::kEnableAndroidSpellChecker
     */
    public static final String ENABLE_ANDROID_SPELLCHECKER = "enable-android-spellchecker";

    /**
     * Disable speculative TCP/IP preconnection.
     * Native switch - switches::kDisablePreconnect
     */
    public static final String DISABLE_PRECONNECT = "disable-preconnect";

    /**
     * Specifies Android phone page loading progress bar animation.
     * Native switch - switches::kProgressBarAnimation
     */
    public static final String PROGRESS_BAR_ANIMATION = "progress-bar-animation";

    /**
     * Enable offline pages.
     */
    public static final String ENABLE_OFFLINE_PAGES = "enable-offline-pages";

    /**
     * Enable theme-color support in ChromeTabbedActivity.
     */
    public static final String ENABLE_THEME_COLOR_IN_TABBED_MODE =
            "enable-theme-color-in-tabbed-mode";
    /**
     * Enable keyboard accessory view that shows autofill suggestions on top of the keyboard.
     * Native switch - autofill::switches::kEnableAccessorySuggestionView
     */
    public static final String ENABLE_AUTOFILL_KEYBOARD_ACCESSORY =
            "enable-autofill-keyboard-accessory-view";

    /**
     * Enables hung renderer InfoBar activation for unresponsive web content.
     * Native switch - switches::kEnableHungRendererInfoBar
     */
    public static final String ENABLE_HUNG_RENDERER_INFOBAR = "enable-hung-renderer-infobar";

    /**
     * Enable Physical Web feature.
     * Native switch - switches::kEnablePhysicalWeb
     */
    public static final String ENABLE_PHYSICAL_WEB = "enable-physical-web";

    /**
     * Enables Web Notification custom layouts.
     * Native switch - switches::kEnableWebNotificationCustomLayouts
     */
    public static final String ENABLE_WEB_NOTIFICATION_CUSTOM_LAYOUTS =
            "enable-web-notification-custom-layouts";

    /**
     * Disables Web Notification custom layouts.
     * Native switch - switches::kDisableWebNotificationCustomLayouts
     */
    public static final String DISABLE_WEB_NOTIFICATION_CUSTOM_LAYOUTS =
            "disable-web-notification-custom-layouts";

    // Prevent instantiation.
    private ChromeSwitches() {}
}
