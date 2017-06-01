// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The metadata information of {@link CastSession}.
 */
public class CastSessionInfo {
    /**
     * The volume information of receivers.
     */
    public static class VolumeInfo {
        /**
         * The receiver volume.
         */
        public final double level;
        /**
         * Whether the receiver is muted.
         */
        public final boolean muted;

        /**
         * Use this class to construct an instance of {@link VolumeInfo}.
         */
        public static class Builder {
            private double mLevel = 0;
            private boolean mMuted = false;

            public Builder setLevel(double level) {
                mLevel = level;
                return this;
            }

            public Builder setMuted(boolean muted) {
                mMuted = muted;
                return this;
            }

            /**
             * Initializes the builder with the default values.
             */
            public Builder() {
            }

            public VolumeInfo build() {
                return new VolumeInfo(mLevel, mMuted);
            }
        }

        private VolumeInfo(double level, boolean muted) {
            this.level = level;
            this.muted = muted;
        }
    }

    /**
     * The receiver information.
     */
    public static class ReceiverInfo {
        /**
         * Label of the receiver.
         */
        public final String label;
        /**
         * The friendly name of the receiver.
         */
        public final String friendlyName;
        /**
         * Receiver capabilities.
         */
        public final List<String> capabilities;
        /**
         * Receiver volume information.
         */
        public final VolumeInfo volume;
        /**
         * The receiver device's active-input state.
         */
        public final int isActiveInput;
        /**
         * The display status of the receiver.
         */
        public final String displayStatus;
        /**
         * The type of the receiver.
         */
        public final String receiverType;

        /**
         * Use this class to create an instance of {@link ReceiverInfo}.
         */
        public static class Builder {
            private String mLabel = "";
            private String mFriendlyName = "";
            private List<String> mCapabilities = new ArrayList<String>();
            private VolumeInfo mVolume = null;
            private int mIsActiveInput = 0;
            private String mDisplayStatus = "";
            private String mReceiverType = "";

            public Builder setLabel(String label) {
                mLabel = label;
                return this;
            }

            public Builder setFriendlyName(String friendlyName) {
                mFriendlyName = friendlyName;
                return this;
            }

            public Builder addCapability(String capability) {
                mCapabilities.add(capability);
                return this;
            }

            public Builder addCapabilities(Collection<String> capabilities) {
                mCapabilities.addAll(capabilities);
                return this;
            }

            public Builder setVolume(VolumeInfo volume) {
                mVolume = volume;
                return this;
            }

            public Builder setIsActiveInput(int isActiveInput) {
                mIsActiveInput = isActiveInput;
                return this;
            }

            public Builder setDisplayStatus(String displayStatus) {
                mDisplayStatus = displayStatus;
                return this;
            }

            public Builder setReceiverType(String receiverType) {
                mReceiverType = receiverType;
                return this;
            }

            /**
             * Initializes the builder with the default values.
             */
            public Builder() {
            }

            public ReceiverInfo build() {
                return new ReceiverInfo(
                        mLabel,
                        mFriendlyName,
                        mCapabilities,
                        mVolume,
                        mIsActiveInput,
                        mDisplayStatus,
                        mReceiverType);
            }
        }

        private ReceiverInfo(
                String label,
                String friendlyName,
                List<String> capabilities,
                VolumeInfo volume,
                int isActiveInput,
                String displayStatus,
                String receiverType) {
            this.label = label;
            this.friendlyName = friendlyName;
            this.capabilities = capabilities;
            this.volume = volume;
            this.isActiveInput = isActiveInput;
            this.displayStatus = displayStatus;
            this.receiverType = receiverType;
        }
    }

    /**
     * The id of the {@link CastSession}.
     */
    public final String sessionId;
    /**
     * The application status (in Cast SDK) of the {@link CastSession}.
     */
    public final String statusText;
    /**
     * The receiver information of the {@link CastSession}.
     */
    public final ReceiverInfo receiver;
    /**
     * The namespaces registered in the {@link CastSession}.
     */
    public final List<String> namespaces;
    /**
     * The media in the {@link CastSession}.
     */
    public final List<String> media;
    /**
     * The status of the {@link CastSession}.
     */
    public final String status;
    /**
     * The tranport id of the {@link CastSession}.
     */
    public final String transportId;
    /**
     * The app id of the {@link CastSession}.
     */
    public final String appId;
    /**
     * The display name of the {@link CastSession}.
     */
    public final String displayName;

    /**
     * Use this class to create an instance of {@link CastSessionInfo}.
     */
    public static class Builder {
        private String mSessionId = "";
        private String mStatusText = "";
        private ReceiverInfo mReceiver = null;
        private List<String> mNamespaces = new ArrayList<String>();
        private List<String> mMedia = new ArrayList<String>();
        private String mStatus = "";
        private String mTransportId = "";
        private String mAppId = "";
        private String mDisplayName = "";

        public Builder setSessionId(String sessionId) {
            mSessionId = sessionId;
            return this;
        }

        public Builder setStatusText(String statusText) {
            mStatusText = statusText;
            return this;
        }

        public Builder setReceiver(ReceiverInfo receiver) {
            mReceiver = receiver;
            return this;
        }

        public Builder addNamespace(String namespace) {
            mNamespaces.add(namespace);
            return this;
        }

        public Builder addNamespaces(Collection<String> namespaces) {
            mNamespaces.addAll(namespaces);
            return this;
        }

        public Builder addMedia(String namespace) {
            mMedia.add(namespace);
            return this;
        }

        public Builder setStatus(String status) {
            mStatus = status;
            return this;
        }

        public Builder setTransportId(String transportId) {
            mTransportId = transportId;
            return this;
        }

        public Builder setAppId(String appId) {
            mAppId = appId;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mDisplayName = displayName;
            return this;
        }

        /**
         * Initializes the builder with the default values.
         */
        public Builder() {
        }

        public CastSessionInfo build() {
            return new CastSessionInfo(
                    mSessionId,
                    mStatusText,
                    mReceiver,
                    mNamespaces,
                    mMedia,
                    mStatus,
                    mTransportId,
                    mAppId,
                    mDisplayName);
        }
    }

    private CastSessionInfo(
            String sessionId,
            String statusText,
            ReceiverInfo receiver,
            List<String> namespaces,
            List<String> media,
            String status,
            String transportId,
            String appId,
            String displayName) {
        this.sessionId = sessionId;
        this.statusText = statusText;
        this.receiver = receiver;
        this.namespaces = namespaces;
        this.media = media;
        this.status = status;
        this.transportId = transportId;
        this.appId = appId;
        this.displayName = displayName;
    }
}
