package me.trinopoty.protobufRpc.client;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.Channel;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class RpcClientChannelImpl implements RpcClientChannel {

    private static final long DEFAULT_READ_TIMEOUT = 5 * 1000;

    private static HashMap<Class<? extends AbstractMessage>, Method> PROTOBUF_PARSER = new HashMap<>();

    private final class RpcInvocationHandler implements InvocationHandler {

        private final RpcServiceCollector.RpcServiceInfo mRpcServiceInfo;

        RpcInvocationHandler(Class classOfService) {
            mRpcServiceInfo = mProtobufRpcClient.getRpcServiceCollector().getServiceInfo(classOfService);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RpcServiceCollector.RpcMethodInfo methodInfo = mRpcServiceInfo.getMethodMap().get(method);
            if(methodInfo != null) {
                WirePacketFormat.ServiceIdentifier serviceIdentifier = WirePacketFormat.ServiceIdentifier.newBuilder()
                        .setServiceIdentifier(mRpcServiceInfo.getServiceIdentifier())
                        .setMethodIdentifier(methodInfo.getMethodIdentifier())
                        .build();
                WirePacketFormat.WirePacket.Builder wirePacketBuilder = WirePacketFormat.WirePacket.newBuilder();
                wirePacketBuilder.setMessageIdentifier(mMessageIdentifierGenerator.incrementAndGet());
                wirePacketBuilder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_REQUEST);
                wirePacketBuilder.setServiceIdentifier(serviceIdentifier);

                AbstractMessage requestMessage = (AbstractMessage) args[0];
                wirePacketBuilder.setPayload(requestMessage.toByteString());

                WirePacketFormat.WirePacket responsePacket = callRpcAndWaitForResponse(wirePacketBuilder.build());

                Method parserMethod = getProtobufParserMethod(methodInfo.getRequestMessageType());
                assert parserMethod != null;

                AbstractMessage responseMessage = null;
                try {
                    responseMessage = (AbstractMessage) parserMethod.invoke(null, (Object) responsePacket.getPayload().toByteArray());
                } catch (IllegalAccessException | InvocationTargetException ignore) {
                }

                if(responseMessage != null) {
                    return responseMessage;
                } else {
                    // TODO: Throw error
                }
            } else {
                // TODO: Throw error
            }

            return null;
        }

        private Method getProtobufParserMethod(Class<? extends AbstractMessage> messageClass) {
            if(PROTOBUF_PARSER.get(messageClass) == null) {
                try {
                    Method parserMethod = messageClass.getMethod("parseFrom", byte[].class);
                    PROTOBUF_PARSER.put(messageClass, parserMethod);
                } catch (NoSuchMethodException ignore) {
                }
            }

            return PROTOBUF_PARSER.get(messageClass);
        }
    }

    private final ProtobufRpcClient mProtobufRpcClient;
    private final Channel mChannel;
    private final long mDefaultReceiveTimeoutMillis;

    private final AtomicLong mMessageIdentifierGenerator = new AtomicLong();
    private final Map<Class, Object> mProxyMap = new HashMap<>();
    private final Map<Long, Thread> mWaitingRequestThreads = new ConcurrentHashMap<>();
    private final Map<Long, WirePacketFormat.WirePacket> mRequestResponseMap = new ConcurrentHashMap<>();

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
            mProtobufRpcClient.getRpcServiceCollector().parseServiceInterface(classOfService);
            RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcClient.getRpcServiceCollector().getServiceInfo(classOfService);
            if(serviceInfo != null) {
                mProxyMap.put(classOfService, Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { classOfService }, new RpcInvocationHandler(classOfService)));
            }
        }

        return (T) mProxyMap.get(classOfService);
    }

    @Override
    public boolean isActive() {
        return mChannel.isActive();
    }

    @Override
    public void close() {
        mChannel.close().syncUninterruptibly();
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    void receivedRpcPacket(WirePacketFormat.WirePacket wirePacket) {
        if(wirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_RESPONSE) {
            Thread thread = mWaitingRequestThreads.get(wirePacket.getMessageIdentifier());
            if(thread != null) {
                mRequestResponseMap.put(wirePacket.getMessageIdentifier(), wirePacket);
                synchronized (thread) {
                    thread.notify();
                }
            }
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
}