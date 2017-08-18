package me.trinopoty.protobufRpc.client;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.io.Closeable;
import java.net.InetSocketAddress;

final class ProtobufRpcClientChannelPool implements Closeable {

    private static final class RpcClientChannelProxyImpl implements RpcClientChannel {

        private final ProtobufRpcClientChannelPool mClientChannelPool;
        private final RpcClientChannel mRpcClientChannel;

        RpcClientChannelProxyImpl(ProtobufRpcClientChannelPool clientChannelPool, RpcClientChannel rpcClientChannel) {
            mClientChannelPool = clientChannelPool;
            mRpcClientChannel = rpcClientChannel;
        }

        @Override
        public <T> T getService(Class<T> classOfService) {
            return mRpcClientChannel.getService(classOfService);
        }

        @Override
        public boolean isActive() {
            return mRpcClientChannel.isActive();
        }

        @Override
        public void close() {
            mClientChannelPool.returnResource(this);
        }

        public void realClose() {
            mRpcClientChannel.close();
        }
    }

    private final class ClientChannelFactory implements PooledObjectFactory<RpcClientChannel> {

        private final ProtobufRpcClient mProtobufRpcClient;
        private final InetSocketAddress mRemoteAddress;
        private final boolean mSsl;

        ClientChannelFactory(ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
            mProtobufRpcClient = protobufRpcClient;
            mRemoteAddress = remoteAddress;
            mSsl = ssl;
        }

        @Override
        public PooledObject<RpcClientChannel> makeObject() throws Exception {
            RpcClientChannel rpcClientChannel = new RpcClientChannelProxyImpl(
                    ProtobufRpcClientChannelPool.this,
                    mProtobufRpcClient.getClientChannel(mRemoteAddress, mSsl));
            return new DefaultPooledObject<>(rpcClientChannel);
        }

        @Override
        public void destroyObject(PooledObject<RpcClientChannel> pooledObject) throws Exception {
            ((RpcClientChannelProxyImpl) pooledObject.getObject()).realClose();
        }

        @Override
        public boolean validateObject(PooledObject<RpcClientChannel> pooledObject) {
            return pooledObject.getObject().isActive();
        }

        @Override
        public void activateObject(PooledObject<RpcClientChannel> pooledObject) {
        }

        @Override
        public void passivateObject(PooledObject<RpcClientChannel> pooledObject) {
        }
    }

    private GenericObjectPool<RpcClientChannel> mClientChannelPool;

    ProtobufRpcClientChannelPool(RpcClientChannelPoolConfig poolConfig, ProtobufRpcClient protobufRpcClient, InetSocketAddress remoteAddress, boolean ssl) {
        mClientChannelPool = new GenericObjectPool<>(new ClientChannelFactory(protobufRpcClient, remoteAddress, ssl), poolConfig);
    }

    @Override
    public void close() {
        mClientChannelPool.close();
    }

    /**
     * Retrieves an instance of {@link RpcClientChannel} object from the pool.
     *
     * @return An instance of {@link RpcClientChannel} for communicating with server.
     */
    public RpcClientChannel getResource() {
        try {
            return mClientChannelPool.borrowObject();
        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a broken {@link RpcClientChannel} instance to the pool.
     * The resource is closed on return.
     *
     * @param clientChannel The instance to return.
     */
    public void returnBrokenResource(RpcClientChannel clientChannel) {
        try {
            mClientChannelPool.invalidateObject(clientChannel);
        } catch(Exception ignore) {
        }
    }

    /**
     * Returns a {@link RpcClientChannel} instance to the pool.
     *
     * @param clientChannel The instance to return.
     */
    public void returnResource(RpcClientChannel clientChannel) {
        if(clientChannel.isActive()) {
            mClientChannelPool.returnObject(clientChannel);
        } else {
            returnBrokenResource(clientChannel);
        }
    }
}