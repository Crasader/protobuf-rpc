package me.trinopoty.protobufRpc.server;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.trinopoty.protobufRpc.DisconnectReason;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.util.Pair;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

final class RpcServerChannelHandler extends ChannelInboundHandlerAdapter {

    private final ProtobufRpcServer mProtobufRpcServer;
    private final HashMap<Class, Object> mServiceImplementationObjectMap = new HashMap<>();

    private ProtobufRpcServerChannel mRpcServerChannel;
    private DisconnectReason mChannelDisconnectReason = DisconnectReason.CLIENT_CLOSE;

    RpcServerChannelHandler(ProtobufRpcServer protobufRpcServer) {
        mProtobufRpcServer = protobufRpcServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        mRpcServerChannel = new ProtobufRpcServerChannel(mProtobufRpcServer, ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        mProtobufRpcServer.sendChannelDisconnectEvent(mRpcServerChannel, mChannelDisconnectReason);
        mChannelDisconnectReason = DisconnectReason.CLIENT_CLOSE;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        WirePacketFormat.WirePacket requestWirePacket = (WirePacketFormat.WirePacket) msg;
        if(requestWirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_REQUEST) {
            WirePacketFormat.ServiceIdentifier serviceIdentifier = requestWirePacket.getServiceIdentifier();

            Pair<RpcServiceCollector.RpcServiceInfo, Object> serviceInfoObjectPair = getServiceImplementationObject(ctx, serviceIdentifier.getServiceIdentifier());
            if(serviceInfoObjectPair != null) {
                RpcServiceCollector.RpcServiceInfo rpcServiceInfo = serviceInfoObjectPair.getKey();
                RpcServiceCollector.RpcMethodInfo methodInfo = rpcServiceInfo.getMethodIdentifierMap().get(serviceIdentifier.getMethodIdentifier());
                Object implObject = serviceInfoObjectPair.getValue();

                if(methodInfo != null) {
                    if(implObject != null) {
                        AbstractMessage requestMessage;
                        AbstractMessage responseMessage;

                        try {
                            requestMessage = (AbstractMessage) methodInfo.getRequestMessageParser().invoke(null, (Object) requestWirePacket.getPayload().toByteArray());
                        } catch (IllegalAccessException | InvocationTargetException ex) {
                            sendError(ctx, requestWirePacket, "Unable to parse call request parameter.");

                            throw ex;
                        }

                        try {
                            responseMessage = (AbstractMessage) methodInfo.getMethod().invoke(implObject, requestMessage);
                        } catch (IllegalAccessException | InvocationTargetException ex) {
                            sendError(ctx, requestWirePacket, "Unable to process call.");

                            throw ex;
                        }

                        if(responseMessage != null) {
                            WirePacketFormat.WirePacket.Builder responseWirePacketBuilder = WirePacketFormat.WirePacket.newBuilder();
                            responseWirePacketBuilder.setMessageIdentifier(requestWirePacket.getMessageIdentifier());
                            responseWirePacketBuilder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_RESPONSE);
                            responseWirePacketBuilder.setServiceIdentifier(requestWirePacket.getServiceIdentifier());
                            responseWirePacketBuilder.setPayload(responseMessage.toByteString());
                            ctx.writeAndFlush(responseWirePacketBuilder.build());
                        } else {
                            sendError(ctx, requestWirePacket, "Unable to process call.");

                            throw new RuntimeException(String.format("Response cannot be null from %s.%s", rpcServiceInfo.getImplClass().getName(), methodInfo.getMethod().getName()));
                        }
                    } else {
                        sendError(ctx, requestWirePacket, "Internal server error.");

                        throw new RuntimeException(String.format("Unable to create implementation object of %s class", rpcServiceInfo.getService().getName()));
                    }
                } else {
                    sendError(ctx, requestWirePacket, "Internal server error.");

                    throw new RuntimeException(String.format("Service class %s does not contain method with identifier %d", rpcServiceInfo.getService().getName(), serviceIdentifier.getMethodIdentifier()));
                }
            } else {
                sendError(ctx, requestWirePacket, "Internal server error.");

                throw new RuntimeException(String.format("Service with identifier %d is not registered", serviceIdentifier.getServiceIdentifier()));
            }
        } else if(requestWirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE) {
            sendKeepAlivePacket(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause != null) {
            if ((cause instanceof IOException) &&
                    (cause.getMessage() != null) &&
                    cause.getMessage().equals("Connection reset by peer")) {
                mChannelDisconnectReason = DisconnectReason.NETWORK_ERROR;
            } else {
                cause.printStackTrace();
            }
        }
    }

    private void sendError(ChannelHandlerContext ctx, WirePacketFormat.WirePacket requestPacket, String message) throws Exception {
        WirePacketFormat.WirePacket.Builder builder = WirePacketFormat.WirePacket.newBuilder();
        builder.setMessageIdentifier(requestPacket.getMessageIdentifier());
        builder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_ERROR);
        builder.setServiceIdentifier(requestPacket.getServiceIdentifier());
        builder.setPayload(WirePacketFormat.ErrorMessage.newBuilder()
                .setMessage(message)
                .build()
                .toByteString());
        ctx.writeAndFlush(builder.build());
    }

    private synchronized Pair<RpcServiceCollector.RpcServiceInfo, Object> getServiceImplementationObject(ChannelHandlerContext context, int serviceIdentifier) {
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
                Object implObject = implConstructor.newInstance(mRpcServerChannel);
                mServiceImplementationObjectMap.put(serviceInfo.getImplClass(), implObject);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignore) {
            }
        }

        return new Pair<>(serviceInfo, mServiceImplementationObjectMap.get(serviceInfo.getImplClass()));
    }

    @SuppressWarnings("Duplicates")
    private void sendKeepAlivePacket(ChannelHandlerContext ctx) {
        WirePacketFormat.WirePacket.Builder builder = WirePacketFormat.WirePacket.newBuilder();
        builder.setMessageIdentifier(0);
        builder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE);

        byte[] wirePacketBytes = builder.build().toByteArray();
        ctx.writeAndFlush(wirePacketBytes);
    }
}