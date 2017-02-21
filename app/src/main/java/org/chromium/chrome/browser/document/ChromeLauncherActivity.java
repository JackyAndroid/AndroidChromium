// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Browser;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.CommandLineInitUtil;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.SeparateTaskCustomTabActivity;
import org.chromium.chrome.browser.firstrun.FirstRunActivity;
import org.chromium.chrome.browser.firstrun.FirstRunFlowSequencer;
import org.chromium.chrome.browser.firstrun.LightweightFirstRunActivity;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.MediaNotificationUma;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.notifications.NotificationPlatformBridge;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.DocumentModeAssassin;
import org.chromium.chrome.browser.upgrade.UpgradeActivity;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.webapps.ActivityAssigner;
import org.chromium.chrome.browser.webapps.WebappLauncherActivity;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Dispatches incoming intents to the appropriate activity based on the current configuration and
 * Intent fired.
 */
public class ChromeLauncherActivity extends Activity
        implements IntentHandler.IntentHandlerDelegate {
    /**
     * Extra indicating launch mode used.
     */
    public static final String EXTRA_LAUNCH_MODE =
            "com.google.android.apps.chrome.EXTRA_LAUNCH_MODE";

    /**
     * Whether or not the toolbar should indicate that a tab was spawned by another Activity.
     */
    public static final String EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT =
            "org.chromium.chrome.browser.document.IS_ALLOWED_TO_RETURN_TO_PARENT";

    private static final String TAG = "document_CLActivity";

    /**
     * Timeout in ms for reading PartnerBrowserCustomizations provider. We do not trust third party
     * provider by default.
     */
    private static final int PARTNER_BROWSER_CUSTOMIZATIONS_TIMEOUT_MS = 10000;

    private static final LaunchMetrics.SparseHistogramSample sIntentFlagsHistogram =
            new LaunchMetrics.SparseHistogramSample("Launch.IntentFlags");

    private IntentHandler mIntentHandler;
    private boolean mIsInLegacyMultiInstanceMode;

    private boolean mIsCustomTabIntent;
    private boolean mIsHerbIntent;

    /** When started with an intent, maybe pre-resolve the domain. */
    private void maybePrefetchDnsInBackground() {
        if (getIntent() != null && Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            String maybeUrl = IntentHandler.getUrlFromIntent(getIntent());
            if (maybeUrl != null) {
                WarmupManager.getInstance().maybePrefetchDnsForUrlInBackground(this, maybeUrl);
            }
        }
    }

    /**
     * Figure out how to route the Intent.  Because this is on the critical path to startup, please
     * avoid making the pathway any more complicated than it already is.  Make sure that anything
     * you add _absolutely has_ to be here.
     */
    @Override
    @SuppressLint("MissingSuperCall") // Called in doOnCreate.
    public void onCreate(Bundle savedInstanceState) {
        // Third-party code adds disk access to Activity.onCreate. http://crbug.com/619824
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        TraceEvent.begin("ChromeLauncherActivity");
        TraceEvent.begin("ChromeLauncherActivity.onCreate");
        try {
            doOnCreate(savedInstanceState);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
            TraceEvent.end("ChromeLauncherActivity.onCreate");
        }
    }

    private final void doOnCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This Activity is only transient. It launches another activity and
        // terminates itself. However, some of the work is performed outside of
        // {@link Activity#onCreate()}. To capture this, the TraceEvent starts
        // in onCreate(), and ends in onPause().
        // Needs to be called as early as possible, to accurately capture the
        // time at which the intent was received.
        IntentHandler.addTimestampToIntent(getIntent());
        // Initialize the command line in case we've disabled document mode from there.
        CommandLineInitUtil.initCommandLine(this, ChromeApplication.COMMAND_LINE_FILE);

        // Read partner browser customizations information asynchronously.
        // We want to initialize early because when there is no tabs to restore, we should possibly
        // show homepage, which might require reading PartnerBrowserCustomizations provider.
        PartnerBrowserCustomizations.initializeAsync(getApplicationContext(),
                PARTNER_BROWSER_CUSTOMIZATIONS_TIMEOUT_MS);
        recordIntentMetrics();

        mIsInLegacyMultiInstanceMode =
                MultiWindowUtils.getInstance().shouldRunInLegacyMultiInstanceMode(this);
        mIntentHandler = new IntentHandler(this, getPackageName());
        mIsCustomTabIntent = isCustomTabIntent(getIntent());
        if (!mIsCustomTabIntent) {
            mIsHerbIntent = isHerbIntent();
            mIsCustomTabIntent = mIsHerbIntent;
        }

        Intent intent = getIntent();
        int tabId = IntentUtils.safeGetIntExtra(intent,
                TabOpenType.BRING_TAB_TO_FRONT.name(), Tab.INVALID_TAB_ID);
        boolean incognito = intent.getBooleanExtra(
                IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, false);

        // Check if this intent was fired at the end of the first run experience and return if
        // first run was not successfully completed.
        boolean firstRunActivityResult = IntentUtils.safeGetBooleanExtra(intent,
                FirstRunActivity.EXTRA_FIRST_RUN_ACTIVITY_RESULT, false);
        boolean firstRunComplete = IntentUtils.safeGetBooleanExtra(intent,
                FirstRunActivity.EXTRA_FIRST_RUN_COMPLETE, false);
        if (firstRunActivityResult && !firstRunComplete) {
            finish();
            return;
        }

        // Check if a web search Intent is being handled.
        String url = IntentHandler.getUrlFromIntent(intent);
        if (url == null && tabId == Tab.INVALID_TAB_ID
                && !incognito && mIntentHandler.handleWebSearchIntent(intent)) {
            finish();
            return;
        }

        // Check if a LIVE WebappActivity has to be brought back to the foreground.  We can't
        // check for a dead WebappActivity because we don't have that information without a global
        // TabManager.  If that ever lands, code to bring back any Tab could be consolidated
        // here instead of being spread between ChromeTabbedActivity and ChromeLauncherActivity.
        // https://crbug.com/443772, https://crbug.com/522918
        if (WebappLauncherActivity.bringWebappToFront(tabId)) {
            ApiCompatibilityUtils.finishAndRemoveTask(this);
            return;
        }

        // The notification settings cog on the flipped side of Notifications and in the Android
        // Settings "App Notifications" view will open us with a specific category.
        if (intent.hasCategory(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES)) {
            NotificationPlatformBridge.launchNotificationPreferences(this, getIntent());
            finish();
            return;
        }

        // Check if we should launch an Instant App to handle the intent.
        if (InstantAppsHandler.getInstance().handleIncomingIntent(
                this, intent, mIsCustomTabIntent && !mIsHerbIntent)) {
            finish();
            return;
        }

        // Check if we should launch the ChromeTabbedActivity.
        if (!mIsCustomTabIntent && !FeatureUtilities.isDocumentMode(this)) {
            boolean checkedFre = false;
            if (CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)) {
                // Launch the First Run Experience for VIEW Intents with URLs before launching
                // ChromeTabbedActivity if necessary.
                if (getIntent() != null && getIntent().getAction() == Intent.ACTION_VIEW
                        && IntentHandler.getUrlFromIntent(getIntent()) != null) {
                    if (launchFirstRunExperience(true)) {
                        finish();
                        return;
                    }
                    checkedFre = true;
                }
            }
            launchTabbedMode(checkedFre);
            finish();
            return;
        }

        // Check if we should launch the FirstRunActivity.  This occurs after the check to launch
        // ChromeTabbedActivity because ChromeTabbedActivity handles FRE in its own way.
        if (launchFirstRunExperience(false)) {
            finish();
            return;
        }

        // Check if we should launch a Custom Tab.
        if (mIsCustomTabIntent) {
            launchCustomTabActivity();
            finish();
            return;
        }

        // Force a user to migrate to document mode, if necessary.
        if (DocumentModeAssassin.getInstance().isMigrationNecessary()) {
            Log.d(TAG, "Diverting to UpgradeActivity via ChromeLauncherActivity.");
            UpgradeActivity.launchInstance(this, intent);
            ApiCompatibilityUtils.finishAndRemoveTask(this);
            return;
        }

        // All possible bounces to other activities should have already been enumerated above.
        Log.e(TAG, "User wasn't sent to another Activity.");
        assert false;
        ApiCompatibilityUtils.finishAndRemoveTask(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TraceEvent.end("ChromeLauncherActivity");
    }

    @Override
    public void processWebSearchIntent(String query) {
        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, query);
        startActivity(searchIntent);
    }

    @Override
    public void processUrlViewIntent(String url, String referer, String headers,
            IntentHandler.TabOpenType tabOpenType, String externalAppId,
            int tabIdToBringToFront, boolean hasUserGesture, Intent intent) {
        assert false;
    }

    /**
     * @return Whether or not an Herb prototype may hijack an Intent.
     */
    public static boolean canBeHijackedByHerb(Intent intent) {
        String url = IntentHandler.getUrlFromIntent(intent);

        // Only VIEW Intents with URLs are rerouted to Custom Tabs.
        if (intent == null || !TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())
                || TextUtils.isEmpty(url)) {
            return false;
        }

        // Don't open explicitly opted out intents in custom tabs.
        if (CustomTabsIntent.shouldAlwaysUseBrowserUI(intent)) {
            return false;
        }

        // Don't reroute Chrome Intents.
        Context context = ContextUtils.getApplicationContext();
        if (TextUtils.equals(context.getPackageName(),
                IntentUtils.safeGetStringExtra(intent, Browser.EXTRA_APPLICATION_ID))
                || IntentHandler.wasIntentSenderChrome(intent, context)) {
            return false;
        }

        // Don't reroute internal chrome URLs.
        try {
            URI uri = URI.create(url);
            if (UrlUtilities.isInternalScheme(uri)) return false;
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Don't reroute Home screen shortcuts.
        if (IntentUtils.safeHasExtra(intent, ShortcutHelper.EXTRA_SOURCE)) {
            return false;
        }

        return true;
    }

    /**
     * @return Whether or not a Custom Tab will be forcefully used for the incoming Intent.
     */
    private boolean isHerbIntent() {
        if (!canBeHijackedByHerb(getIntent())) return false;

        // Different Herb flavors handle incoming intents differently.
        String flavor = FeatureUtilities.getHerbFlavor();
        if (TextUtils.isEmpty(flavor)
                || TextUtils.equals(ChromeSwitches.HERB_FLAVOR_DISABLED, flavor)) {
            return false;
        } else if (TextUtils.equals(flavor, ChromeSwitches.HERB_FLAVOR_ELDERBERRY)) {
            return IntentUtils.safeGetBooleanExtra(getIntent(),
                    ChromeLauncherActivity.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, true);
        } else {
            // Legacy Herb Flavors might hit this path before the caching logic corrects it, so
            // treat this as disabled.
            return false;
        }
    }

    /**
     * Adds extras to the Intent that are needed by Herb.
     */
    public static void updateHerbIntent(Context context, Intent newIntent) {
        // For Elderberry flavored Herbs that are to be launched in a separate task, add a random
        // UUID to try and prevent Android from refocusing/clobbering items that share the same
        // base intent.  If we do support refocusing of existing Herbs, we need to do it on the
        // current URL and not the URL that it was triggered with.
        if (TextUtils.equals(
                FeatureUtilities.getHerbFlavor(), ChromeSwitches.HERB_FLAVOR_ELDERBERRY)
                && ((newIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                        || (newIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0)) {
            String uuid = UUID.randomUUID().toString();
            newIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            newIntent.setFlags(
                    newIntent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Force a new document L+ to ensure the proper task/stack creation.
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
                newIntent.setClassName(context, SeparateTaskCustomTabActivity.class.getName());
            } else {
                int activityIndex =
                        ActivityAssigner.instance(ActivityAssigner.SEPARATE_TASK_CCT_NAMESPACE)
                        .assign(uuid);
                String className = SeparateTaskCustomTabActivity.class.getName() + activityIndex;
                newIntent.setClassName(context, className);
            }

            String url = IntentHandler.getUrlFromIntent(newIntent);
            assert url != null;
            newIntent.setData(new Uri.Builder().scheme(UrlConstants.CUSTOM_TAB_SCHEME)
                    .authority(uuid).query(url).build());
        }

        newIntent.putExtra(CustomTabsIntent.EXTRA_DEFAULT_SHARE_MENU_ITEM, true);
    }

    /**
     * @return Whether the intent is for launching a Custom Tab.
     */
    public static boolean isCustomTabIntent(Intent intent) {
        if (intent == null
                || CustomTabsIntent.shouldAlwaysUseBrowserUI(intent)
                || !intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)) {
            return false;
        }
        return IntentHandler.getUrlFromIntent(intent) != null;
    }

    /**
     * Creates an Intent that can be used to launch a {@link CustomTabActivity}.
     */
    public static Intent createCustomTabActivityIntent(
            Context context, Intent intent, boolean addHerbExtras) {
        // Use the copy constructor to carry over the myriad of extras.
        Uri uri = Uri.parse(IntentHandler.getUrlFromIntent(intent));

        Intent newIntent = new Intent(intent);
        newIntent.setAction(Intent.ACTION_VIEW);
        newIntent.setClassName(context, CustomTabActivity.class.getName());
        newIntent.setData(uri);

        // If a CCT intent triggers First Run, then NEW_TASK will be automatically applied.  As
        // part of that, it will inherit the EXCLUDE_FROM_RECENTS bit from ChromeLauncherActivity,
        // so explicitly remove it to ensure the CCT does not get lost in recents.
        if ((newIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                || (newIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0) {
            newIntent.setFlags(
                    newIntent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }

        if (addHerbExtras) {
            // TODO(tedchoc|mariakhomenko): Specifically not marking the intent is from Chrome via
            //                              IntentHandler.addTrustedIntentExtras as it breaks the
            //                              redirect logic for triggering instant apps.  See if
            //                              this is better addressed in TabRedirectHandler long
            //                              term.
            newIntent.putExtra(CustomTabIntentDataProvider.EXTRA_IS_OPENED_BY_CHROME, true);
        } else {
            IntentUtils.safeRemoveExtra(
                    intent, CustomTabIntentDataProvider.EXTRA_IS_OPENED_BY_CHROME);
        }
        if (addHerbExtras) updateHerbIntent(context, newIntent);

        return newIntent;
    }

    /**
     * Handles launching a {@link CustomTabActivity}, which will sit on top of a client's activity
     * in the same task.
     */
    private void launchCustomTabActivity() {
        boolean handled = CustomTabActivity.handleInActiveContentIfNeeded(getIntent());
        if (handled) return;

        // Create and fire a launch intent.
        startActivity(createCustomTabActivityIntent(
                this, getIntent(), !isCustomTabIntent(getIntent()) && mIsHerbIntent));
        if (mIsHerbIntent) overridePendingTransition(R.anim.activity_open_enter, R.anim.no_anim);
    }

    /**
     * Handles launching a {@link ChromeTabbedActivity}.
     * @param skipFre Whether skip the First Run Experience in ChromeTabbedActivity.
     */
    @SuppressLint("InlinedApi")
    private void launchTabbedMode(boolean skipFre) {
        maybePrefetchDnsInBackground();

        Intent newIntent = new Intent(getIntent());
        String className = MultiWindowUtils.getInstance().getTabbedActivityForIntent(
                newIntent, this).getName();
        newIntent.setClassName(getApplicationContext().getPackageName(), className);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            newIntent.addFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        }
        Uri uri = newIntent.getData();
        if (uri != null && "content".equals(uri.getScheme())) {
            newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        if (mIsInLegacyMultiInstanceMode) {
            MultiWindowUtils.getInstance().makeLegacyMultiInstanceIntent(this, newIntent);
        }
        if (skipFre) {
            newIntent.putExtra(ChromeTabbedActivity.SKIP_FIRST_RUN_EXPERIENCE, true);
        }

        // This system call is often modified by OEMs and not actionable. http://crbug.com/619646.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            startActivity(newIntent);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * @return Whether there is already an browser instance of Chrome already running.
     */
    public boolean isChromeBrowserActivityRunning() {
        for (WeakReference<Activity> reference : ApplicationStatus.getRunningActivities()) {
            Activity activity = reference.get();
            if (activity == null) continue;

            String className = activity.getClass().getName();
            if (TextUtils.equals(className, ChromeTabbedActivity.class.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to launch the First Run Experience.  If ChromeLauncherActivity is running with the
     * wrong Intent flags, we instead relaunch ChromeLauncherActivity to make sure it runs in its
     * own task, which then triggers First Run.
     * @return Whether or not the First Run Experience needed to be shown.
     * @param forTabbedMode Whether the First Run Experience is launched for tabbed mode.
     */
    private boolean launchFirstRunExperience(boolean forTabbedMode) {
        // Tries to launch the Generic First Run Experience for intent from GSA.
        boolean showLightweightFre =
                IntentHandler.determineExternalIntentSource(this.getPackageName(), getIntent())
                != ExternalAppId.GSA;
        Intent freIntent = FirstRunFlowSequencer.checkIfFirstRunIsNecessary(
                this, getIntent(), showLightweightFre);
        if (freIntent == null) return false;

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            if (CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)) {
                boolean isTabbedModeActive = false;
                boolean isLightweightFreActive = false;
                boolean isGenericFreActive = false;
                List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
                for (WeakReference<Activity> weakActivity : activities) {
                    Activity activity = weakActivity.get();
                    if (activity == null) {
                        continue;
                    }

                    if (activity instanceof ChromeTabbedActivity) {
                        isTabbedModeActive = true;
                        continue;
                    }

                    if (activity instanceof LightweightFirstRunActivity) {
                        isLightweightFreActive = true;
                        // A Generic or a new Lightweight First Run Experience will be launched
                        // below, so finish the old Lightweight First Run Experience.
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                        continue;
                    }

                    if (activity instanceof FirstRunActivity) {
                        isGenericFreActive = true;
                        continue;
                    }
                }

                if (forTabbedMode) {
                    if (isTabbedModeActive || isLightweightFreActive || !showLightweightFre) {
                        // Lets ChromeTabbedActivity checks and launches the Generic First Run
                        // Experience.
                        launchTabbedMode(false);
                        finish();
                        return true;
                    }
                } else if (isGenericFreActive) {
                    // Launch the Generic First Run Experience if it is active previously.
                    freIntent = FirstRunFlowSequencer.createGenericFirstRunIntent(
                            this, TextUtils.equals(getIntent().getAction(), Intent.ACTION_MAIN));
                }
            }
            // Add a PendingIntent so that the intent used to launch Chrome will be resent when
            // first run is completed or canceled.
            FirstRunFlowSequencer.addPendingIntent(this, freIntent, getIntent());
            startActivity(freIntent);
        } else {
            Intent newIntent = new Intent(getIntent());
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(newIntent);
        }
        finish();
        return true;
    }

    /**
     * Records metrics gleaned from the Intent.
     */
    private void recordIntentMetrics() {
        Intent intent = getIntent();
        IntentHandler.ExternalAppId source =
                IntentHandler.determineExternalIntentSource(getPackageName(), intent);
        if (intent.getPackage() == null && source != IntentHandler.ExternalAppId.CHROME) {
            int flagsOfInterest = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
            int maskedFlags = intent.getFlags() & flagsOfInterest;
            sIntentFlagsHistogram.record(maskedFlags);
        }
        MediaNotificationUma.recordClickSource(intent);
    }
}
