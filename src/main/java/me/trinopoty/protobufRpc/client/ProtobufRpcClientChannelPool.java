package me.trinopoty.protobufRpc.client;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.io.Closeable;
import java.net.InetSocketAddress;

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
        public boolean isActive() {
            return mRpcClientChannel.isActive();
        }

        @Override
        public void close() {
            mClientChannelPool.returnResource(this);
        }

        void realClose() {
            mRpcClientChannel.close();
        }
    }

    private final class ClientChannelFactory implements PooledObjectFactory<ProtobufRpcClientChannel> {

        private final ProtobufRpcClient mProtobufRpcClient;
        private final InetSocketAddress mRemoteAddress;
        private final boolean mSsl;

        ClientChannelFactory(ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
            mProtobufRpcClient = protobufRpcClient;
            mRemoteAddress = remoteAddress;
            mSsl = ssl;
        }

        @Override
        public PooledObject<ProtobufRpcClientChannel> makeObject() throws Exception {
            ProtobufRpcClientChannel rpcClientChannel = new RpcClientChannelProxyImpl(
                    ProtobufRpcClientChannelPool.this,
                    mProtobufRpcClient.getClientChannel(mRemoteAddress, mSsl));
            return new DefaultPooledObject<>(rpcClientChannel);
        }

        @Override
        public void destroyObject(PooledObject<ProtobufRpcClientChannel> pooledObject) throws Exception {
            ((RpcClientChannelProxyImpl) pooledObject.getObject()).realClose();
        }

        @Override
        public boolean validateObject(PooledObject<ProtobufRpcClientChannel> pooledObject) {
            return pooledObject.getObject().isActive();
        }

        @Override
        public void activateObject(PooledObject<ProtobufRpcClientChannel> pooledObject) {
        }

        @Override
        public void passivateObject(PooledObject<ProtobufRpcClientChannel> pooledObject) {
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
     * Returns a broken {@link ProtobufRpcClientChannel} instance to the pool.
     * The resource is closed on return.
     *
     * @param clientChannel The instance to return.
     */
    public void returnBrokenResource(ProtobufRpcClientChannel clientChannel) {
        try {
            mClientChannelPool.invalidateObject(clientChannel);
        } catch(Exception ignore) {
        }
    }

    /**
     * Returns a {@link ProtobufRpcClientChannel} instance to the pool.
     *
     * @param clientChannel The instance to return.
     */
    public void returnResource(ProtobufRpcClientChannel clientChannel) {
        if(clientChannel.isActive()) {
            mClientChannelPool.returnObject(clientChannel);
        } else {
            returnBrokenResource(clientChannel);
        }
    }
}