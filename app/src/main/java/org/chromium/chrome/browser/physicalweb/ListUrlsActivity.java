// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.components.location.LocationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This activity displays a list of nearby URLs as stored in the {@link UrlManager}.
 * This activity does not and should not rely directly or indirectly on the native library.
 */
public class ListUrlsActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        SwipeRefreshWidget.OnRefreshListener, UrlManager.Listener {
    public static final String REFERER_KEY = "referer";
    public static final int NOTIFICATION_REFERER = 1;
    public static final int OPTIN_REFERER = 2;
    public static final int PREFERENCE_REFERER = 3;
    public static final int DIAGNOSTICS_REFERER = 4;
    public static final int REFERER_BOUNDARY = 5;
    private static final String TAG = "PhysicalWeb";
    private static final String PREFS_VERSION_KEY =
            "org.chromium.chrome.browser.physicalweb.VERSION";
    private static final String PREFS_BOTTOM_BAR_KEY =
            "org.chromium.chrome.browser.physicalweb.BOTTOM_BAR_DISPLAY_COUNT";
    private static final int PREFS_VERSION = 1;
    private static final int BOTTOM_BAR_DISPLAY_LIMIT = 1;
    private static final int DURATION_SLIDE_UP_MS = 250;
    private static final int DURATION_SLIDE_DOWN_MS = 250;

    private final List<PwsResult> mPwsResults = new ArrayList<>();

