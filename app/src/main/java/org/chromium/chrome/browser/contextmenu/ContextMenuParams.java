// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.text.TextUtils;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.common.Referrer;

/**
 * A list of parameters that explain what kind of context menu to show the user.  This data is
 * generated from content/public/common/context_menu_params.h.
 */
@JNINamespace("ContextMenuParamsAndroid")
public class ContextMenuParams {
    /** Must correspond to the MediaType enum in WebKit/chromium/public/WebContextMenuData.h */
    @SuppressWarnings("unused")
    private static interface MediaType {
        public static final int MEDIA_TYPE_NONE = 0;
        public static final int MEDIA_TYPE_IMAGE = 1;
        public static final int MEDIA_TYPE_VIDEO = 2;
        public static final int MEDIA_TYPE_AUDIO = 3;
        public static final int MEDIA_TYPE_FILE = 4;
        public static final int MEDIA_TYPE_PLUGIN = 5;
    }

    private final String mPageUrl;
    private final String mLinkUrl;
    private final String mLinkText;
    private final String mTitleText;
    private final String mUnfilteredLinkUrl;
    private final String mSrcUrl;
    private final boolean mImageWasFetchedLoFi;
    private final Referrer mReferrer;

    private final boolean mIsAnchor;
    private final boolean mIsImage;
    private final boolean mIsVideo;

    /**
     * @return The URL associated with the main frame of the page that triggered the context menu.
     */
    public String getPageUrl() {
        return mPageUrl;
    }

    /**
     * @return The link URL, if any.
     */
    public String getLinkUrl() {
        return mLinkUrl;
    }

    /**
     * @return The link text, if any.
     */
    public String getLinkText() {
        return mLinkText;
    }

    /**
     * @return The title or alt attribute (if title is not available).
     */
    public String getTitleText() {
        return mTitleText;
    }

    /**
     * @return The unfiltered link URL, if any.
     */
    public String getUnfilteredLinkUrl() {
        return mUnfilteredLinkUrl;
    }

    /**
     * @return The source URL.
     */
    public String getSrcUrl() {
        return mSrcUrl;
    }

    /**
     * @return Whether or not an image was fetched using Lo-Fi.
     */
    public boolean imageWasFetchedLoFi() {
        return mImageWasFetchedLoFi;
    }

    /**
     * @return the referrer associated with the frame on which the menu is invoked
     */
    public Referrer getReferrer() {
        return mReferrer;
    }

    /**
     * @return Whether or not the context menu is being shown for an anchor.
     */
    public boolean isAnchor() {
        return mIsAnchor;
    }

    /**
     * @return Whether or not the context menu is being shown for an image.
     */
    public boolean isImage() {
        return mIsImage;
    }

    /**
     * @return Whether or not the context menu is being shown for a video.
     */
    public boolean isVideo() {
        return mIsVideo;
    }

    private ContextMenuParams(int mediaType, String pageUrl, String linkUrl, String linkText,
            String unfilteredLinkUrl, String srcUrl, String titleText, boolean imageWasFetchedLoFi,
            Referrer referrer) {
        mPageUrl = pageUrl;
        mLinkUrl = linkUrl;
        mLinkText = linkText;
        mTitleText = titleText;
        mUnfilteredLinkUrl = unfilteredLinkUrl;
        mSrcUrl = srcUrl;
        mImageWasFetchedLoFi = imageWasFetchedLoFi;
        mReferrer = referrer;

        mIsAnchor = !TextUtils.isEmpty(linkUrl);
        mIsImage = mediaType == MediaType.MEDIA_TYPE_IMAGE;
        mIsVideo = mediaType == MediaType.MEDIA_TYPE_VIDEO;
    }

    @CalledByNative
    private static ContextMenuParams create(int mediaType, String pageUrl, String linkUrl,
            String linkText, String unfilteredLinkUrl, String srcUrl, String titleText,
            boolean imageWasFetchedLoFi, String sanitizedReferrer, int referrerPolicy) {
        Referrer referrer = TextUtils.isEmpty(sanitizedReferrer)
                ? null : new Referrer(sanitizedReferrer, referrerPolicy);
        return new ContextMenuParams(mediaType, pageUrl, linkUrl, linkText, unfilteredLinkUrl,
                srcUrl, titleText, imageWasFetchedLoFi, referrer);
    }
}
