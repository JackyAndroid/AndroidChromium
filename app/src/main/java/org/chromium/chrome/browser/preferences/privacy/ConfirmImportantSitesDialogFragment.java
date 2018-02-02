// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Modal dialog that shows a list of important domains to the user which they can uncheck. Used to
 * allow the user to exclude domains from being cleared by the clear browsing data function.
 * We use proper bundle construction (through the {@link #newInstance(String[], int[], String[])}
 * method) and onActivityResult return conventions.
 */
public class ConfirmImportantSitesDialogFragment extends DialogFragment {
    private class ClearBrowsingDataAdapter extends ArrayAdapter<String>
            implements AdapterView.OnItemClickListener {
        private final String[] mDomains;
        private final String[] mFaviconURLs;
        private final int mCornerRadius;
        private final int mFaviconSize;
        private RoundedIconGenerator mIconGenerator;

        private ClearBrowsingDataAdapter(
                String[] domains, String[] faviconURLs, Resources resources) {
            super(getActivity(), R.layout.confirm_important_sites_list_row, domains);
            mDomains = domains;
            mFaviconURLs = faviconURLs;
            mFaviconSize = resources.getDimensionPixelSize(R.dimen.default_favicon_size);
            mCornerRadius = resources.getDimensionPixelSize(R.dimen.default_favicon_corner_radius);
            int textSize = resources.getDimensionPixelSize(R.dimen.default_favicon_icon_text_size);
            int iconColor = ApiCompatibilityUtils.getColor(
                    resources, R.color.default_favicon_background_color);
            mIconGenerator = new RoundedIconGenerator(
                    mFaviconSize, mFaviconSize, mCornerRadius, iconColor, textSize);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View childView = convertView;
            if (childView == null) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                childView =
                        inflater.inflate(R.layout.confirm_important_sites_list_row, parent, false);

                ViewAndFaviconHolder viewHolder = new ViewAndFaviconHolder();
                viewHolder.checkboxView = (CheckBox) childView.findViewById(R.id.icon_row_checkbox);
                viewHolder.imageView = (ImageView) childView.findViewById(R.id.icon_row_image);
                childView.setTag(viewHolder);
            }
            ViewAndFaviconHolder viewHolder = (ViewAndFaviconHolder) childView.getTag();
            configureChildView(position, viewHolder);
            return childView;
        }

        private void configureChildView(int position, ViewAndFaviconHolder viewHolder) {
            String domain = mDomains[position];
            viewHolder.checkboxView.setChecked(mCheckedState.get(domain));
            viewHolder.checkboxView.setText(domain);
            loadFavicon(viewHolder, mFaviconURLs[position]);
        }

        /**
         * Called when a list item is clicked. We toggle the checkbox and update our selected
         * domains list.
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String domain = mDomains[position];
            ViewAndFaviconHolder viewHolder = (ViewAndFaviconHolder) view.getTag();
            boolean isChecked = mCheckedState.get(domain);
            mCheckedState.put(domain, !isChecked);
            viewHolder.checkboxView.setChecked(!isChecked);
        }

        private void loadFavicon(final ViewAndFaviconHolder viewHolder, final String url) {
            viewHolder.imageCallback = new LargeIconCallback() {
                @Override
                public void onLargeIconAvailable(
                        Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
                    if (this != viewHolder.imageCallback) return;
                    Drawable image = getFaviconDrawable(icon, fallbackColor, url);
                    viewHolder.imageView.setImageDrawable(image);
                }
            };
            mLargeIconBridge.getLargeIconForUrl(url, mFaviconSize, viewHolder.imageCallback);
        }

        private Drawable getFaviconDrawable(Bitmap icon, int fallbackColor, String url) {
            if (icon == null) {
                mIconGenerator.setBackgroundColor(fallbackColor);
                icon = mIconGenerator.generateIconForUrl(url);
                return new BitmapDrawable(getResources(), icon);
            } else {
                RoundedBitmapDrawable roundedIcon =
                        RoundedBitmapDrawableFactory.create(getResources(),
                                Bitmap.createScaledBitmap(icon, mFaviconSize, mFaviconSize, false));
                roundedIcon.setCornerRadius(mCornerRadius);
                return roundedIcon;
            }
        }
    }

    /**
     * ViewHolder class optimizes looking up table row fields. findViewById is only called once
     * per row view initialization, and the references are cached here. Also stores a reference to
     * the favicon image callback so that we can make sure we load the correct favicon.
     */
    private static class ViewAndFaviconHolder {
        public CheckBox checkboxView;
        public ImageView imageView;
        public LargeIconCallback imageCallback;
    }

    /**
     * Constructs a new instance of the important sites dialog fragment.
     * @param importantDomains The list of important domains to display.
     * @param importantDomainReasons The reasons for choosing each important domain.
     * @param faviconURLs The list of favicon urls that correspond to each importantDomains.
     * @return An instance of ConfirmImportantSitesDialogFragment with the bundle arguments set.
     */
    public static ConfirmImportantSitesDialogFragment newInstance(
            String[] importantDomains, int[] importantDomainReasons, String[] faviconURLs) {
        ConfirmImportantSitesDialogFragment dialogFragment =
                new ConfirmImportantSitesDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putStringArray(IMPORTANT_DOMAINS_TAG, importantDomains);
        bundle.putIntArray(IMPORTANT_DOMAIN_REASONS_TAG, importantDomainReasons);
        bundle.putStringArray(FAVICON_URLS_TAG, faviconURLs);
        dialogFragment.setArguments(bundle);
        return dialogFragment;
    }

    private static final int FAVICON_MAX_CACHE_SIZE_BYTES = 100 * 1024; // 100KB

    /** The tag used when showing the clear browsing fragment. */
    public static final String FRAGMENT_TAG = "ConfirmImportantSitesDialogFragment";

    /** The tag for the string array of deselected domains. These are meant to NOT be cleared. */
    public static final String DESELECTED_DOMAINS_TAG = "DeselectedDomains";
    /** The tag for the int array of reasons the deselected domains were important. */
    public static final String DESELECTED_DOMAIN_REASONS_TAG = "DeselectedDomainReasons";
    /** The tag for the string array of ignored domains, which whill be cleared. */
    public static final String IGNORED_DOMAINS_TAG = "IgnoredDomains";
    /** The tag for the int array of reasons the ignored domains were important. */
    public static final String IGNORED_DOMAIN_REASONS_TAG = "IgnoredDomainReasons";

    /** The tag used for logging. */
    public static final String TAG = "ConfirmImportantSitesDialogFragment";

    /** The tag used to store the important domains in the bundle. */
    private static final String IMPORTANT_DOMAINS_TAG = "ImportantDomains";
    /** The tag used to store the important domain reasons in the bundle. */
    private static final String IMPORTANT_DOMAIN_REASONS_TAG = "ImportantDomainReasons";

    /** The tag used to store the favicon urls corresponding to each important domain. */
    private static final String FAVICON_URLS_TAG = "FaviconURLs";

    /** Array of important registerable domains we're showing to the user. */
    private String[] mImportantDomains;
    /** Map of the reasons the above important domains were chosen. */
    private Map<String, Integer> mImportantDomainsReasons;
    /** Array of favicon urls to use for each important domain above. */
    private String[] mFaviconURLs;
    /** The map of domains to the checked state, where true is checked. */
    private Map<String, Boolean> mCheckedState;
    /** The alert dialog shown to the user. */
    private AlertDialog mDialog;
    /** Our adapter that we use with the list view in the dialog. */
    private ClearBrowsingDataAdapter mAdapter;

    private LargeIconBridge mLargeIconBridge;

    private Profile mProfile;

    /** We store the custom list view for testing */
    private ListView mSitesListView;

    public ConfirmImportantSitesDialogFragment() {
        mImportantDomainsReasons = new HashMap<>();
        mCheckedState = new HashMap<>();
    }

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);
        mImportantDomains = args.getStringArray(IMPORTANT_DOMAINS_TAG);
        mFaviconURLs = args.getStringArray(FAVICON_URLS_TAG);
        int[] importantDomainReasons = args.getIntArray(IMPORTANT_DOMAIN_REASONS_TAG);
        for (int i = 0; i < mImportantDomains.length; ++i) {
            mImportantDomainsReasons.put(mImportantDomains[i], importantDomainReasons[i]);
            mCheckedState.put(mImportantDomains[i], true);
        }
    }

    @VisibleForTesting
    public Set<String> getDeselectedDomains() {
        HashSet<String> deselected = new HashSet<>();
        for (Entry<String, Boolean> entry : mCheckedState.entrySet()) {
            if (!entry.getValue()) deselected.add(entry.getKey());
        }
        return deselected;
    }

    @VisibleForTesting
    public ListView getSitesList() {
        return mSitesListView;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mLargeIconBridge != null) {
            mLargeIconBridge.destroy();
        }
    }

    private int[] toIntArray(List<Integer> boxedList) {
        int[] result = new int[boxedList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = boxedList.get(i);
        }
        return result;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // We check the domains and urls as well due to crbug.com/622879.
        if (savedInstanceState != null) {
            // The important domains and favicon URLs aren't currently saved, so if this dialog
            // is recreated from a saved instance they will be null. This method must return a
            // valid dialog, so these two array's are initialized, then the dialog is dismissed.
            // TODO(dmurph): save mImportantDomains and mFaviconURLs so that they can be restored
            // from a savedInstanceState and the dialog can be properly recreated rather than
            // dismissed.
            mImportantDomains = new String[0];
            mFaviconURLs = new String[0];
            dismiss();
        }
        mProfile = Profile.getLastUsedProfile().getOriginalProfile();
        mLargeIconBridge = new LargeIconBridge(mProfile);
        ActivityManager activityManager =
                ((ActivityManager) ContextUtils.getApplicationContext().getSystemService(
                        Context.ACTIVITY_SERVICE));
        int maxSize = Math.min(
                activityManager.getMemoryClass() / 16 * 25 * 1024, FAVICON_MAX_CACHE_SIZE_BYTES);
        mLargeIconBridge.createCache(maxSize);

        mAdapter = new ClearBrowsingDataAdapter(mImportantDomains, mFaviconURLs, getResources());
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    Intent data = new Intent();
                    List<String> deselectedDomains = new ArrayList<>();
                    List<Integer> deselectedDomainReasons = new ArrayList<>();
                    List<String> ignoredDomains = new ArrayList<>();
                    List<Integer> ignoredDomainReasons = new ArrayList<>();
                    for (Entry<String, Boolean> entry : mCheckedState.entrySet()) {
                        Integer reason = mImportantDomainsReasons.get(entry.getKey());
                        if (entry.getValue()) {
                            ignoredDomains.add(entry.getKey());
                            ignoredDomainReasons.add(reason);
                        } else {
                            deselectedDomains.add(entry.getKey());
                            deselectedDomainReasons.add(reason);
                        }
                    }
                    data.putExtra(DESELECTED_DOMAINS_TAG, deselectedDomains.toArray(new String[0]));
                    data.putExtra(
                            DESELECTED_DOMAIN_REASONS_TAG, toIntArray(deselectedDomainReasons));
                    data.putExtra(IGNORED_DOMAINS_TAG, ignoredDomains.toArray(new String[0]));
                    data.putExtra(IGNORED_DOMAIN_REASONS_TAG, toIntArray(ignoredDomainReasons));
                    getTargetFragment().onActivityResult(
                            getTargetRequestCode(), Activity.RESULT_OK, data);
                } else {
                    getTargetFragment().onActivityResult(getTargetRequestCode(),
                            Activity.RESULT_CANCELED, getActivity().getIntent());
                }
            }
        };
        // We create our own ListView, as AlertDialog doesn't let us set a message and a list
        // adapter at the same time.
        View messageAndListView = getActivity().getLayoutInflater().inflate(
                R.layout.clear_browsing_important_dialog_listview, null);
        mSitesListView = (ListView) messageAndListView.findViewById(R.id.select_dialog_listview);
        mSitesListView.setAdapter(mAdapter);
        mSitesListView.setOnItemClickListener(mAdapter);
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                        .setTitle(R.string.storage_clear_site_storage_title)
                        .setPositiveButton(R.string.clear_browsing_data_important_dialog_button,
                                listener)
                        .setNegativeButton(R.string.cancel, listener)
                        .setView(messageAndListView);
        mDialog = builder.create();

        return mDialog;
    }
}
