// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.io.Serializable;

/**
 * Exception information for a given origin.
 */
@SuppressFBWarnings("NM_CLASS_NOT_EXCEPTION")
public class ContentSettingException implements Serializable {
    private final int mContentSettingType;
    private final String mPattern;
    private final ContentSetting mContentSetting;
    private final String mSource;

    /**
     * Construct a ContentSettingException.
     * @param type The content setting type this exception covers.
     * @param pattern The host/domain pattern this exception covers.
     * @param setting The setting for this exception, e.g. ALLOW or BLOCK.
     * @param source The source for this exception, e.g. "policy".
     */
    public ContentSettingException(
            int type, String pattern, ContentSetting setting, String source) {
        mContentSettingType = type;
        mPattern = pattern;
        mContentSetting = setting;
        mSource = source;
    }

    public String getPattern() {
        return mPattern;
    }

    public ContentSetting getContentSetting() {
        return mContentSetting;
    }

    public String getSource() {
        return mSource;
    }

    /**
     * Sets the content setting value for this exception.
     */
    public void setContentSetting(ContentSetting value) {
        PrefServiceBridge.getInstance().nativeSetContentSettingForPattern(mContentSettingType,
                mPattern, value.toInt());
    }
}
