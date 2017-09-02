package me.trinopoty.protobufRpc.server;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.exception.DuplicateRpcMethodIdentifierException;
import me.trinopoty.protobufRpc.exception.DuplicateRpcServiceIdentifierException;
import me.trinopoty.protobufRpc.exception.IllegalMethodSignatureException;
import me.trinopoty.protobufRpc.exception.MissingRpcIdentifierException;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class RpcServerChannel {

    private final class OobInvocationHandler implements InvocationHandler {

        private final RpcServiceCollector.RpcServiceInfo mRpcServiceInfo;

        OobInvocationHandler(RpcServiceCollector.RpcServiceInfo serviceInfo) {
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
            requestWirePacketBuilder.setMessageIdentifier(0);
            requestWirePacketBuilder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_OOB);
            requestWirePacketBuilder.setServiceIdentifier(serviceIdentifier);

            AbstractMessage requestMessage = (AbstractMessage) args[0];
            requestWirePacketBuilder.setPayload(requestMessage.toByteString());

            mChannel.writeAndFlush(requestWirePacketBuilder.build());

            return null;
        }
    }

    private final ProtobufRpcServer mProtobufRpcServer;
    private final Channel mChannel;

    RpcServerChannel(ProtobufRpcServer protobufRpcServer, Channel channel) {
        mProtobufRpcServer = protobufRpcServer;
        mChannel = channel;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOobService(Class<T> classOfService) {
        RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcServer.getRpcServiceCollector().getServiceInfo(classOfService);
        if(serviceInfo != null) {
            return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { classOfService }, new OobInvocationHandler(serviceInfo));
        } else {
            throw new IllegalArgumentException(String.format("Class<%s> not registered for OOB handling.", classOfService.getName()));
        }
    }
}