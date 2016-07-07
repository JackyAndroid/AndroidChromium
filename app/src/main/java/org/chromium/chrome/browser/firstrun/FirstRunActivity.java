// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.FieldTrialList;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.EmbedContentViewActivity;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoScreen;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionProxyUma;
import org.chromium.chrome.browser.profiles.Profile;

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
    public static final String COMING_FROM_CHROME_ICON = "ComingFromChromeIcon";
    public static final String USE_FRE_FLOW_SEQUENCER = "UseFreFlowSequencer";

    public static final String SHOW_WELCOME_PAGE = "ShowWelcome";
    public static final String SHOW_SIGNIN_PAGE = "ShowSignIn";

    // Outcoming results:
    public static final String RESULT_CLOSE_APP = "Close App";
    public static final String RESULT_SIGNIN_ACCOUNT_NAME = "ResultSignInTo";
    public static final String RESULT_SHOW_SYNC_SETTINGS = "ResultShowSyncSettings";

    // UMA constants.
    private static final String UMA_SIGNIN_CHOICE = "MobileFre.SignInChoice";
    private static final String UMA_SIGNIN_CHOICE_ENTRY_MAIN_INTENT = ".MainIntent";
    private static final String UMA_SIGNIN_CHOICE_ENTRY_VIEW_INTENT = ".ViewIntent";
    private static final String UMA_SIGNIN_CHOICE_ZERO_ACCOUNTS = ".ZeroAccounts";
    private static final String UMA_SIGNIN_CHOICE_ONE_ACCOUNT = ".OneAccount";
    private static final String UMA_SIGNIN_CHOICE_MANY_ACCOUNTS = ".ManyAccounts";
    private static final int SIGNIN_SETTINGS_DEFAULT_ACCOUNT = 0;
    private static final int SIGNIN_SETTINGS_ANOTHER_ACCOUNT = 1;
    private static final int SIGNIN_ACCEPT_DEFAULT_ACCOUNT = 2;
    private static final int SIGNIN_ACCEPT_ANOTHER_ACCOUNT = 3;
    private static final int SIGNIN_NO_THANKS = 4;
    private static final int SIGNIN_OPTION_COUNT = 5;

    @VisibleForTesting
    static FirstRunGlue sGlue = new FirstRunGlueImpl();

    private boolean mShowWelcomePage = true;

    private String mResultSignInAccountName;
    private boolean mResultShowSyncSettings;

    private boolean mNativeSideIsInitialized;

    private ProfileDataCache mProfileDataCache;
    private ViewPager mPager;

    private Bundle mFreProperties;

    private List<Callable<FirstRunPage>> mPages;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private FirstRunPagerAdapter mPagerAdapter;

    /**
     * Defines a sequence of pages to be shown (depending on parameters etc).
     */
    private void createPageSequence() {
        mPages = new ArrayList<Callable<FirstRunPage>>();

        // An optional welcome page.
        if (mShowWelcomePage) mPages.add(pageOf(ToSAndUMAFirstRunFragment.class));

        // An optional Data Saver page.
        if (FieldTrialList.findFullName("DataReductionProxyFREPromo").startsWith("Enabled")) {
            mPages.add(pageOf(DataReductionProxyFirstRunFragment.class));
        }

        // An optional sign-in page.
        if (mFreProperties.getBoolean(SHOW_SIGNIN_PAGE)) {
            mPages.add(pageOf(AccountFirstRunFragment.class));
        }
    }

    // Activity:

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeBrowserProcess();

        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);

        if (savedInstanceState != null) {
            mFreProperties = savedInstanceState;
        } else if (getIntent() != null) {
            mFreProperties = getIntent().getExtras();
        } else {
            mFreProperties = new Bundle();
        }

        mPager = new ViewPager(this);
        mPager.setId(R.id.fre_pager);
        setContentView(mPager);

        mProfileDataCache = new ProfileDataCache(FirstRunActivity.this, null);
        mProfileDataCache.setProfile(Profile.getLastUsedProfile());
        new FirstRunFlowSequencer(this, mFreProperties) {
            @Override
            public void onFlowIsKnown(Activity activity, Bundle freProperties) {
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

                skipPagesIfNecessary();
            }
        }.start();
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
        mProfileDataCache.destroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopProgressionIfNotAcceptedTermsOfService();
        if (!mFreProperties.getBoolean(USE_FRE_FLOW_SEQUENCER)) {
            if (FirstRunStatus.getFirstRunFlowComplete(this)) {
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
            mPager.setCurrentItem(mPager.getCurrentItem() - 1);
        }
    }

    // FirstRunPageDelegate:

    @Override
    public ProfileDataCache getProfileDataCache() {
        return mProfileDataCache;
    }

    @Override
    public void advanceToNextPage() {
        jumpToPage(mPager.getCurrentItem() + 1, true);
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
        finishAllFREActivities(Activity.RESULT_CANCELED, intent);
    }

    @Override
    public void completeFirstRunExperience() {
        if (!TextUtils.isEmpty(mResultSignInAccountName)) {
            boolean defaultAccountName =
                    sGlue.isDefaultAccountName(getApplicationContext(), mResultSignInAccountName);
            int choice;
            if (mResultShowSyncSettings) {
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
            RecordHistogram.recordEnumeratedHistogram(
                    UMA_SIGNIN_CHOICE, choice, SIGNIN_OPTION_COUNT);

            String entryType = mFreProperties.getBoolean(FirstRunActivity.COMING_FROM_CHROME_ICON)
                    ? UMA_SIGNIN_CHOICE_ENTRY_MAIN_INTENT : UMA_SIGNIN_CHOICE_ENTRY_VIEW_INTENT;
            int numAccounts = sGlue.numberOfAccounts(getApplicationContext());
            String numAccountsString;
            if (numAccounts == 0) {
                numAccountsString = UMA_SIGNIN_CHOICE_ZERO_ACCOUNTS;
            } else if (numAccounts == 1) {
                numAccountsString = UMA_SIGNIN_CHOICE_ONE_ACCOUNT;
            } else {
                numAccountsString = UMA_SIGNIN_CHOICE_MANY_ACCOUNTS;
            }
            RecordHistogram.recordEnumeratedHistogram(
                    UMA_SIGNIN_CHOICE + entryType + numAccountsString, choice, SIGNIN_OPTION_COUNT);
        }

        mFreProperties.putString(RESULT_SIGNIN_ACCOUNT_NAME, mResultSignInAccountName);
        mFreProperties.putBoolean(RESULT_SHOW_SYNC_SETTINGS, mResultShowSyncSettings);
        FirstRunFlowSequencer.markFlowAsCompleted(this, mFreProperties);

        if (DataReductionPromoScreen
                .getDisplayedDataReductionPromo(getApplicationContext())) {
            if (DataReductionProxySettings.getInstance().isDataReductionProxyEnabled()) {
                DataReductionProxyUma
                        .dataReductionProxyUIAction(DataReductionProxyUma.ACTION_FRE_ENABLED);
            } else {
                DataReductionProxyUma
                        .dataReductionProxyUIAction(DataReductionProxyUma.ACTION_FRE_DISABLED);
            }
        }

        Intent resultData = new Intent();
        resultData.putExtras(mFreProperties);
        finishAllFREActivities(Activity.RESULT_OK, resultData);
    }

    @Override
    public void onSigninDialogShown() {
        RecordUserAction.record("MobileFre.SignInShown");
    }

    @Override
    public void refuseSignIn() {
        RecordHistogram.recordEnumeratedHistogram(
                UMA_SIGNIN_CHOICE, SIGNIN_NO_THANKS, SIGNIN_OPTION_COUNT);
        mResultSignInAccountName = null;
        mResultShowSyncSettings = false;
    }

    @Override
    public void acceptSignIn(String accountName) {
        mResultSignInAccountName = accountName;
    }

    @Override
    public void askToOpenSyncSettings() {
        mResultShowSyncSettings = true;
    }

    @Override
    public boolean didAcceptTermsOfService() {
        return sGlue.didAcceptTermsOfService(getApplicationContext());
    }

    @Override
    public boolean isNeverUploadCrashDump() {
        return sGlue.isNeverUploadCrashDump(getApplicationContext());
    }

    @Override
    public void acceptTermsOfService(boolean allowCrashUpload) {
        sGlue.acceptTermsOfService(getApplicationContext(), allowCrashUpload);
        flushPersistentData();
        stopProgressionIfNotAcceptedTermsOfService();
        jumpToPage(mPager.getCurrentItem() + 1, true);
    }

    @Override
    public void openAccountAdder(Fragment fragment) {
        sGlue.openAccountAdder(fragment);
    }

    protected void flushPersistentData() {
        if (mNativeSideIsInitialized) ChromeApplication.flushPersistentData();
    }

    private static void finishAllFREActivities(int result, Intent data) {
        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        for (WeakReference<Activity> weakActivity : activities) {
            Activity activity = weakActivity.get();
            if (activity instanceof FirstRunActivity) {
                activity.setResult(result, data);
                activity.finish();
            }
        }
    }

    /**
     * Transitions to a given page.
     * @return Whether the transition to a given page was allowed.
     * @param position A page index to transition to.
     * @param smooth   Whether the transition should be smooth.
     */
    private boolean jumpToPage(int position, boolean smooth) {
        if (mShowWelcomePage && !didAcceptTermsOfService()) {
            return position == 0;
        }
        if (position >= mPagerAdapter.getCount()) {
            completeFirstRunExperience();
            return false;
        }
        mPager.setCurrentItem(position, smooth);
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
            if (!jumpToPage(currentPageIndex + 1, false)) return;
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
            ((ChromeApplication) getApplication())
                    .startBrowserProcessesAndLoadLibrariesSync(true);
            mNativeSideIsInitialized = true;
        } catch (ProcessInitException e) {
            Log.e(TAG, "Unable to load native library.", e);
            abortFirstRunExperience();
            return;
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
