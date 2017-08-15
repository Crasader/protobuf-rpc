package me.trinopoty.protobufRpc.server;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.util.Pair;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

final class RpcServerChannelHandler extends ChannelInboundHandlerAdapter {

    private static HashMap<Class<? extends AbstractMessage>, Method> PROTOBUF_PARSER = new HashMap<>();

    private final ProtobufRpcServer mProtobufRpcServer;
    private final HashMap<Class, Object> mServiceImplementationObjectMap = new HashMap<>();

    RpcServerChannelHandler(ProtobufRpcServer protobufRpcServer) {
        mProtobufRpcServer = protobufRpcServer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        WirePacketFormat.WirePacket wirePacket = (WirePacketFormat.WirePacket) msg;
        if(wirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_REQUEST) {
            WirePacketFormat.ServiceIdentifier serviceIdentifier = wirePacket.getServiceIdentifier();

            Pair<RpcServiceCollector.RpcServiceInfo, Object> serviceInfoObjectPair = getServiceImplementationObject(serviceIdentifier.getServiceIdentifier());
            if(serviceInfoObjectPair != null) {
                RpcServiceCollector.RpcServiceInfo rpcServiceInfo = serviceInfoObjectPair.getKey();
                Object implObject = serviceInfoObjectPair.getValue();
                if(implObject != null) {
                    RpcServiceCollector.RpcMethodInfo methodInfo = rpcServiceInfo.getMethodMap().get(serviceIdentifier.getMethodIdentifier());
                    if(methodInfo != null) {
                        AbstractMessage requestMessage = null;
                        Method parserMethod = getProtobufParserMethod(methodInfo.getRequestMessageType());
                        assert parserMethod != null;

                        try {
                            requestMessage = (AbstractMessage) parserMethod.invoke(null, (Object) wirePacket.getPayload().toByteArray());
                        } catch (IllegalAccessException | InvocationTargetException ignore) {
                        }

                        if(requestMessage != null) {
                            AbstractMessage responseMessage = null;
                            try {
                                responseMessage = (AbstractMessage) methodInfo.getMethod().invoke(implObject, requestMessage);
                            } catch (IllegalAccessException | InvocationTargetException ex) {
                                // TODO: Send error
                            }

                            if(responseMessage != null) {
                                ctx.writeAndFlush(responseMessage);
                            }
                        } else {
                            // TODO: Send + throw error
                        }
                    }
                } else {
                    // TODO: Send + throw error
                }
            } else {
                // TODO: Send + throw error
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

    }

    private synchronized Pair<RpcServiceCollector.RpcServiceInfo, Object> getServiceImplementationObject(int serviceIdentifier) {
        RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcServer.getRpcServiceCollector().getServiceInfo(serviceIdentifier);
        if(serviceInfo == null) {
            return null;
        }

        if(serviceInfo.getImplClass() == null) {
            return null;
        }

        if(!mServiceImplementationObjectMap.containsKey(serviceInfo.getImplClass())) {
            Constructor implConstructor = serviceInfo.getImplClassConstructor();
            assert implConstructor != null;

            try {
                Object implObject = implConstructor.newInstance();
                mServiceImplementationObjectMap.put(serviceInfo.getImplClass(), implObject);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                // TODO: Do something maybe?
            }
        }

        return new Pair<>(serviceInfo, mServiceImplementationObjectMap.get(serviceInfo.getImplClass()));
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