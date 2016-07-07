// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.crypto.CipherFactory;
import org.chromium.content_public.browser.WebContents;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * Object that contains the state of a tab, including its navigation history.
 */
public class TabState {
    private static final String TAG = "TabState";

    public static final String SAVED_TAB_STATE_FILE_PREFIX = "tab";
    public static final String SAVED_TAB_STATE_FILE_PREFIX_INCOGNITO = "cryptonito";

    /**
     * Version number of the format used to save the WebContents navigation history, as returned by
     * nativeGetContentsStateAsByteBuffer(). Version labels:
     *   0 - Chrome m18
     *   1 - Chrome m25
     *   2 - Chrome m26+
     */
    public static final int CONTENTS_STATE_CURRENT_VERSION = 2;

    /** Special value for mTimestampMillis. */
    private static final long TIMESTAMP_NOT_SET = -1;

    /** Checks if the TabState header is loaded properly. */
    private static final long KEY_CHECKER = 0;

    /** Overrides the Chrome channel/package name to test a variant channel-specific behaviour. */
    private static String sChannelNameOverrideForTest;

    /** Contains the state for a WebContents. */
    public static class WebContentsState {
        private final ByteBuffer mBuffer;
        private int mVersion;

        public WebContentsState(ByteBuffer buffer) {
            mBuffer = buffer;
        }

        public ByteBuffer buffer() {
            return mBuffer;
        }

        public int version() {
            return mVersion;
        }

        public void setVersion(int version) {
            mVersion = version;
        }

        /**
         * Creates a WebContents from the buffer.
         * @param isHidden Whether or not the tab initially starts hidden.
         * @return Pointer A WebContents object.
         */
        public WebContents restoreContentsFromByteBuffer(boolean isHidden) {
            return nativeRestoreContentsFromByteBuffer(mBuffer, mVersion, isHidden);
        }

        /**
         * Creates a WebContents for the ContentsState and adds it as an historical tab, then
         * deletes the WebContents.
         */
        public void createHistoricalTab() {
            nativeCreateHistoricalTab(mBuffer, mVersion);
        }
    }

    /** Deletes the native-side portion of the buffer. */
    public static class WebContentsStateNative extends WebContentsState {
        private final Handler mHandler;

        public WebContentsStateNative(ByteBuffer buffer) {
            super(buffer);
            this.mHandler = new Handler();
        }

