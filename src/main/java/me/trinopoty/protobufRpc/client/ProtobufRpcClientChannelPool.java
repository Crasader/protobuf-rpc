package me.trinopoty.protobufRpc.client;

import me.trinopoty.protobufRpc.ProtobufRpcLog;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class ProtobufRpcClientChannelPool implements Closeable {

    private static final int BORROW_RETRY_COUNT = 5;

    private static final class RpcClientChannelProxyImpl implements ProtobufRpcClientChannel {

        private final ProtobufRpcClientChannelPool mClientChannelPool;
        private final ProtobufRpcClientChannel mRpcClientChannel;

        RpcClientChannelProxyImpl(ProtobufRpcClientChannelPool clientChannelPool, ProtobufRpcClientChannel rpcClientChannel) {
            mClientChannelPool = clientChannelPool;
            mRpcClientChannel = rpcClientChannel;
        }

        @Override
        public <T> T getService(Class<T> classOfService) {
            return mRpcClientChannel.getService(classOfService);
        }

        @Override
        public <T> void addOobHandler(Class<T> classOfOob, T objectOfOob) {
            mRpcClientChannel.addOobHandler(classOfOob, objectOfOob);
        }

        @Override
        public void setChannelDisconnectListener(ProtobufRpcClientChannelDisconnectListener channelDisconnectListener) {
            mRpcClientChannel.setChannelDisconnectListener(channelDisconnectListener);
        }

        @Override
        public boolean isActive() {
            return mRpcClientChannel.isActive();
        }

        @Override
        public void close() {
            mClientChannelPool.returnResource(this);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return mRpcClientChannel.getRemoteAddress();
        }

        void realClose() {
            mRpcClientChannel.close();
        }
    }

    private final class ClientChannelFactory extends BasePooledObjectFactory<ProtobufRpcClientChannel> {

        private final ProtobufRpcClient mProtobufRpcClient;
        private final InetSocketAddress mRemoteAddress;
        private final boolean mSsl;

        ClientChannelFactory(ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
            mProtobufRpcClient = protobufRpcClient;
            mRemoteAddress = remoteAddress;
            mSsl = ssl;
        }

        @Override
        public ProtobufRpcClientChannel create() {
            return new RpcClientChannelProxyImpl(
                    ProtobufRpcClientChannelPool.this,
                    mProtobufRpcClient.getClientChannel(mRemoteAddress, mSsl));
        }

        @Override
        public PooledObject<ProtobufRpcClientChannel> wrap(ProtobufRpcClientChannel protobufRpcClientChannel) {
            return new DefaultPooledObject<>(protobufRpcClientChannel);
        }

        @Override
        public void destroyObject(PooledObject<ProtobufRpcClientChannel> p) {
            ((RpcClientChannelProxyImpl) p.getObject()).realClose();
        }

        @Override
        public boolean validateObject(PooledObject<ProtobufRpcClientChannel> p) {
            return p.getObject().isActive();
        }
    }

    private final Logger mLogger;
    private final String mLogTag;
    private final boolean mLogCallingMethod;
    private final AtomicLong mBorrowedObjectCount;

    private GenericObjectPool<ProtobufRpcClientChannel> mClientChannelPool;

    ProtobufRpcClientChannelPool(RpcClientChannelPoolConfig poolConfig, ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
        mClientChannelPool = new GenericObjectPool<>(new ClientChannelFactory(protobufRpcClient, remoteAddress, ssl), poolConfig);

        if(poolConfig.isLoggingEnabled()) {
            mLogger = LogManager.getLogger(ProtobufRpcLog.CLIENT_POOL);
            mLogTag = (poolConfig.getLogTag() != null)? poolConfig.getLogTag() : Integer.toHexString(System.identityHashCode(this));
            mLogCallingMethod = poolConfig.isLogCallingMethod();
            mBorrowedObjectCount = new AtomicLong(0);
        } else {
            mLogger = null;
            mLogTag = null;
            mLogCallingMethod = false;
            mBorrowedObjectCount = null;
        }

        if(mLogger != null) {
            mLogger.debug("[ProtobufRpc Pool, " + mLogTag + ", Init] { remoteAddress : \"" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + "\" }");
        }
    }

    @Override
    public void close() {
        mClientChannelPool.close();
    }

    /**
     * Retrieves an instance of {@link ProtobufRpcClientChannel} object from the pool.
     *
     * @return An instance of {@link ProtobufRpcClientChannel} for communicating with server.
     */
    public ProtobufRpcClientChannel getResource() throws IOException {
        ProtobufRpcClientChannel result = null;

        try {
            int thisRetryCount = 0;
            do {
                result = mClientChannelPool.borrowObject();
                if(!result.isActive()) {
                    mClientChannelPool.invalidateObject(result);
                    result = null;
                    thisRetryCount += 1;
                }
            } while ((result == null) && (thisRetryCount < BORROW_RETRY_COUNT));
        } catch (Exception ignore) {
        }

        if(result == null) {
            throw new IOException("Unable to borrow channel resource.");
        }

        if(mLogger != null) {
            String additionalData = null;
            if(mLogCallingMethod) {
                @SuppressWarnings("ThrowableNotThrown") StackTraceElement[] stackTraceElementList = (new Throwable()).getStackTrace();
                StackTraceElement callingMethod = (stackTraceElementList.length > 1)? stackTraceElementList[1] : null;
                if(callingMethod != null) {
                    additionalData = "calledFrom: { class: " + callingMethod.getClassName() +
                            ", method: " + callingMethod.getMethodName() +
                            ", file: " + callingMethod.getFileName() +
                            ", line: " + callingMethod.getLineNumber() + " }";
                }
            }
            mLogger.debug("[ProtobufRpc Pool, " + mLogTag + ", Borrow] { borrowCount: " + mBorrowedObjectCount.incrementAndGet() + ((additionalData != null)? ", " + additionalData : "") + " }");
        }
        return result;
    }

    /**
     * Returns a {@link ProtobufRpcClientChannel} instance to the pool.
     *
     * @param clientChannel The instance to return.
     */
    public void returnResource(ProtobufRpcClientChannel clientChannel) {
        if(mLogger != null) {
            mLogger.debug("[ProtobufRpc Pool, " + mLogTag + ", Return] { borrowCount: " + mBorrowedObjectCount.decrementAndGet() + " }");
        }

        if(clientChannel.isActive()) {
            mClientChannelPool.returnObject(clientChannel);
        } else {
            try {
                mClientChannelPool.invalidateObject(clientChannel);
            } catch (Exception ignore) {
            }
        }
    }
}