package me.trinopoty.protobufRpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.exception.ServiceConstructorNotFoundException;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
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

    public static final class Builder {

        private InetSocketAddress mLocalAddress = null;
        private InetSocketAddress mSslLocalAddress = null;
        private SslContext mSslContext = null;

        private int mBacklogCount = 5;
        private boolean mKeepAlive = true;
        private Integer mMaxDecoderPacketLength = null;
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

        public Builder setMaxDecoderPacketLength(int maxDecoderPacketLength) {
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
            if((mLocalAddress == null) && (mSslLocalAddress == null)) {
                throw new IllegalArgumentException("LocalAddress must be provided.");
            }
            if(mEnableTrafficLogging && (mLoggingName == null)) {
                throw new IllegalArgumentException("Logging name must be provided if logging is enabled.");
            }

            ProtobufRpcServer protobufRpcServer = new ProtobufRpcServer();

            if(mLocalAddress != null) {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(acquireServerEventLoopGroup(), acquireClientEventLoopGroup());
                serverBootstrap.channel(NioServerSocketChannel.class);
                serverBootstrap.childHandler(new RpcServerChannelInitializer(
                        protobufRpcServer,
                        mMaxDecoderPacketLength,
                        null,
                        mEnableTrafficLogging,
                        mLoggingName));
                serverBootstrap.option(ChannelOption.SO_BACKLOG, mBacklogCount);
                serverBootstrap.option(ChannelOption.SO_KEEPALIVE, mKeepAlive);

                protobufRpcServer.setServerBootstrap(mLocalAddress, serverBootstrap);
            }

            if(mSslLocalAddress != null) {
                ServerBootstrap sslServerBootstrap = new ServerBootstrap();
                sslServerBootstrap.group(acquireServerEventLoopGroup(), acquireClientEventLoopGroup());
                sslServerBootstrap.channel(NioServerSocketChannel.class);
                sslServerBootstrap.childHandler(new RpcServerChannelInitializer(
                        protobufRpcServer,
                        mMaxDecoderPacketLength,
                        mSslContext,
                        mEnableTrafficLogging,
                        mLoggingName));
                sslServerBootstrap.option(ChannelOption.SO_BACKLOG, mBacklogCount);
                sslServerBootstrap.option(ChannelOption.SO_KEEPALIVE, mKeepAlive);

                protobufRpcServer.setSslServerBootstrap(mSslLocalAddress, sslServerBootstrap);
            }

            return protobufRpcServer;
        }
    }

    private InetSocketAddress mLocalAddress;
    private ServerBootstrap mServerBootstrap;
    private Channel mServerChannel = null;

    private InetSocketAddress mSslLocalAddress;
    private ServerBootstrap mSslServerBootstrap;
    private Channel mSslServerChannel = null;

    private final RpcServiceCollector mRpcServiceCollector = new RpcServiceCollector();

    private boolean mServerStarted = false;

    private ProtobufRpcServer() {
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
     * Add the implementation class of a service interface.
     * @param classOfService The interface defining the service.
     * @param implOfService The class implementing the interface of the service.
     */
    public synchronized <T> void addServiceImplementation(Class<T> classOfService, Class<? extends T> implOfService) {
        mRpcServiceCollector.parseServiceInterface(classOfService);
        RpcServiceCollector.RpcServiceInfo serviceInfo = mRpcServiceCollector.getServiceInfo(classOfService);
        assert serviceInfo != null;

        serviceInfo.setImplClass(implOfService);
        serviceInfo.setImplClassConstructor(getServiceImplementationConstructor(implOfService));
    }

    /**
     * Bind to ports and start this server.
     *
     * @throws IllegalStateException If the server is already running.
     */
    public synchronized void startServer() {
        if(mServerStarted) {
            throw new IllegalStateException("Server already running.");
        }

        if(mLocalAddress != null) {
            ChannelFuture channelFuture = mServerBootstrap.bind(mLocalAddress).syncUninterruptibly();
            if(channelFuture.isSuccess()) {
                mServerChannel = channelFuture.channel();
            }
        }
        if(mSslLocalAddress != null) {
            ChannelFuture channelFuture = mSslServerBootstrap.bind(mSslLocalAddress).syncUninterruptibly();
            if(channelFuture.isSuccess()) {
                mSslServerChannel = channelFuture.channel();
            }
        }

        mServerStarted = true;
    }

    /**
     * Stop this server and unbind from ports.
     */
    public synchronized void stopServer() {
        if(mServerChannel != null) {
            mServerChannel.close().syncUninterruptibly();

            returnServerEventLoopGroup();
            returnClientEventLoopGroup();
        }
        if(mSslServerChannel != null) {
            mSslServerChannel.close().syncUninterruptibly();

            returnServerEventLoopGroup();
            returnClientEventLoopGroup();
        }

        mServerStarted = false;
    }

    RpcServiceCollector getRpcServiceCollector() {
        return mRpcServiceCollector;
    }

    private void setServerBootstrap(InetSocketAddress localAddress, ServerBootstrap serverBootstrap) {
        mLocalAddress = localAddress;
        mServerBootstrap = serverBootstrap;
    }

    private void setSslServerBootstrap(InetSocketAddress localAddress, ServerBootstrap serverBootstrap) {
        mSslLocalAddress = localAddress;
        mSslServerBootstrap = serverBootstrap;
    }

    private Constructor getServiceImplementationConstructor(Class implClass) {
        try {
            @SuppressWarnings("unchecked") Constructor constructor = implClass.getDeclaredConstructor();
            if((constructor.getModifiers() & Modifier.PUBLIC) != Modifier.PUBLIC) {
                throw new ServiceConstructorNotFoundException(String.format("Class<%s> does not have a valid constructor.", implClass.getName()));
            }

            return constructor;
        } catch (NoSuchMethodException ex) {
            throw new ServiceConstructorNotFoundException(String.format("Class<%s> does not have a valid constructor.", implClass.getName()));
        }
    }
}