        @Override
        protected void finalize() {
            assert mHandler != null;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    nativeFreeWebContentsStateBuffer(buffer());
                }
            });
        }
    }

    /** Navigation history of the WebContents. */
    public WebContentsState contentsState;
    public int parentId = Tab.INVALID_TAB_ID;
    public long syncId;

    public long timestampMillis = TIMESTAMP_NOT_SET;
    public String openerAppId;
    public boolean shouldPreserve;

    /** Whether this TabState was created from a file containing info about an incognito Tab. */
    protected boolean mIsIncognito;

    /** @return Whether a Stable channel build of Chrome is being used. */
    private static boolean isStableChannelBuild() {
        if ("stable".equals(sChannelNameOverrideForTest)) return true;
        return ChromeVersionInfo.isStableBuild();
    }

    /**
     * Restore a TabState file for a particular Tab.  Checks if the Tab exists as a regular tab
     * before searching for an encrypted version.
     * @param stateFolder Folder containing the TabState files.
     * @param id ID of the Tab to restore.
     * @return TabState that has been restored, or null if it failed.
     */
    public static TabState restoreTabState(File stateFolder, int id) {
        // First try finding an unencrypted file.
        boolean encrypted = false;
        File file = getTabStateFile(stateFolder, id, encrypted);

        // If that fails, try finding the encrypted version.
        if (!file.exists()) {
            encrypted = true;
            file = getTabStateFile(stateFolder, id, encrypted);
        }

        // If they both failed, there's nothing to read.
        if (!file.exists()) return null;

        // If one of them passed, open the file input stream and read the state contents.
        return restoreTabState(file, encrypted);
    }

    /**
     * Restores a particular TabState file from storage.
     * @param tabFile Location of the TabState file.
     * @param isIncognito Whether the Tab is incognito or not.
     * @return TabState that has been restored, or null if it failed.
     */
    public static TabState restoreTabState(File tabFile, boolean isIncognito) {
        FileInputStream stream = null;
        TabState tabState = null;
        try {
            stream = new FileInputStream(tabFile);
            tabState = TabState.readState(stream, isIncognito);
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Failed to restore tab state for tab: " + tabFile);
        } catch (IOException exception) {
            Log.e(TAG, "Failed to restore tab state.", exception);
        } finally {
            StreamUtil.closeQuietly(stream);
        }
        return tabState;
    }

    /**
     * Restores a particular TabState file from storage.
     * @param input Location of the TabState file.
     * @param encrypted Whether the file is encrypted or not.
     * @return TabState that has been restored, or null if it failed.
     */
    public static TabState readState(FileInputStream input, boolean encrypted) throws IOException {
        DataInputStream stream = null;
        if (encrypted) {
            Cipher cipher = CipherFactory.getInstance().getCipher(Cipher.DECRYPT_MODE);
            if (cipher != null) {
                stream = new DataInputStream(new CipherInputStream(input, cipher));
            }
        }
        if (stream == null) {
            stream = new DataInputStream(input);
        }
        try {
            if (encrypted && stream.readLong() != KEY_CHECKER) {
                // Got the wrong key, skip the file
                return null;
            }
            TabState tabState = new TabState();
            tabState.timestampMillis = stream.readLong();
            int size = stream.readInt();
            if (encrypted) {
                // If it's encrypted, we have to read the stream normally to apply the cipher.
                byte[] state = new byte[size];
                stream.readFully(state);
                tabState.contentsState = new WebContentsState(ByteBuffer.allocateDirect(size));
                tabState.contentsState.buffer().put(state);
            } else {
                // If not, we can mmap the file directly, saving time and copies into the java heap.
                FileChannel channel = input.getChannel();
                tabState.contentsState = new WebContentsState(
                        channel.map(MapMode.READ_ONLY, channel.position(), size));
                // Skip ahead to avoid re-reading data that mmap'd.
                long skipped = input.skip(size);
                if (skipped != size) {
                    Log.e(TAG, "Only skipped " + skipped + " bytes when " + size + " should've "
                            + "been skipped. Tab restore may fail.");
                }
            }
            tabState.parentId = stream.readInt();
            try {
                tabState.openerAppId = stream.readUTF();
                if ("".equals(tabState.openerAppId)) tabState.openerAppId = null;
            } catch (EOFException eof) {
                // Could happen if reading a version of a TabState that does not include the app id.
                Log.w(TAG, "Failed to read opener app id state from tab state");
            }
            try {
                tabState.contentsState.setVersion(stream.readInt());
            } catch (EOFException eof) {
                // On the stable channel, the first release is version 18. For all other channels,
                // chrome 25 is the first release.
                tabState.contentsState.setVersion(isStableChannelBuild() ? 0 : 1);

                // Could happen if reading a version of a TabState that does not include the
                // version id.
                Log.w(TAG, "Failed to read saved state version id from tab state. Assuming "
                        + "version " + tabState.contentsState.version());
            }
            try {
                tabState.syncId = stream.readLong();
            } catch (EOFException eof) {
                tabState.syncId = 0;
                // Could happen if reading a version of TabState without syncId.
                Log.w(TAG, "Failed to read syncId from tab state. Assuming syncId is: 0");
            }
            try {
                tabState.shouldPreserve = stream.readBoolean();
            } catch (EOFException eof) {
                // Could happen if reading a version of TabState without this flag set.
                tabState.shouldPreserve = false;
                Log.w(TAG, "Failed to read shouldPreserve flag from tab state. "
                        + "Assuming shouldPreserve is false");
            }
            tabState.mIsIncognito = encrypted;
            return tabState;
        } finally {
            stream.close();
        }
    }

    /**
     * Writes the TabState to disk. This method may be called on either the UI or background thread.
     * @param output Stream to write the tab's state to.
     * @param state State object obtained from from {@link Tab#getState()}.
     * @param encrypted Whether or not the TabState should be encrypted.
     */
    public static void saveState(FileOutputStream output, TabState state, boolean encrypted)
            throws IOException {
        if (state == null || state.contentsState == null) {
            return;
        }

        DataOutputStream stream;
        if (encrypted) {
            Cipher cipher = CipherFactory.getInstance().getCipher(Cipher.ENCRYPT_MODE);
            if (cipher != null) {
                stream = new DataOutputStream(new CipherOutputStream(output, cipher));
            } else {
                // If cipher is null, getRandomBytes failed, which means encryption is meaningless.
                // Therefore, do not save anything. This will cause users to lose Incognito state in
                // certain cases. That is annoying, but is better than failing to provide the
                // guarantee of Incognito Mode.
                return;
            }
        } else {
            stream = new DataOutputStream(output);
        }

        try {
            if (encrypted) {
                stream.writeLong(KEY_CHECKER);
            }
            stream.writeLong(state.timestampMillis);
            state.contentsState.buffer().rewind();
            stream.writeInt(state.contentsState.buffer().remaining());
            if (encrypted) {
                byte[] bytes = new byte[state.contentsState.buffer().remaining()];
                state.contentsState.buffer().get(bytes);
                stream.write(bytes);
            } else {
                output.getChannel().write(state.contentsState.buffer());
            }
            stream.writeInt(state.parentId);
            stream.writeUTF(state.openerAppId != null ? state.openerAppId : "");
            stream.writeInt(state.contentsState.version());
            stream.writeLong(state.syncId);
            stream.writeBoolean(state.shouldPreserve);
        } finally {
            StreamUtil.closeQuietly(stream);
        }
    }

    /**
     * Returns a File corresponding to the given TabState.
     * @param directory Directory containing the TabState files.
     * @param tabId ID of the TabState to delete.
     * @param encrypted Whether the TabState is encrypted.
     * @return File corresponding to the given TabState.
     */
    public static File getTabStateFile(File directory, int tabId, boolean encrypted) {
        return new File(directory, getTabStateFilename(tabId, encrypted));
    }

    /**
     * Deletes the TabState corresponding to the given Tab.
     * @param directory Directory containing the TabState files.
     * @param tabId ID of the TabState to delete.
     * @param encrypted Whether the TabState is encrypted.
     */
    public static void deleteTabState(File directory, int tabId, boolean encrypted) {
        File file = getTabStateFile(directory, tabId, encrypted);
        if (file.exists() && !file.delete()) Log.e(TAG, "Failed to delete TabState: " + file);
    }

    /** @return Title currently being displayed in the saved state's current entry. */
    public String getDisplayTitleFromState() {
        return nativeGetDisplayTitleFromByteBuffer(contentsState.buffer(), contentsState.version());
    }

    /** @return URL currently being displayed in the saved state's current entry. */
    public String getVirtualUrlFromState() {
        return nativeGetVirtualUrlFromByteBuffer(contentsState.buffer(), contentsState.version());
    }

    /** @return Whether an incognito TabState was loaded by {@link #readState}. */
    public boolean isIncognito() {
        return mIsIncognito;
    }

    /**
     * Creates a WebContentsState for a tab that will be loaded lazily.
     * @param url URL that is pending.
     * @param referrerUrl URL for the referrer.
     * @param referrerPolicy Policy for the referrer.
     * @param isIncognito Whether or not the state is meant to be incognito (e.g. encrypted).
     * @return ByteBuffer that represents a state representing a single pending URL.
     */
    public static ByteBuffer createSingleNavigationStateAsByteBuffer(
            String url, String referrerUrl, int referrerPolicy, boolean isIncognito) {
        return nativeCreateSingleNavigationStateAsByteBuffer(
                url, referrerUrl, referrerPolicy, isIncognito);
    }

    /**
     * Returns the WebContents' state as a ByteBuffer.
     * @param tab Tab to pickle.
     * @return ByteBuffer containing the state of the WebContents.
     */
    public static ByteBuffer getContentsStateAsByteBuffer(Tab tab) {
        return nativeGetContentsStateAsByteBuffer(tab);
    }

    /**
     * Generates the name of the state file that should represent the Tab specified by {@code id}
     * and {@code encrypted}.
     * @param id        The id of the {@link Tab} to save.
     * @param encrypted Whether or not the tab is incognito and should be encrypted.
     * @return          The name of the file the Tab state should be saved to.
     */
    public static String getTabStateFilename(int id, boolean encrypted) {
        return (encrypted ? SAVED_TAB_STATE_FILE_PREFIX_INCOGNITO : SAVED_TAB_STATE_FILE_PREFIX)
                + id;
    }

    /**
     * Parse the tab id and whether the tab is incognito from the tab state filename.
     * @param name The given filename for the tab state file.
     * @return A {@link Pair} with tab id and incognito state read from the filename.
     */
    public static Pair<Integer, Boolean> parseInfoFromFilename(String name) {
        try {
            if (name.startsWith(SAVED_TAB_STATE_FILE_PREFIX_INCOGNITO)) {
                int id = Integer.parseInt(
                        name.substring(SAVED_TAB_STATE_FILE_PREFIX_INCOGNITO.length()));
                return Pair.create(id, true);
            } else if (name.startsWith(SAVED_TAB_STATE_FILE_PREFIX)) {
                int id = Integer.parseInt(
                        name.substring(SAVED_TAB_STATE_FILE_PREFIX.length()));
                return Pair.create(id, false);
            }
        } catch (NumberFormatException ex) {
            // Expected for files not related to tab state.
        }
        return null;
    }

    /**
     * Overrides the channel name for testing.
     * @param name Channel to use.
     */
    @VisibleForTesting
    public static void setChannelNameOverrideForTest(String name) {
        sChannelNameOverrideForTest = name;
    }

    private static native WebContents nativeRestoreContentsFromByteBuffer(
            ByteBuffer buffer, int savedStateVersion, boolean initiallyHidden);

    private static native ByteBuffer nativeGetContentsStateAsByteBuffer(Tab tab);

    private static native ByteBuffer nativeCreateSingleNavigationStateAsByteBuffer(
            String url, String referrerUrl, int referrerPolicy, boolean isIncognito);

    private static native String nativeGetDisplayTitleFromByteBuffer(
            ByteBuffer state, int savedStateVersion);

    private static native String nativeGetVirtualUrlFromByteBuffer(
            ByteBuffer state, int savedStateVersion);

    private static native void nativeFreeWebContentsStateBuffer(ByteBuffer buffer);

    private static native void nativeCreateHistoricalTab(ByteBuffer state, int savedStateVersion);
}