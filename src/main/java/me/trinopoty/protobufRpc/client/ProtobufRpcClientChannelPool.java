package me.trinopoty.protobufRpc.client;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.io.Closeable;
import java.net.InetSocketAddress;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class ProtobufRpcClientChannelPool implements Closeable {

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
        public ProtobufRpcClientChannel create() throws Exception {
            return new RpcClientChannelProxyImpl(
                    ProtobufRpcClientChannelPool.this,
                    mProtobufRpcClient.getClientChannel(mRemoteAddress, mSsl));
        }

        @Override
        public PooledObject<ProtobufRpcClientChannel> wrap(ProtobufRpcClientChannel protobufRpcClientChannel) {
            return new DefaultPooledObject<>(protobufRpcClientChannel);
        }

        @Override
        public void destroyObject(PooledObject<ProtobufRpcClientChannel> p) throws Exception {
            ((RpcClientChannelProxyImpl) p.getObject()).realClose();
        }

        @Override
        public boolean validateObject(PooledObject<ProtobufRpcClientChannel> p) {
            return p.getObject().isActive();
        }
    }

    private GenericObjectPool<ProtobufRpcClientChannel> mClientChannelPool;

    ProtobufRpcClientChannelPool(RpcClientChannelPoolConfig poolConfig, ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
        mClientChannelPool = new GenericObjectPool<>(new ClientChannelFactory(protobufRpcClient, remoteAddress, ssl), poolConfig);
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
    public ProtobufRpcClientChannel getResource() {
        try {
            return mClientChannelPool.borrowObject();
        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a {@link ProtobufRpcClientChannel} instance to the pool.
     *
     * @param clientChannel The instance to return.
     */
    public void returnResource(ProtobufRpcClientChannel clientChannel) {
        mClientChannelPool.returnObject(clientChannel);
    }
}