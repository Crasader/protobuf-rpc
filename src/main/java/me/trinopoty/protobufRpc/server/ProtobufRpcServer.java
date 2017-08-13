package me.trinopoty.protobufRpc.server;

import io.netty.handler.ssl.SslContext;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The server side aspect of Protobuf-RPC.
 */
public final class ProtobufRpcServer {

    public static final class Builder {

        private InetSocketAddress mLocalAddress = null;
        private InetSocketAddress mSslLocalAddress = null;
        private SslContext mSslContext = null;

        private int mBacklogCount = 5;
        private boolean mKeepAlive = true;
        private Long mMaxDecoderPacketLength = null;
        private boolean mEnableTrafficLogging = false;
        private String mLoggingName = null;

        public Builder() {
        }

        public Builder setLocalPort(int port) {
            mLocalAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            return this;
        }

        public Builder setLocalAddress(InetSocketAddress localAddress) {
            mLocalAddress = localAddress;
            return this;
        }

        public Builder setSslLocalPort(int port, SslContext sslContext) {
            mSslLocalAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            mSslContext = sslContext;
            return this;
        }

        public Builder setSslLocalAddress(InetSocketAddress localAddress, SslContext sslContext) {
            mSslLocalAddress = localAddress;
            mSslContext = sslContext;
            return this;
        }

        public Builder setBacklogCount(int backlogCount) {
            mBacklogCount = backlogCount;
            return this;
        }

        public Builder setKeepAlive(boolean keepAlive) {
            mKeepAlive = keepAlive;
            return this;
        }

        public Builder setMaxDecoderPacketLength(long maxDecoderPacketLength) {
            mMaxDecoderPacketLength = maxDecoderPacketLength;
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

        public ProtobufRpcServer build() {
            return null;
        }
    }

    private ProtobufRpcServer() {
    }
}