package me.trinopoty.protobufRpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client side aspect of Protobuf-RPC.
 */
public final class ProtobufRpcClient {

    private static final Object sClientEventLoopLock = new Object();
    private static final AtomicInteger sClientEventLoopRefCount = new AtomicInteger(0);
    private static EventLoopGroup sClientEventLoopGroup;

    public static final class Builder {

        private Integer mMaxReceivePacketLength = null;
        private Long mDefaultReceiveTimeoutMillis = null;
        private boolean mEnableTrafficLogging = false;
        private String mLoggingName = null;
        private SslContext mSslContext = null;

        public Builder() {
        }

        public Builder setMaxReceivePacketLength(int maxReceivePacketLength) {
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
            if(mEnableTrafficLogging && (mLoggingName == null)) {
                throw new IllegalArgumentException("Logging name must be provided if logging is enabled.");
            }

            ProtobufRpcClient protobufRpcClient = new ProtobufRpcClient(mDefaultReceiveTimeoutMillis);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(acquireClientEventLoopGroup());
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.handler(new RpcClientChannelInitializer(
                    mMaxReceivePacketLength,
                    null,
                    mEnableTrafficLogging,
                    mLoggingName));
            protobufRpcClient.setBootstrap(bootstrap);

            if(mSslContext != null) {
                Bootstrap sslBootstrap = new Bootstrap();
                sslBootstrap.group(acquireClientEventLoopGroup());
                sslBootstrap.channel(NioSocketChannel.class);
                sslBootstrap.handler(new RpcClientChannelInitializer(
                        mMaxReceivePacketLength,
                        mSslContext,
                        mEnableTrafficLogging,
                        mLoggingName));
                protobufRpcClient.setSslBootstrap(sslBootstrap);
            }

            return protobufRpcClient;
        }
    }

    private final Long mDefaultReceiveTimeoutMillis;

    private final RpcServiceCollector mRpcServiceCollector = new RpcServiceCollector();

    private Bootstrap mBootstrap;
    private Bootstrap mSslBootstrap;

    private ProtobufRpcClient(Long defaultReceiveTimeoutMillis) {
        mDefaultReceiveTimeoutMillis = defaultReceiveTimeoutMillis;
    }

    @SuppressWarnings("Duplicates")
    private static EventLoopGroup acquireClientEventLoopGroup() {
        synchronized (sClientEventLoopLock) {
            if (sClientEventLoopRefCount.get() == 0) {
                sClientEventLoopGroup = new NioEventLoopGroup();
            }

            sClientEventLoopRefCount.incrementAndGet();
        }

        return sClientEventLoopGroup;
    }

    @SuppressWarnings("Duplicates")
    private static void returnClientEventLoopGroup() {
        synchronized (sClientEventLoopLock) {
            if (sClientEventLoopRefCount.decrementAndGet() == 0) {
                sClientEventLoopGroup.shutdownGracefully();
                sClientEventLoopGroup = null;
            }
        }
    }

    public RpcClientChannel getClientChannel(String host, int port) {
        return getClientChannel(host, port, false);
    }

    public RpcClientChannel getClientChannel(String host, int port, boolean ssl) {
        return getClientChannel(new InetSocketAddress(host, port), ssl);
    }

    public RpcClientChannel getClientChannel(InetSocketAddress remoteAddress) {
        return getClientChannel(remoteAddress, false);
    }

    public RpcClientChannel getClientChannel(InetSocketAddress remoteAddress, boolean ssl) {
        ChannelFuture channelFuture = (!ssl)? mBootstrap.connect(remoteAddress) : mSslBootstrap.connect(remoteAddress);
        channelFuture.syncUninterruptibly();
        if(channelFuture.isSuccess()) {
            return new RpcClientChannelImpl(this, channelFuture.channel(), mDefaultReceiveTimeoutMillis);
        } else {
            return null;
        }
    }

    /**
     * Close this instance and all server connections.
     */
    public void close() {
        returnClientEventLoopGroup();
    }

    RpcServiceCollector getRpcServiceCollector() {
        return mRpcServiceCollector;
    }

    private void setBootstrap(Bootstrap bootstrap) {
        mBootstrap = bootstrap;
    }

    private void setSslBootstrap(Bootstrap bootstrap) {
        mSslBootstrap = bootstrap;
    }
}