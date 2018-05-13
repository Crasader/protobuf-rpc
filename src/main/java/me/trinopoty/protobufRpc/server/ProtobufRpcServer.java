package me.trinopoty.protobufRpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.DisconnectReason;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.util.Pair;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server side aspect of Protobuf-RPC.
 */
public final class ProtobufRpcServer {

    private static final Object sServerEventLoopLock = new Object();
    private static final AtomicInteger sServerEventLoopRefCount = new AtomicInteger(0);
    private static EventLoopGroup sServerEventLoopGroup;

    private static final Object sClientEventLoopLock = new Object();
    private static final AtomicInteger sClientEventLoopRefCount = new AtomicInteger(0);
    private static EventLoopGroup sClientEventLoopGroup;

    /**
     * The builder class for {@link ProtobufRpcServer} instance.
     * All configuration is done in this class.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static final class Builder {

        private InetSocketAddress mLocalAddress = null;
        private InetSocketAddress mSslLocalAddress = null;
        private SslContext mSslContext = null;

        private int mBacklogCount = 5;
        private Integer mMaxReceivePacketLength = null;
        private boolean mEnableRpcLogging = false;
        private boolean mEnableTrafficLogging = false;
        private String mLoggingName = null;

        private final RpcServiceCollector mRpcServiceCollector = new RpcServiceCollector();

        public Builder() {
        }

        /**
         * Sets the local address to listen for non-SSL connections.
         *
         * @param localAddress The address to listen for non-SSL connections.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setLocalAddress(InetSocketAddress localAddress) {
            mLocalAddress = localAddress;
            return this;
        }

        /**
         * Sets the local address to listen for SSL connections.
         *
         * @param localAddress The address to listen for SSL connections.
         * @param sslContext The SSL context to use for SSL connections.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setSslLocalAddress(InetSocketAddress localAddress, SslContext sslContext) {
            mSslLocalAddress = localAddress;
            mSslContext = sslContext;
            return this;
        }

        /**
         * Set the TCP connection backlog count.
         *
         * @param backlogCount The TCP connection backlog count.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setBacklogCount(int backlogCount) {
            mBacklogCount = backlogCount;
            return this;
        }

        /**
         * Set the maximum allowed receive packet length. Packets larger than this will be discarded.
         *
         * @param maxReceivePacketLength The maximum size of receive packets.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setMaxReceivePacketLength(int maxReceivePacketLength) {
            mMaxReceivePacketLength = maxReceivePacketLength;
            return this;
        }

        /**
         * Enable or disable rpc logging. If logging is enabled, a logging name must be provided.
         *
         * @param enableRpcLogging Value indicating whether rpc logging is to be enabled or disabled.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setEnableRpcLogging(boolean enableRpcLogging) {
            mEnableRpcLogging = enableRpcLogging;
            return this;
        }

        /**
         * Enable or disable traffic logging. If logging is enabled, a logging name must be provided.
         *
         * @param enableTrafficLogging Value indicating whether traffic logging would be enabled or disabled.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setEnableTrafficLogging(boolean enableTrafficLogging) {
            mEnableTrafficLogging = enableTrafficLogging;
            return this;
        }

        /**
         * Sets the name to use in log records.
         *
         * @param loggingName The name to use for all logs.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         */
        public Builder setLoggingName(String loggingName) {
            mLoggingName = loggingName;
            return this;
        }

        /**
         * Add the implementation class of a service interface.
         * @param classOfService The interface defining the service.
         * @param implOfService The class implementing the interface of the service.
         *
         * @throws DuplicateRpcServiceIdentifierException If two interfaces have same {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} value
         * @throws DuplicateRpcMethodIdentifierException If two methods in the same interface have same {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} value
         * @throws MissingRpcIdentifierException If {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} is missing form an interface or method
         * @throws IllegalMethodSignatureException If the signature, parameter and return type, of a method is wrong
         * @throws ServiceConstructorNotFoundException If the signature of the implementation class constructor is wrong
         */
        public synchronized <T> void addServiceImplementation(Class<T> classOfService, Class<? extends T> implOfService) throws DuplicateRpcServiceIdentifierException, MissingRpcIdentifierException, DuplicateRpcMethodIdentifierException, IllegalMethodSignatureException, ServiceConstructorNotFoundException {
            mRpcServiceCollector.parseServiceInterface(classOfService, false);
            RpcServiceCollector.RpcServiceInfo serviceInfo = mRpcServiceCollector.getServiceInfo(classOfService);
            assert serviceInfo != null;

            serviceInfo.setImplClass(implOfService);
            serviceInfo.setImplClassConstructor(getServiceImplementationConstructor(implOfService));
        }

