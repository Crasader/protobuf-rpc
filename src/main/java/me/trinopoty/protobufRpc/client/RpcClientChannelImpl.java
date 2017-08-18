package me.trinopoty.protobufRpc.client;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.Channel;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.exception.RpcCallException;
import me.trinopoty.protobufRpc.exception.RpcCallServerException;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class RpcClientChannelImpl implements ProtobufRpcClientChannel {

    private static final long DEFAULT_READ_TIMEOUT = 5 * 1000;

    private final class RpcInvocationHandler implements InvocationHandler {

        private final RpcServiceCollector.RpcServiceInfo mRpcServiceInfo;

        RpcInvocationHandler(RpcServiceCollector.RpcServiceInfo serviceInfo) {
            mRpcServiceInfo = serviceInfo;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RpcServiceCollector.RpcMethodInfo methodInfo = mRpcServiceInfo.getMethodMap().get(method);
            assert methodInfo != null;

            WirePacketFormat.ServiceIdentifier serviceIdentifier = WirePacketFormat.ServiceIdentifier.newBuilder()
                    .setServiceIdentifier(mRpcServiceInfo.getServiceIdentifier())
                    .setMethodIdentifier(methodInfo.getMethodIdentifier())
                    .build();
            WirePacketFormat.WirePacket.Builder requestWirePacketBuilder = WirePacketFormat.WirePacket.newBuilder();
            requestWirePacketBuilder.setMessageIdentifier(mMessageIdentifierGenerator.incrementAndGet());
            requestWirePacketBuilder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_REQUEST);
            requestWirePacketBuilder.setServiceIdentifier(serviceIdentifier);

            AbstractMessage requestMessage = (AbstractMessage) args[0];
            requestWirePacketBuilder.setPayload(requestMessage.toByteString());

            WirePacketFormat.WirePacket responseWirePacketPacket = callRpcAndWaitForResponse(requestWirePacketBuilder.build());
            if(responseWirePacketPacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_RESPONSE) {
                try {
                    return methodInfo.getResponseMessageParser().invoke(null, (Object) responseWirePacketPacket.getPayload().toByteArray());
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new RpcCallException("Unable to parse response message.", ex);
                }
            } else if(responseWirePacketPacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_ERROR) {
                WirePacketFormat.ErrorMessage errorMessage = WirePacketFormat.ErrorMessage.parseFrom(responseWirePacketPacket.getPayload());
                throw new RpcCallServerException(errorMessage.getMessage());
            } else {
                throw new RpcCallException("Invalid response received: " + responseWirePacketPacket.toString());
            }
        }
    }

    private final ProtobufRpcClient mProtobufRpcClient;
    private final Channel mChannel;
    private final long mDefaultReceiveTimeoutMillis;

    private final AtomicLong mMessageIdentifierGenerator = new AtomicLong();
    private final Map<Class, Object> mProxyMap = new HashMap<>();
    private final Map<Long, Thread> mWaitingRequestThreads = new ConcurrentHashMap<>();
    private final Map<Long, WirePacketFormat.WirePacket> mRequestResponseMap = new ConcurrentHashMap<>();

    private final Map<Class, Object> mOobHandlerMap = new HashMap<>();

    RpcClientChannelImpl(
            ProtobufRpcClient protobufRpcClient,
            Channel channel,
            Long defaultReceiveTimeoutMillis) {
        mProtobufRpcClient = protobufRpcClient;
        mChannel = channel;
        mDefaultReceiveTimeoutMillis = (defaultReceiveTimeoutMillis != null)? defaultReceiveTimeoutMillis : DEFAULT_READ_TIMEOUT;

        RpcClientChannelHandler rpcClientChannelHandler = (RpcClientChannelHandler) mChannel.pipeline().get("handler");
        rpcClientChannelHandler.setRpcClientChannel(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getService(Class<T> classOfService) {
        if(!mProxyMap.containsKey(classOfService)) {
            RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcClient.getRpcServiceCollector().getServiceInfo(classOfService);
            if(serviceInfo != null) {
                mProxyMap.put(classOfService, Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { classOfService }, new RpcInvocationHandler(serviceInfo)));
            }
        }

        return (T) mProxyMap.get(classOfService);
    }

    @Override
    public <T> void addOobHandler(Class<T> classOfOob, T objectOfOob) {
        if(!mOobHandlerMap.containsKey(classOfOob)) {
            RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcClient.getRpcServiceCollector().getServiceInfo(classOfOob);
            if((serviceInfo == null) || !serviceInfo.isOob()) {
                throw new IllegalArgumentException(String.format("Class<%s> not registered for OOB handling.", classOfOob.getName()));
            }
        }

        mOobHandlerMap.put(classOfOob, objectOfOob);
    }

    @Override
    public boolean isActive() {
        return mChannel.isActive();
    }

    @Override
    public void close() {
        mChannel.close().syncUninterruptibly();

        mProxyMap.clear();
        mOobHandlerMap.clear();
    }

    void receivedRpcPacket(WirePacketFormat.WirePacket wirePacket) {
        if((wirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_RESPONSE) || (wirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_ERROR)) {
            handleRpcResponse(wirePacket);
        } else if(wirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_OOB) {
            handleOob(wirePacket);
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private WirePacketFormat.WirePacket callRpcAndWaitForResponse(WirePacketFormat.WirePacket wirePacket) {
        final Thread currentThread = Thread.currentThread();
        synchronized (currentThread) {
            mWaitingRequestThreads.put(wirePacket.getMessageIdentifier(), currentThread);
            mChannel.writeAndFlush(wirePacket);

            try {
                currentThread.wait(mDefaultReceiveTimeoutMillis);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } finally {
                mWaitingRequestThreads.remove(wirePacket.getMessageIdentifier());
            }
        }

        return mRequestResponseMap.get(wirePacket.getMessageIdentifier());
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private void handleRpcResponse(WirePacketFormat.WirePacket wirePacket) {
        Thread thread = mWaitingRequestThreads.get(wirePacket.getMessageIdentifier());
        if(thread != null) {
            mRequestResponseMap.put(wirePacket.getMessageIdentifier(), wirePacket);
            synchronized (thread) {
                thread.notify();
            }
        }
    }

    private void handleOob(WirePacketFormat.WirePacket wirePacket) {
        WirePacketFormat.ServiceIdentifier serviceIdentifier = wirePacket.getServiceIdentifier();
        RpcServiceCollector.RpcServiceInfo serviceInfo;
        RpcServiceCollector.RpcMethodInfo methodInfo;
        Object oobHandler;

        if(((serviceInfo = mProtobufRpcClient.getRpcServiceCollector().getServiceInfo(serviceIdentifier.getServiceIdentifier())) != null) &&
                ((methodInfo = serviceInfo.getMethodIdentifierMap().get(serviceIdentifier.getMethodIdentifier())) != null) &&
                ((oobHandler = mOobHandlerMap.get(serviceInfo.getService())) != null)) {
            try {
                AbstractMessage requestMessage = (AbstractMessage) methodInfo.getRequestMessageParser().invoke(null, wirePacket.getPayload().toByteArray());
                methodInfo.getMethod().invoke(oobHandler, requestMessage);
            } catch (IllegalAccessException | InvocationTargetException ignore) {
            }
        } else {
            throw new RpcCallException(String.format("Unable to find OOB handler for service identifier: (%d, %d)", serviceIdentifier.getServiceIdentifier(), serviceIdentifier.getMethodIdentifier()));
        }
    }
}