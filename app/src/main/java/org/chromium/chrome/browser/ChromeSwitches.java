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

    /** Mimic a low end device */
    public static final String ENABLE_ACCESSIBILITY_TAB_SWITCHER =
            "enable-accessibility-tab-switcher";

    /** Whether fullscreen support is disabled (auto hiding controls, etc...). */
    public static final String DISABLE_FULLSCREEN = "disable-fullscreen";

    /** Whether instant is disabled. */
    public static final String DISABLE_INSTANT = "disable-instant";

    /** Enables StrictMode violation detection. By default this logs violations to logcat. */
    public static final String STRICT_MODE = "strict-mode";

    /** Don't restore persistent state from saved files on startup. */
    public static final String NO_RESTORE_STATE = "no-restore-state";

    /** Disable the First Run Experience. */
    public static final String DISABLE_FIRST_RUN_EXPERIENCE = "disable-fre";

    /** Enable the Lightweight First Run Experience. */
    public static final String ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE = "enable-lightweight-fre";

    /** Force the crash dump to be uploaded regardless of preferences. */
    public static final String FORCE_CRASH_DUMP_UPLOAD = "force-dump-upload";

    /**
     * Force the crash dump NOT to be uploaded regardless of preferences.
     * This is intended for testing use, when command-line switches may be needed.
     * Overrides any other upload preference.
     */
    public static final String DISABLE_CRASH_DUMP_UPLOAD = "disable-dump-upload";

    /** Whether or not to enable the experimental tablet tab stack. */
    public static final String ENABLE_TABLET_TAB_STACK = "enable-tablet-tab-stack";

    /** Never forward URL requests to external intents. */
    public static final String DISABLE_EXTERNAL_INTENT_REQUESTS =
            "disable-external-intent-requests";

    /** Disable Contextual Search. */
    public static final String DISABLE_CONTEXTUAL_SEARCH = "disable-contextual-search";

    /** Enable Contextual Search. */
    public static final String ENABLE_CONTEXTUAL_SEARCH = "enable-contextual-search";

    /** Contextual Search UI integration with Contextual Cards data.*/
    public static final String CONTEXTUAL_SEARCH_CONTEXTUAL_CARDS_BAR_INTEGRATION =
            "cs-contextual-cards-bar-integration";

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

    /**
     * Forces the update menu item to show.
     */
    public static final String FORCE_SHOW_UPDATE_MENU_ITEM = "force-show-update-menu-item";

    /**
     * Forces the update menu badge to show.
     */
    public static final String FORCE_SHOW_UPDATE_MENU_BADGE = "force-show-update-menu-badge";

    /**
     * Sets the market URL for Chrome for use in testing.
     */
    public static final String MARKET_URL_FOR_TESTING = "market-url-for-testing";

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Native Switches
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Enable the DOM Distiller. */
    public static final String ENABLE_DOM_DISTILLER = "enable-dom-distiller";

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
     * Specifies Android phone page loading progress bar animation.
     * Native switch - switches::kProgressBarAnimation
     */
    public static final String PROGRESS_BAR_ANIMATION = "progress-bar-animation";

    /**
     * Specifies Android NTP behaviour on clicking a Most{Visited/Likely} tile.
     * Specifically whether to refocus an existing tab with the same url or host or to load the url
     * in the current tab.
     * Native switch - switches::kNtpSwitchToExistingTab
     */
    public static final String NTP_SWITCH_TO_EXISTING_TAB = "ntp-switch-to-existing-tab";

    /**
     * Enable keyboard accessory view that shows autofill suggestions on top of the keyboard.
     * Native switch - autofill::switches::kEnableAccessorySuggestionView
     */
    public static final String ENABLE_AUTOFILL_KEYBOARD_ACCESSORY =
            "enable-autofill-keyboard-accessory-view";

    /**
     * Enables overscroll of the on screen keyboard. With this flag on, the OSK will only resize the
     * visual viewport.
     * Native switch - switches::kEnableOSKOverscroll
     */
    public static final String ENABLE_OSK_OVERSCROLL = "enable-osk-overscroll";

    /**
     * Enables hung renderer InfoBar activation for unresponsive web content.
     * Native switch - switches::kEnableHungRendererInfoBar
     */
    public static final String ENABLE_HUNG_RENDERER_INFOBAR = "enable-hung-renderer-infobar";

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

    /**
     * Determines which of the Herb prototypes is being tested.
     * See about:flags for descriptions.
     */
    public static final String HERB_FLAVOR_DISABLED_SWITCH =
            "tab-management-experiment-type-disabled";
    public static final String HERB_FLAVOR_ELDERBERRY_SWITCH =
            "tab-management-experiment-type-elderberry";

    public static final String HERB_FLAVOR_DEFAULT = "Default";
    public static final String HERB_FLAVOR_CONTROL = "Control";
    public static final String HERB_FLAVOR_DISABLED = "Disabled";
    public static final String HERB_FLAVOR_ELDERBERRY = "Elderberry";

    public static final String DISABLE_APP_LINK = "disable-app-link";
    public static final String ENABLE_APP_LINK = "enable-app-link";

    /**
     * Set the partner-defined homepage URL, for testing.
     */
    public static final String PARTNER_HOMEPAGE_FOR_TESTING = "partner-homepage-for-testing";

    /**
     * Enables "Add to Home screen" to mint a WebApk.
     */
    public static final String ENABLE_WEBAPK = "enable-webapk";

    /**
     * Forces the WebAPK runtime dex to be extracted each time that Chrome is started.
     */
    public static final String ALWAYS_EXTRACT_WEBAPK_RUNTIME_DEX_ON_STARTUP =
            "always-extract-webapk-dex-on-startup";

    // Prevent instantiation.
    private ChromeSwitches() {}
}
