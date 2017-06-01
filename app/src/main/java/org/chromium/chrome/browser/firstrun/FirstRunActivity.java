// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.CachedMetrics.EnumeratedHistogramSample;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.EmbedContentViewActivity;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoUtils;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionProxyUma;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.IntentUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Handles the First Run Experience sequences shown to the user launching Chrome for the first time.
 * It supports only a simple format of FRE:
 *   [Welcome]
 *   [Intro pages...]
 *   [Sign-in page]
 * The activity might be run more than once, e.g. 1) for ToS and sign-in, and 2) for intro.
 */
public class FirstRunActivity extends AppCompatActivity implements FirstRunPageDelegate {
    protected static final String TAG = "FirstRunActivity";

    // Incoming parameters:
    public static final String EXTRA_COMING_FROM_CHROME_ICON = "Extra.ComingFromChromeIcon";
    public static final String EXTRA_USE_FRE_FLOW_SEQUENCER = "Extra.UseFreFlowSequencer";
    public static final String EXTRA_START_LIGHTWEIGHT_FRE = "Extra.StartLightweightFRE";
    public static final String EXTRA_CHROME_LAUNCH_INTENT = "Extra.FreChromeLaunchIntent";
    public static final String EXTRA_FINISH_ON_TOUCH_OUTSIDE = "Extra.FreFinishOnTouchOutside";

    static final String SHOW_WELCOME_PAGE = "ShowWelcome";
    static final String SHOW_SIGNIN_PAGE = "ShowSignIn";
    static final String SHOW_DATA_REDUCTION_PAGE = "ShowDataReduction";

    // Outgoing results:
    public static final String RESULT_CLOSE_APP = "Close App";
    public static final String RESULT_SIGNIN_ACCOUNT_NAME = "ResultSignInTo";
    public static final String RESULT_SHOW_SIGNIN_SETTINGS = "ResultShowSignInSettings";
    public static final String EXTRA_FIRST_RUN_ACTIVITY_RESULT = "Extra.FreActivityResult";
    public static final String EXTRA_FIRST_RUN_COMPLETE = "Extra.FreComplete";

    public static final boolean DEFAULT_METRICS_AND_CRASH_REPORTING = true;

    // UMA constants.
    private static final int SIGNIN_SETTINGS_DEFAULT_ACCOUNT = 0;
    private static final int SIGNIN_SETTINGS_ANOTHER_ACCOUNT = 1;
    private static final int SIGNIN_ACCEPT_DEFAULT_ACCOUNT = 2;
    private static final int SIGNIN_ACCEPT_ANOTHER_ACCOUNT = 3;
    private static final int SIGNIN_NO_THANKS = 4;
    private static final int SIGNIN_MAX = 5;
    private static final EnumeratedHistogramSample sSigninChoiceHistogram =
            new EnumeratedHistogramSample("MobileFre.SignInChoice", SIGNIN_MAX);

    private static final int FRE_PROGRESS_STARTED = 0;
    private static final int FRE_PROGRESS_WELCOME_SHOWN = 1;
    private static final int FRE_PROGRESS_DATA_SAVER_SHOWN = 2;
    private static final int FRE_PROGRESS_SIGNIN_SHOWN = 3;
    private static final int FRE_PROGRESS_COMPLETED_SIGNED_IN = 4;
    private static final int FRE_PROGRESS_COMPLETED_NOT_SIGNED_IN = 5;
    private static final int FRE_PROGRESS_MAX = 6;
    private static final EnumeratedHistogramSample sMobileFreProgressMainIntentHistogram =
            new EnumeratedHistogramSample("MobileFre.SignInChoice.MainIntent", FRE_PROGRESS_MAX);
    private static final EnumeratedHistogramSample sMobileFreProgressViewIntentHistogram =
            new EnumeratedHistogramSample("MobileFre.SignInChoice.ViewIntent", FRE_PROGRESS_MAX);

    @VisibleForTesting
    static FirstRunGlue sGlue = new FirstRunGlueImpl();

    private boolean mShowWelcomePage = true;

    private String mResultSignInAccountName;
    private boolean mResultShowSignInSettings;

    private boolean mNativeSideIsInitialized;

    private ProfileDataCache mProfileDataCache;
    private FirstRunViewPager mPager;

    protected Bundle mFreProperties;

    private List<Callable<FirstRunPage>> mPages;

    private List<Integer> mFreProgressStates;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private FirstRunPagerAdapter mPagerAdapter;