        /**
         * Register OOB interface.
         *
         * @param oobClass List of OOB interfaces.
         * @return {@link ProtobufRpcServer.Builder} instance for chaining.
         *
         * @throws DuplicateRpcServiceIdentifierException If two interfaces have same {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} value
         * @throws DuplicateRpcMethodIdentifierException If two methods in the same interface have same {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} value
         * @throws MissingRpcIdentifierException If {@link me.trinopoty.protobufRpc.annotation.RpcIdentifier} is missing form an interface or method
         * @throws IllegalMethodSignatureException If the signature, parameter and return type, of a method is wrong
         */
        public Builder registerOob(Class... oobClass) throws DuplicateRpcServiceIdentifierException, MissingRpcIdentifierException, DuplicateRpcMethodIdentifierException, IllegalMethodSignatureException {
            for(Class aOobClass : oobClass) {
                mRpcServiceCollector.parseServiceInterface(aOobClass, true);
            }
            return this;
        }

        /**
         * Build an instance of {@link ProtobufRpcServer} with the provided configuration.
         *
         * @return Instance of {@link ProtobufRpcServer} if successful.
         * @throws IllegalArgumentException On error.
         */
        public ProtobufRpcServer build() {
            if((mLocalAddress == null) && (mSslLocalAddress == null)) {
                throw new IllegalArgumentException("LocalAddress must be provided.");
            }
            if(mEnableRpcLogging && (mLoggingName == null)) {
                throw new IllegalArgumentException("Logging name must be provided if RPC logging is enabled.");
            }
            if(mEnableTrafficLogging && (mLoggingName == null)) {
                throw new IllegalArgumentException("Logging name must be provided if traffic logging is enabled.");
            }

            ProtobufRpcServer protobufRpcServer = new ProtobufRpcServer(mRpcServiceCollector);

            if(mLocalAddress.equals(mSslLocalAddress)) {
                throw new IllegalArgumentException("SSL and non-SSL address cannot be same.");
            }

            if(mLocalAddress != null) {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(acquireServerEventLoopGroup(), acquireClientEventLoopGroup());
                serverBootstrap.channel(NioServerSocketChannel.class);
                serverBootstrap.childHandler(new RpcServerChannelInitializer(
                        protobufRpcServer,
                        mMaxReceivePacketLength,
                        null,
                        mLoggingName,
                        mEnableRpcLogging,
                        mEnableTrafficLogging
                ));
                serverBootstrap.option(ChannelOption.SO_BACKLOG, mBacklogCount);

                protobufRpcServer.setServerBootstrap(mLocalAddress, serverBootstrap);
            }

            if(mSslLocalAddress != null) {
                ServerBootstrap sslServerBootstrap = new ServerBootstrap();
                sslServerBootstrap.group(acquireServerEventLoopGroup(), acquireClientEventLoopGroup());
                sslServerBootstrap.channel(NioServerSocketChannel.class);
                sslServerBootstrap.childHandler(new RpcServerChannelInitializer(
                        protobufRpcServer,
                        mMaxReceivePacketLength,
                        mSslContext,
                        mLoggingName,
                        mEnableRpcLogging,
                        mEnableTrafficLogging
                ));
                sslServerBootstrap.option(ChannelOption.SO_BACKLOG, mBacklogCount);

                protobufRpcServer.setSslServerBootstrap(mSslLocalAddress, sslServerBootstrap);
            }

            return protobufRpcServer;
        }