    private Context mContext;
    private NearbyUrlsAdapter mAdapter;
    private PwsClient mPwsClient;
    private ListView mListView;
    private TextView mEmptyListText;
    private ImageView mScanningImageView;
    private SwipeRefreshWidget mSwipeRefreshWidget;
    private View mBottomBar;
    private boolean mIsInitialDisplayRecorded;
    private boolean mIsRefreshing;
    private boolean mIsRefreshUserInitiated;
    private NearbyForegroundSubscription mNearbyForegroundSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.physical_web_list_urls_activity);

        initSharedPreferences();

        mAdapter = new NearbyUrlsAdapter(this);

        View emptyView = findViewById(R.id.physical_web_empty);
        mListView = (ListView) findViewById(R.id.physical_web_urls_list);
        mListView.setEmptyView(emptyView);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);

        mEmptyListText = (TextView) findViewById(R.id.physical_web_empty_list_text);

        mScanningImageView = (ImageView) findViewById(R.id.physical_web_logo);

        mSwipeRefreshWidget =
                (SwipeRefreshWidget) findViewById(R.id.physical_web_swipe_refresh_widget);
        mSwipeRefreshWidget.setOnRefreshListener(this);

        mBottomBar = findViewById(R.id.physical_web_bottom_bar);

        int shadowColor = ApiCompatibilityUtils.getColor(getResources(),
                R.color.bottom_bar_shadow_color);
        FadingShadowView shadow =
                (FadingShadowView) findViewById(R.id.physical_web_bottom_bar_shadow);
        shadow.init(shadowColor, FadingShadow.POSITION_BOTTOM);

        View bottomBarClose = (View) findViewById(R.id.physical_web_bottom_bar_close);
        bottomBarClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideBottomBar();
            }
        });

        mPwsClient = new PwsClientImpl(this);
        int referer = getIntent().getIntExtra(REFERER_KEY, 0);
        if (savedInstanceState == null) {  // Ensure this is a newly-created activity.
            PhysicalWebUma.onActivityReferral(this, referer);
        }
        mIsInitialDisplayRecorded = false;
        mIsRefreshing = false;
        mIsRefreshUserInitiated = false;
        mNearbyForegroundSubscription = new NearbyForegroundSubscription(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int tintColor = ContextCompat.getColor(this, R.color.light_normal_color);

        Drawable tintedRefresh = ContextCompat.getDrawable(this, R.drawable.btn_toolbar_reload);
        tintedRefresh.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        menu.add(0, R.id.menu_id_refresh, 1, R.string.physical_web_refresh)
                .setIcon(tintedRefresh)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(0, R.id.menu_id_close, 2, R.string.close)
                .setIcon(R.drawable.btn_close)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_id_close) {
            finish();
            return true;
        } else if (id == R.id.menu_id_refresh) {
            startRefresh(true, false);
            return true;
        }

        Log.e(TAG, "Unknown menu item selected");
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        UrlManager.getInstance().addObserver(this);
        // Only connect so that we can subscribe to Nearby if we have the location permission.
        LocationUtils locationUtils = LocationUtils.getInstance();
        if (locationUtils.hasAndroidLocationPermission()
                && locationUtils.isSystemLocationSettingEnabled()) {
            mNearbyForegroundSubscription.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNearbyForegroundSubscription.subscribe();
        startRefresh(false, false);

        int bottomBarDisplayCount = getBottomBarDisplayCount();
        if (bottomBarDisplayCount < BOTTOM_BAR_DISPLAY_LIMIT) {
            showBottomBar();
            setBottomBarDisplayCount(bottomBarDisplayCount + 1);
        }
    }

    @Override
    protected void onPause() {
        mNearbyForegroundSubscription.unsubscribe();
        super.onPause();
    }

    @Override
    public void onRefresh() {
        startRefresh(true, true);
    }

    @Override
    protected void onStop() {
        UrlManager.getInstance().removeObserver(this);
        mNearbyForegroundSubscription.disconnect();
        super.onStop();
    }

    private void resolve(Collection<UrlInfo> urls, final boolean isUserInitiated) {
        final long timestamp = SystemClock.elapsedRealtime();
        mPwsClient.resolve(urls, new PwsClient.ResolveScanCallback() {
            @Override
            public void onPwsResults(Collection<PwsResult> pwsResults) {
                long duration = SystemClock.elapsedRealtime() - timestamp;
                if (isUserInitiated) {
                    PhysicalWebUma.onRefreshPwsResolution(ListUrlsActivity.this, duration);
                } else {
                    PhysicalWebUma.onForegroundPwsResolution(ListUrlsActivity.this, duration);
                }

                // filter out duplicate groups.
                for (PwsResult pwsResult : pwsResults) {
                    mPwsResults.add(pwsResult);
                    if (!mAdapter.hasGroupId(pwsResult.groupId)) {
                        mAdapter.add(pwsResult);

                        if (pwsResult.iconUrl != null && !mAdapter.hasIcon(pwsResult.iconUrl)) {
                            fetchIcon(pwsResult.iconUrl);
                        }
                    }
                }
                finishRefresh();
            }
        });
    }

    /**
     * Handle a click event.
     * @param adapterView The AdapterView where the click happened.
     * @param view The View that was clicked inside the AdapterView.
     * @param position The position of the clicked element in the list.
     * @param id The row id of the clicked element in the list.
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        PhysicalWebUma.onUrlSelected(this);
        PwsResult minPwsResult = mAdapter.getItem(position);
        String groupId = minPwsResult.groupId;

        // Make sure the PwsResult corresponds to the closest UrlDevice in the group.
        double minDistance = Double.MAX_VALUE;
        for (PwsResult pwsResult : mPwsResults) {
            if (pwsResult.groupId.equals(groupId)) {
                double distance = UrlManager.getInstance()
                        .getUrlInfoByUrl(pwsResult.requestUrl).getDistance();
                if (distance < minDistance) {
                    minDistance = distance;
                    minPwsResult = pwsResult;
                }
            }
        }
        Intent intent = createNavigateToUrlIntent(minPwsResult);
        mContext.startActivity(intent);
    }

    /**
     * Called when new nearby URLs are found.
     * @param urls The set of newly-found nearby URLs.
     */
    @Override
    public void onDisplayableUrlsAdded(Collection<UrlInfo> urls) {
        resolve(urls, false);
    }

    private void startRefresh(boolean isUserInitiated, boolean isSwipeInitiated) {
        if (mIsRefreshing) {
            return;
        }

        mIsRefreshing = true;
        mIsRefreshUserInitiated = isUserInitiated;

        // Clear the list adapter to trigger the empty list display.
        mAdapter.clear();

        Collection<UrlInfo> urls = UrlManager.getInstance().getUrls(true);

        // Check the Physical Web preference to ensure we do not resolve URLs when Physical Web is
        // off or onboarding. Normally the user will not reach this activity unless the preference
        // is explicitly enabled, but there is a button on the diagnostics page that launches into
        // the activity without checking the preference state.
        if (urls.isEmpty() || !PhysicalWeb.isPhysicalWebPreferenceEnabled()) {
            finishRefresh();
        } else {
            // Show the swipe-to-refresh busy indicator for refreshes initiated by a swipe.
            if (isSwipeInitiated) {
                mSwipeRefreshWidget.setRefreshing(true);
            }

            // Update the empty list view to show a scanning animation.
            mEmptyListText.setText(R.string.physical_web_empty_list_scanning);

            mScanningImageView.setImageResource(R.drawable.physical_web_scanning_animation);
            mScanningImageView.setColorFilter(null);

            AnimationDrawable animationDrawable =
                    (AnimationDrawable) mScanningImageView.getDrawable();
            animationDrawable.start();

            mPwsResults.clear();
            resolve(urls, isUserInitiated);
        }
    }

    private void finishRefresh() {
        // Hide the busy indicator.
        mSwipeRefreshWidget.setRefreshing(false);

        // Stop the scanning animation, show a "nothing found" message.
        mEmptyListText.setText(R.string.physical_web_empty_list);

        int tintColor = ContextCompat.getColor(this, R.color.light_grey);
        mScanningImageView.setImageResource(R.drawable.physical_web_logo);
        mScanningImageView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);

        // Record refresh-related UMA.
        if (!mIsInitialDisplayRecorded) {
            mIsInitialDisplayRecorded = true;
            PhysicalWebUma.onUrlsDisplayed(this, mAdapter.getCount());
        } else if (mIsRefreshUserInitiated) {
            PhysicalWebUma.onUrlsRefreshed(this, mAdapter.getCount());
        }

        mIsRefreshing = false;
    }

    private void fetchIcon(String iconUrl) {
        mPwsClient.fetchIcon(iconUrl, new PwsClient.FetchIconCallback() {
            @Override
            public void onIconReceived(String url, Bitmap bitmap) {
                mAdapter.setIcon(url, bitmap);
            }
        });
    }

    private void showBottomBar() {
        mBottomBar.setTranslationY(mBottomBar.getHeight());
        mBottomBar.setVisibility(View.VISIBLE);
        Animator animator = createTranslationYAnimator(mBottomBar, 0f, DURATION_SLIDE_UP_MS);
        animator.start();
    }

    private void hideBottomBar() {
        Animator animator = createTranslationYAnimator(mBottomBar, mBottomBar.getHeight(),
                DURATION_SLIDE_DOWN_MS);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBottomBar.setVisibility(View.GONE);
            }
        });
        animator.start();
    }

    private static Animator createTranslationYAnimator(View view, float endValue,
                long durationMillis) {
        return ObjectAnimator.ofFloat(view, "translationY", view.getTranslationY(), endValue)
                .setDuration(durationMillis);
    }

    private void initSharedPreferences() {
        if (ContextUtils.getAppSharedPreferences().getInt(PREFS_VERSION_KEY, 0) == PREFS_VERSION) {
            return;
        }

        // Stored preferences are old, upgrade to the current version.
        ContextUtils.getAppSharedPreferences().edit()
                .putInt(PREFS_VERSION_KEY, PREFS_VERSION)
                .apply();
    }

    private int getBottomBarDisplayCount() {
        return ContextUtils.getAppSharedPreferences().getInt(PREFS_BOTTOM_BAR_KEY, 0);
    }

    private void setBottomBarDisplayCount(int count) {
        ContextUtils.getAppSharedPreferences().edit()
                .putInt(PREFS_BOTTOM_BAR_KEY, count)
                .apply();
    }

    private static Intent createNavigateToUrlIntent(PwsResult pwsResult) {
        String url = pwsResult.siteUrl;
        if (url == null) {
            url = pwsResult.requestUrl;
        }

        return new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @VisibleForTesting
    void overridePwsClientForTesting(PwsClient pwsClient) {
        mPwsClient = pwsClient;
    }

    @VisibleForTesting
    void overrideContextForTesting(Context context) {
        mContext = context;
    }
}
