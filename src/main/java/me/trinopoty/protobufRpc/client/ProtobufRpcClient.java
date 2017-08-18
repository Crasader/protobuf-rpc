package me.trinopoty.protobufRpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client side aspect of Protobuf-RPC.
 */
@SuppressWarnings("WeakerAccess")
public final class ProtobufRpcClient {

    private static final Object sClientEventLoopLock = new Object();
    private static final AtomicInteger sClientEventLoopRefCount = new AtomicInteger(0);
    private static EventLoopGroup sClientEventLoopGroup;

    /**
     * The builder class for {@link ProtobufRpcClient} instance.
     * All configuration is done in this class.
     */
    @SuppressWarnings("unused")
    public static final class Builder {

        private Integer mMaxReceivePacketLength = null;
        private Long mDefaultReceiveTimeoutMillis = null;
        private boolean mEnableTrafficLogging = false;
        private String mLoggingName = null;
        private SslContext mSslContext = null;

        public Builder() {
        }

        /**
         * Set the maximum allowed receive packet length. Packets larger than this will be discarded.
         *
         * @param maxReceivePacketLength The maximum size of receive packets.
         * @return {@link ProtobufRpcClient} instance for chaining.
         */
        public Builder setMaxReceivePacketLength(int maxReceivePacketLength) {
            mMaxReceivePacketLength = maxReceivePacketLength;
            return this;
        }

        /**
         * Set the maximum receive timeout.
         *
         * @param defaultReceiveTimeoutMillis The maximum receive timeout in milliseconds.
         * @return {@link ProtobufRpcClient} instance for chaining.
         */
        public Builder setDefaultReceiveTimeoutMillis(long defaultReceiveTimeoutMillis) {
            mDefaultReceiveTimeoutMillis = defaultReceiveTimeoutMillis;
            return this;
        }

        /**
         * Enable or disable traffic logging. If logging is enabled, a logging name must be provided.
         *
         * @param enableTrafficLogging Value indicating whether traffic logging whould be enabled or disabled.
         * @return {@link ProtobufRpcClient} instance for chaining.
         */
        public Builder setEnableTrafficLogging(boolean enableTrafficLogging) {
            mEnableTrafficLogging = enableTrafficLogging;
            return this;
        }

        /**
         * Sets the name to use in log records.
         *
         * @param loggingName The name to use for all logs.
         * @return {@link ProtobufRpcClient} instance for chaining.
         */
        public Builder setLoggingName(String loggingName) {
            mLoggingName = loggingName;
            return this;
        }

        /**
         * Sets the SSL context for SSL connections.
         *
         * @param sslContext The SSL Context.
         * @return {@link ProtobufRpcClient} instance for chaining.
         */
        public Builder setSslContext(SslContext sslContext) {
            mSslContext = sslContext;
            return this;
        }

        /**
         * Build an instance of {@link ProtobufRpcClient} with the provided configuration.
         *
         * @return Instance of {@link ProtobufRpcClient} if successful.
         * @throws IllegalArgumentException If there is any error.
         */
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

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannel} instance
     * to communicate with the server.
     *
     * @param host The hostname of the server to connect to.
     * @param port The port of the server to connect to.
     *
     * @return Use the returned instance to communicate with the server.
     */
    public ProtobufRpcClientChannel getClientChannel(String host, int port) {
        return getClientChannel(host, port, false);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannel} instance
     * to communicate with the server.
     *
     * @param host The hostname of the server to connect to.
     * @param port The port of the server to connect to.
     * @param ssl Whether to encrypt the connection to the server.
     *
     * @return Use the returned instance to communicate with the server.
     */
    public ProtobufRpcClientChannel getClientChannel(String host, int port, boolean ssl) {
        return getClientChannel(new InetSocketAddress(host, port), ssl);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannel} instance
     * to communicate with the server.
     *
     * @param remoteAddress The address (host, port) of ther server to connect to.
     *
     * @return Use the returned instance to communicate with the server.
     */
    public ProtobufRpcClientChannel getClientChannel(InetSocketAddress remoteAddress) {
        return getClientChannel(remoteAddress, false);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannel} instance
     * to communicate with the server.
     *
     * @param remoteAddress The address (host, port) of ther server to connect to.
     * @param ssl Whether to encrypt the connection to the server.
     *
     * @return Use the returned instance to communicate with the server.
     */
    public ProtobufRpcClientChannel getClientChannel(InetSocketAddress remoteAddress, boolean ssl) {
        ChannelFuture channelFuture = (!ssl)? mBootstrap.connect(remoteAddress) : mSslBootstrap.connect(remoteAddress);
        channelFuture.syncUninterruptibly();
        if(channelFuture.isSuccess()) {
            return new RpcClientChannelImpl(this, channelFuture.channel(), mDefaultReceiveTimeoutMillis);
        } else {
            return null;
        }
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannelPool} instance.
     * Acquire {@link ProtobufRpcClientChannel} objects from the pool to communicate with the server.
     *
     * @param poolConfig The configuration parameters of the connection pool.
     * @param host The hostname of the server to connect to.
     * @param port The port of the server to connect to.
     *
     * @return Use the returned instance get {@link ProtobufRpcClientChannel} to communicate with the server.
     */
    public ProtobufRpcClientChannelPool getClientChannelPool(RpcClientChannelPoolConfig poolConfig, String host, int port) {
        return getClientChannelPool(poolConfig, host, port, false);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannelPool} instance.
     * Acquire {@link ProtobufRpcClientChannel} objects from the pool to communicate with the server.
     *
     * @param poolConfig The configuration parameters of the connection pool.
     * @param host The hostname of the server to connect to.
     * @param port The port of the server to connect to.
     * @param ssl Whether to encrypt the connection to the server.
     *
     * @return Use the returned instance get {@link ProtobufRpcClientChannel} to communicate with the server.
     */
    public ProtobufRpcClientChannelPool getClientChannelPool(RpcClientChannelPoolConfig poolConfig, String host, int port, boolean ssl) {
        return getClientChannelPool(poolConfig, new InetSocketAddress(host, port), ssl);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannelPool} instance.
     * Acquire {@link ProtobufRpcClientChannel} objects from the pool to communicate with the server.
     *
     * @param poolConfig The configuration parameters of the connection pool.
     * @param remoteAddress The address (host, port) of ther server to connect to.
     *
     * @return Use the returned instance get {@link ProtobufRpcClientChannel} to communicate with the server.
     */
    public ProtobufRpcClientChannelPool getClientChannelPool(RpcClientChannelPoolConfig poolConfig, InetSocketAddress remoteAddress) {
        return getClientChannelPool(poolConfig, remoteAddress, false);
    }

    /**
     * Connect to a remote server and return a {@link ProtobufRpcClientChannelPool} instance.
     * Acquire {@link ProtobufRpcClientChannel} objects from the pool to communicate with the server.
     *
     * @param poolConfig The configuration parameters of the connection pool.
     * @param remoteAddress The address (host, port) of ther server to connect to.
     * @param ssl Whether to encrypt the connection to the server.
     *
     * @return Use the returned instance get {@link ProtobufRpcClientChannel} to communicate with the server.
     */
    public ProtobufRpcClientChannelPool getClientChannelPool(RpcClientChannelPoolConfig poolConfig, InetSocketAddress remoteAddress, boolean ssl) {
        return new ProtobufRpcClientChannelPool(poolConfig, this, remoteAddress, ssl);
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