        private static Pair<RpcServiceCollector.RpcServiceInfo.ConstructorType, Constructor> getServiceImplementationConstructor(Class implClass) throws ServiceConstructorNotFoundException {
            RpcServiceCollector.RpcServiceInfo.ConstructorType constructorType = null;
            Constructor constructor = null;

            try {
                //noinspection unchecked
                constructor = implClass.getDeclaredConstructor(ProtobufRpcServerChannel.class);
                constructorType = RpcServiceCollector.RpcServiceInfo.ConstructorType.PARAMETERIZED;
            } catch (NoSuchMethodException ignore) {
            }

            if(constructor == null) {
                try {
                    //noinspection unchecked
                    constructor = implClass.getDeclaredConstructor();
                    constructorType = RpcServiceCollector.RpcServiceInfo.ConstructorType.DEFAULT;
                } catch (NoSuchMethodException ignore) {
                }
            }

            if(constructor == null) {
                throw new ServiceConstructorNotFoundException(String.format("Class<%s> does not have a valid constructor.", implClass.getName()));
            }

            if((constructor.getModifiers() & Modifier.PUBLIC) != Modifier.PUBLIC) {
                throw new ServiceConstructorNotFoundException(String.format("Class<%s> does not have a valid constructor.", implClass.getName()));
            }

            return new Pair<>(constructorType, constructor);
        }
    }

    private final RpcServiceCollector mRpcServiceCollector;

    private InetSocketAddress mLocalAddress = null;
    private ServerBootstrap mServerBootstrap = null;
    private Channel mServerChannel = null;
    private InetSocketAddress mActualLocalAddress = null;

    private InetSocketAddress mSslLocalAddress = null;
    private ServerBootstrap mSslServerBootstrap = null;
    private Channel mSslServerChannel = null;
    private InetSocketAddress mActualSslLocalAddress = null;

    private boolean mServerStarted = false;
    private ProtobufRpcServerChannelDisconnectListener mChannelDisconnectListener = null;

    private ProtobufRpcServer(RpcServiceCollector rpcServiceCollector) {
        mRpcServiceCollector = rpcServiceCollector;
    }

    @SuppressWarnings("Duplicates")
    private static EventLoopGroup acquireServerEventLoopGroup() {
        synchronized (sServerEventLoopLock) {
            if (sServerEventLoopRefCount.get() == 0) {
                sServerEventLoopGroup = new NioEventLoopGroup();
            }

            sServerEventLoopRefCount.incrementAndGet();
        }

        return sServerEventLoopGroup;
    }

    @SuppressWarnings("Duplicates")
    private static void returnServerEventLoopGroup() {
        synchronized (sServerEventLoopLock) {
            if (sServerEventLoopRefCount.decrementAndGet() == 0) {
                sServerEventLoopGroup.shutdownGracefully();
                sServerEventLoopGroup = null;
            }
        }
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
     * Bind to ports and start this server.
     * Do nothing if already running.
     */
    @SuppressWarnings("Duplicates")
    public synchronized void startServer() {
        if(mServerStarted) {
            return;
        }

        if(mLocalAddress != null) {
            ChannelFuture channelFuture = mServerBootstrap.bind(mLocalAddress).syncUninterruptibly();
            if(channelFuture.isSuccess()) {
                mServerChannel = channelFuture.channel();
                mActualLocalAddress = (InetSocketAddress) mServerChannel.localAddress();
            }
        }
        if(mSslLocalAddress != null) {
            ChannelFuture channelFuture = mSslServerBootstrap.bind(mSslLocalAddress).syncUninterruptibly();
            if(channelFuture.isSuccess()) {
                mSslServerChannel = channelFuture.channel();
                mActualSslLocalAddress = (InetSocketAddress) mSslServerChannel.localAddress();
            }
        }

        mServerStarted = true;
    }

    /**
     * Stop this server and unbind from ports.
     */
    @SuppressWarnings("Duplicates")
    public synchronized void stopServer() {
        if(!mServerStarted) {
            return;
        }

        if(mServerChannel != null) {
            mServerChannel.close().syncUninterruptibly();
            mServerChannel = null;
            mActualLocalAddress = null;

            returnServerEventLoopGroup();
            returnClientEventLoopGroup();
        }
        if(mSslServerChannel != null) {
            mSslServerChannel.close().syncUninterruptibly();
            mSslServerChannel = null;
            mActualSslLocalAddress = null;

            returnServerEventLoopGroup();
            returnClientEventLoopGroup();
        }

        mServerStarted = false;
    }

    public void setChannelDisconnectListener(ProtobufRpcServerChannelDisconnectListener channelDisconnectListener) {
        mChannelDisconnectListener = channelDisconnectListener;
    }

    /**
     * Get the actual local address of the server.
     *
     * @return Local address of the server.
     * @throws IllegalStateException If server is not started.
     */
    @SuppressWarnings("unused")
    public InetSocketAddress getActualLocalAddress() {
        if(!mServerStarted) {
            throw new IllegalArgumentException("Server not started.");
        }
        return mActualLocalAddress;
    }

    /**
     * Get the actual local address of the SSL server.
     *
     * @return Local address of the SSL server.
     * @throws IllegalStateException If server is not started.
     */
    @SuppressWarnings("unused")
    public InetSocketAddress getActualSslLocalAddress() {
        if(!mServerStarted) {
            throw new IllegalArgumentException("Server not started.");
        }
        return mActualSslLocalAddress;
    }

    RpcServiceCollector getRpcServiceCollector() {
        return mRpcServiceCollector;
    }

    void sendChannelDisconnectEvent(ProtobufRpcServerChannel serverChannel, DisconnectReason reason) {
        if(mChannelDisconnectListener != null) {
            mChannelDisconnectListener.channelDisconnected(serverChannel, reason);
        }
    }

    private void setServerBootstrap(InetSocketAddress localAddress, ServerBootstrap serverBootstrap) {
        mLocalAddress = localAddress;
        mServerBootstrap = serverBootstrap;
    }

    private void setSslServerBootstrap(InetSocketAddress localAddress, ServerBootstrap serverBootstrap) {
        mSslLocalAddress = localAddress;
        mSslServerBootstrap = serverBootstrap;
    }
}