    /**
     * Defines a sequence of pages to be shown (depending on parameters etc).
     */
    private void createPageSequence() {
        mPages = new ArrayList<Callable<FirstRunPage>>();
        mFreProgressStates = new ArrayList<Integer>();

        // An optional welcome page.
        if (mShowWelcomePage) {
            mPages.add(pageOf(ToSAndUMAFirstRunFragment.class));
            mFreProgressStates.add(FRE_PROGRESS_WELCOME_SHOWN);
        }

        // An optional Data Saver page.
        if (mFreProperties.getBoolean(SHOW_DATA_REDUCTION_PAGE)) {
            mPages.add(pageOf(DataReductionProxyFirstRunFragment.class));
            mFreProgressStates.add(FRE_PROGRESS_DATA_SAVER_SHOWN);
        }

        // An optional sign-in page.
        if (mFreProperties.getBoolean(SHOW_SIGNIN_PAGE)) {
            mPages.add(pageOf(AccountFirstRunFragment.class));
            mFreProgressStates.add(FRE_PROGRESS_SIGNIN_SHOWN);
        }
    }

    // Activity:

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeBrowserProcess();

        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mFreProperties = savedInstanceState;
        } else if (getIntent() != null) {
            mFreProperties = getIntent().getExtras();
        } else {
            mFreProperties = new Bundle();
        }

        setFinishOnTouchOutside(
                mFreProperties.getBoolean(FirstRunActivity.EXTRA_FINISH_ON_TOUCH_OUTSIDE));

        // Skip creating content view if it is to start a lightweight First Run Experience.
        if (mFreProperties.getBoolean(FirstRunActivity.EXTRA_START_LIGHTWEIGHT_FRE)) {
            return;
        }

        mPager = new FirstRunViewPager(this);
        mPager.setId(R.id.fre_pager);
        setContentView(mPager);

        new FirstRunFlowSequencer(this, mFreProperties) {
            @Override
            public void onFlowIsKnown(Bundle freProperties) {
                if (freProperties == null) {
                    completeFirstRunExperience();
                    return;
                }

                mFreProperties = freProperties;
                mShowWelcomePage = mFreProperties.getBoolean(SHOW_WELCOME_PAGE);

                createPageSequence();

                if (TextUtils.isEmpty(mResultSignInAccountName)) {
                    mResultSignInAccountName = mFreProperties.getString(
                            AccountFirstRunFragment.FORCE_SIGNIN_ACCOUNT_TO);
                }

                if (mPages.size() == 0) {
                    completeFirstRunExperience();
                    return;
                }

                mPagerAdapter =
                        new FirstRunPagerAdapter(getFragmentManager(), mPages, mFreProperties);
                stopProgressionIfNotAcceptedTermsOfService();
                mPager.setAdapter(mPagerAdapter);

                recordFreProgressHistogram(mFreProgressStates.get(0));

                skipPagesIfNecessary();
            }
        }.start();

        recordFreProgressHistogram(FRE_PROGRESS_STARTED);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(mFreProperties);
    }

    @Override
    protected void onPause() {
        super.onPause();
        flushPersistentData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProfileDataCache != null) mProfileDataCache.destroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopProgressionIfNotAcceptedTermsOfService();
        if (!mFreProperties.getBoolean(EXTRA_USE_FRE_FLOW_SEQUENCER)) {
            if (FirstRunStatus.getFirstRunFlowComplete()) {
                // This is a parallel flow that needs to be refreshed/re-fired.
                // Signal the FRE flow completion and re-launch the original intent.
                completeFirstRunExperience();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Terminate if we are still waiting for the native or for Android EDU / GAIA Child checks.
        if (mPagerAdapter == null) {
            abortFirstRunExperience();
            return;
        }

        Object currentItem = mPagerAdapter.instantiateItem(mPager, mPager.getCurrentItem());
        if (currentItem instanceof FirstRunPage) {
            FirstRunPage page = (FirstRunPage) currentItem;
            if (page.interceptBackPressed()) return;
        }

        if (mPager.getCurrentItem() == 0) {
            abortFirstRunExperience();
        } else {
            mPager.setCurrentItem(mPager.getCurrentItem() - 1, false);
        }
    }

    // FirstRunPageDelegate:

    @Override
    public ProfileDataCache getProfileDataCache() {
        if (mProfileDataCache == null) {
            mProfileDataCache = new ProfileDataCache(FirstRunActivity.this, null);
            mProfileDataCache.setProfile(Profile.getLastUsedProfile());
        }
        return mProfileDataCache;
    }

    @Override
    public void advanceToNextPage() {
        jumpToPage(mPager.getCurrentItem() + 1);
    }

    @Override
    public void recreateCurrentPage() {
        mPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public void abortFirstRunExperience() {
        Intent intent = new Intent();
        if (mFreProperties != null) intent.putExtras(mFreProperties);
        intent.putExtra(RESULT_CLOSE_APP, true);
        finishAllTheActivities(getLocalClassName(), Activity.RESULT_CANCELED, intent);

        sendPendingIntentIfNecessary(false);
    }

    @Override
    public void completeFirstRunExperience() {
        if (!TextUtils.isEmpty(mResultSignInAccountName)) {
            boolean defaultAccountName =
                    sGlue.isDefaultAccountName(getApplicationContext(), mResultSignInAccountName);
            int choice;
            if (mResultShowSignInSettings) {
                if (defaultAccountName) {
                    choice = SIGNIN_SETTINGS_DEFAULT_ACCOUNT;
                } else {
                    choice = SIGNIN_SETTINGS_ANOTHER_ACCOUNT;
                }
            } else {
                if (defaultAccountName) {
                    choice = SIGNIN_ACCEPT_DEFAULT_ACCOUNT;
                } else {
                    choice = SIGNIN_ACCEPT_ANOTHER_ACCOUNT;
                }
            }
            sSigninChoiceHistogram.record(choice);
            recordFreProgressHistogram(FRE_PROGRESS_COMPLETED_SIGNED_IN);
        } else {
            recordFreProgressHistogram(FRE_PROGRESS_COMPLETED_NOT_SIGNED_IN);
        }

        mFreProperties.putString(RESULT_SIGNIN_ACCOUNT_NAME, mResultSignInAccountName);
        mFreProperties.putBoolean(RESULT_SHOW_SIGNIN_SETTINGS, mResultShowSignInSettings);
        FirstRunFlowSequencer.markFlowAsCompleted(this, mFreProperties);

        if (DataReductionPromoUtils.getDisplayedFreOrSecondRunPromo()) {
            if (DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) {
                DataReductionProxyUma
                        .dataReductionProxyUIAction(DataReductionProxyUma.ACTION_FRE_ENABLED);
                DataReductionPromoUtils.saveFrePromoOptOut(false);
            } else {
                DataReductionProxyUma
                        .dataReductionProxyUIAction(DataReductionProxyUma.ACTION_FRE_DISABLED);
                DataReductionPromoUtils.saveFrePromoOptOut(true);
            }
        }

        Intent resultData = new Intent();
        resultData.putExtras(mFreProperties);
        finishAllTheActivities(getLocalClassName(), Activity.RESULT_OK, resultData);

        sendPendingIntentIfNecessary(true);
    }

    @Override
    public void refuseSignIn() {
        sSigninChoiceHistogram.record(SIGNIN_NO_THANKS);
        mResultSignInAccountName = null;
        mResultShowSignInSettings = false;
    }

    @Override
    public void acceptSignIn(String accountName) {
        mResultSignInAccountName = accountName;
    }

    @Override
    public void askToOpenSignInSettings() {
        mResultShowSignInSettings = true;
    }

    @Override
    public boolean didAcceptTermsOfService() {
        return sGlue.didAcceptTermsOfService(getApplicationContext());
    }

    @Override
    public void acceptTermsOfService(boolean allowCrashUpload) {
        // If default is true then it corresponds to opt-out and false corresponds to opt-in.
        UmaUtils.recordMetricsReportingDefaultOptIn(!DEFAULT_METRICS_AND_CRASH_REPORTING);
        sGlue.acceptTermsOfService(allowCrashUpload);
        FirstRunStatus.setSkipWelcomePage(true);
        flushPersistentData();
        stopProgressionIfNotAcceptedTermsOfService();
        jumpToPage(mPager.getCurrentItem() + 1);
    }

    @Override
    public void openAccountAdder(Fragment fragment) {
        sGlue.openAccountAdder(fragment);
    }

    protected void flushPersistentData() {
        if (mNativeSideIsInitialized) ChromeApplication.flushPersistentData();
    }

    /**
    * Finish all the instances of the given Activity.
    * @param targetActivity The class name of the target Activity.
    * @param result The result code to propagate back to the originating activity.
    * @param data The data to propagate back to the originating activity.
    */
    protected static void finishAllTheActivities(String targetActivity, int result, Intent data) {
        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        for (WeakReference<Activity> weakActivity : activities) {
            Activity activity = weakActivity.get();
            if (activity != null && activity.getLocalClassName().equals(targetActivity)) {
                activity.setResult(result, data);
                activity.finish();
            }
        }
    }

    /**
     * Sends the PendingIntent included with the CHROME_LAUNCH_INTENT extra if it exists.
     * @param complete Whether first run completed successfully.
     */
    protected void sendPendingIntentIfNecessary(final boolean complete) {
        PendingIntent pendingIntent = IntentUtils.safeGetParcelableExtra(getIntent(),
                EXTRA_CHROME_LAUNCH_INTENT);
        if (pendingIntent == null) return;

        Intent extraDataIntent = new Intent();
        extraDataIntent.putExtra(FirstRunActivity.EXTRA_FIRST_RUN_ACTIVITY_RESULT, true);
        extraDataIntent.putExtra(FirstRunActivity.EXTRA_FIRST_RUN_COMPLETE, complete);

        try {
            // After the PendingIntent has been sent, send a first run callback to custom tabs if
            // necessary.
            PendingIntent.OnFinished onFinished = new PendingIntent.OnFinished() {
                @Override
                public void onSendFinished(PendingIntent pendingIntent, Intent intent,
                        int resultCode, String resultData, Bundle resultExtras) {
                    if (ChromeLauncherActivity.isCustomTabIntent(intent)) {
                        CustomTabsConnection.getInstance(
                                getApplication()).sendFirstRunCallbackIfNecessary(intent, complete);
                    }
                }
            };

            // Use the PendingIntent to send the intent that originally launched Chrome. The intent
            // will go back to the ChromeLauncherActivity, which will route it accordingly.
            pendingIntent.send(this, complete ? Activity.RESULT_OK : Activity.RESULT_CANCELED,
                    extraDataIntent, onFinished, null);
        } catch (CanceledException e) {
            Log.e(TAG, "Unable to send PendingIntent.", e);
        }
    }

    /**
     * Transitions to a given page.
     * @return Whether the transition to a given page was allowed.
     * @param position A page index to transition to.
     */
    private boolean jumpToPage(int position) {
        if (mShowWelcomePage && !didAcceptTermsOfService()) {
            return position == 0;
        }
        if (position >= mPagerAdapter.getCount()) {
            completeFirstRunExperience();
            return false;
        }
        mPager.setCurrentItem(position, false);
        recordFreProgressHistogram(mFreProgressStates.get(position));
        return true;
    }

    private void stopProgressionIfNotAcceptedTermsOfService() {
        if (mPagerAdapter == null) return;
        mPagerAdapter.setStopAtTheFirstPage(mShowWelcomePage && !didAcceptTermsOfService());
    }

    private void skipPagesIfNecessary() {
        if (mPagerAdapter == null) return;

        int currentPageIndex = mPager.getCurrentItem();
        while (currentPageIndex < mPagerAdapter.getCount()) {
            FirstRunPage currentPage = (FirstRunPage) mPagerAdapter.getItem(currentPageIndex);
            if (!currentPage.shouldSkipPageOnCreate(getApplicationContext())) return;
            if (!jumpToPage(currentPageIndex + 1)) return;
            currentPageIndex = mPager.getCurrentItem();
        }
    }

    private void initializeBrowserProcess() {
        // The Chrome browser process must be started here because this Activity
        // may be started explicitly for tests cases, from Android notifications or
        // when the application is restoring a FRE fragment after Chrome being killed.
        // This should happen before super.onCreate() because it might recreate a fragment,
        // and a fragment might depend on the native library.
        try {
            ChromeBrowserInitializer.getInstance(this).handleSynchronousStartup();
            mNativeSideIsInitialized = true;
        } catch (ProcessInitException e) {
            Log.e(TAG, "Unable to load native library.", e);
            abortFirstRunExperience();
            return;
        }
    }

    private void recordFreProgressHistogram(int state) {
        if (mFreProperties.getBoolean(FirstRunActivity.EXTRA_COMING_FROM_CHROME_ICON)) {
            sMobileFreProgressMainIntentHistogram.record(state);
        } else {
            sMobileFreProgressViewIntentHistogram.record(state);
        }
    }

    /**
     * Creates a trivial page constructor for a given page type.
     * @param clazz The .class of the page type.
     * @return The simple constructor for a given page type (no parameters, no tuning).
     */
    public static Callable<FirstRunPage> pageOf(final Class<? extends FirstRunPage> clazz) {
        return new Callable<FirstRunPage>() {
            @Override
            public FirstRunPage call() throws Exception {
                Constructor<? extends FirstRunPage> constructor = clazz.getDeclaredConstructor();
                return constructor.newInstance();
            }
        };
    }

    @Override
    public void showEmbedContentViewActivity(int title, int url) {
        EmbedContentViewActivity.show(this, title, url);
    }
}
