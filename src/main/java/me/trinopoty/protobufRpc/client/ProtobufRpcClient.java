package me.trinopoty.protobufRpc.client;

import io.netty.handler.ssl.SslContext;

/**
 * The client side aspect of Protobuf-RPC.
 */
public final class ProtobufRpcClient {

    public static final class Builder {

        private Long mMaxReceivePacketLength = null;
        private Long mDefaultReceiveTimeoutMillis = null;
        private boolean mEnableTrafficLogging = false;
        private String mLoggingName = null;
        private SslContext mSslContext = null;

        public Builder() {
        }

        public Builder setMaxReceivePacketLength(long maxReceivePacketLength) {
            mMaxReceivePacketLength = maxReceivePacketLength;
            return this;
        }

        public Builder setDefaultReceiveTimeoutMillis(long defaultReceiveTimeoutMillis) {
            mDefaultReceiveTimeoutMillis = defaultReceiveTimeoutMillis;
            return this;
        }

        public Builder setEnableTrafficLogging(boolean enableTrafficLogging) {
            mEnableTrafficLogging = enableTrafficLogging;
            return this;
        }

        public Builder setLoggingName(String loggingName) {
            mLoggingName = loggingName;
            return this;
        }

        public Builder setSslContext(SslContext sslContext) {
            mSslContext = sslContext;
            return this;
        }

        public ProtobufRpcClient build() {
            return null;
        }
    }

    private ProtobufRpcClient() {
    }
}