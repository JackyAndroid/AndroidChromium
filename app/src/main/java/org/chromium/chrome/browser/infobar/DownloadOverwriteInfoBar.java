// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;

import java.util.List;

/**
 * An infobar to ask whether to overwrite an existing file with a new download.
 */
public class DownloadOverwriteInfoBar extends InfoBar {
    private static final String TAG = "DownloadOverwriteInfoBar";

    private final String mFileName;
    private final String mDirName;
    private final String mDirFullPath;

    @CalledByNative
    private static InfoBar createInfoBar(String fileName, String dirName, String dirFullPath) {
        return new DownloadOverwriteInfoBar(fileName, dirName, dirFullPath);
    }

    /**
     * Constructs DownloadOverwriteInfoBar.
     * @param fileName The file name. ex) example.jpg
     * @param dirName The dir name. ex) Downloads
     * @param dirFullPath The full dir path. ex) sdcards/Downloads
     */
    private DownloadOverwriteInfoBar(String fileName, String dirName, String dirFullPath) {
        super(null, R.drawable.infobar_downloading, null, null);
        mFileName = fileName;
        mDirName = dirName;
        mDirFullPath = dirFullPath;
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        int action = isPrimaryButton ? ActionType.OVERWRITE
                                     : ActionType.CREATE_NEW_FILE;
        onButtonClicked(action);
    }

    private CharSequence getMessageText(Context context) {
        String template = context.getString(R.string.download_overwrite_infobar_text);
        Intent intent = getIntentForDirectoryLaunch(mDirFullPath);
        return formatInfoBarMessage(context, template, mFileName, mDirName, intent);
    }

    /**
     * @param dirFullPath The full path of the directory to be launched.
     * @return An Android intent that can launch the directory.
     */
    private static Intent getIntentForDirectoryLaunch(String dirFullPath) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        Uri uri = Uri.parse(dirFullPath);
        if (uri == null) {
            return null;
        }
        intent.setDataAndType(uri, "*/*");
        return intent;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        Context context = layout.getContext();
        layout.setMessage(getMessageText(context));
        layout.setButtons(
                context.getString(R.string.download_overwrite_infobar_replace_file_button),
                context.getString(R.string.download_overwrite_infobar_create_new_file_button));
    }

    /**
     * Create infobar message in the form of CharSequence.
     *
     * @param context The context.
     * @param template The template CharSequence.
     * @param fileName The file name.
     * @param dirName The directory name.
     * @param dirNameIntent The intent to be launched when user touches the directory name link.
     * @return CharSequence formatted message for InfoBar.
     */
    private static CharSequence formatInfoBarMessage(final Context context, String template,
            String fileName, String dirName, final Intent dirNameIntent) {
        SpannableString formattedFileName = new SpannableString(fileName);
        formattedFileName.setSpan(new StyleSpan(Typeface.BOLD), 0, fileName.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString formattedDirName = new SpannableString(dirName);
        if (canResolveIntent(context, dirNameIntent)) {
            formattedDirName.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    context.startActivity(dirNameIntent);
                }
            }, 0, dirName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return TextUtils.expandTemplate(template, formattedFileName, formattedDirName);
    }

    private static boolean canResolveIntent(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfoList.size() > 0;
    }
}
