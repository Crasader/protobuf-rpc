package me.trinopoty.protobufRpc.server;

import com.google.protobuf.AbstractMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import me.trinopoty.protobufRpc.DisconnectReason;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;
import me.trinopoty.protobufRpc.util.Pair;
import me.trinopoty.protobufRpc.util.RpcServiceCollector;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

final class RpcServerChannelHandler extends ChannelInboundHandlerAdapter {

    private final ProtobufRpcServer mProtobufRpcServer;
    private final String mLoggingName;
    private final boolean mEnableRpcLogging;

    private final HashMap<Class, Object> mServiceImplementationObjectMap = new HashMap<>();

    private ProtobufRpcServerChannel mRpcServerChannel;
    private DisconnectReason mChannelDisconnectReason = DisconnectReason.CLIENT_CLOSE;

    private boolean mKeepAlive = false;

    RpcServerChannelHandler(
            ProtobufRpcServer protobufRpcServer,
            String loggingName,
            boolean enableRpcLogging) {
        mProtobufRpcServer = protobufRpcServer;
        mLoggingName = loggingName;
        mEnableRpcLogging = enableRpcLogging;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        mRpcServerChannel = new ProtobufRpcServerChannel(mProtobufRpcServer, ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        mProtobufRpcServer.sendChannelDisconnectEvent(mRpcServerChannel, mChannelDisconnectReason);
        mChannelDisconnectReason = DisconnectReason.CLIENT_CLOSE;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        WirePacketFormat.WirePacket requestWirePacket = (WirePacketFormat.WirePacket) msg;
        if(requestWirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_REQUEST) {
            handleIncomingRequest(ctx, requestWirePacket);
        } else if(requestWirePacket.getMessageType() == WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE) {
            handleIncomingKeepAlive(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if(cause != null) {
            if (cause instanceof IOException) {
                mChannelDisconnectReason = DisconnectReason.NETWORK_ERROR;
            } else {
                cause.printStackTrace();
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if(evt instanceof IdleStateEvent) {
            mChannelDisconnectReason = DisconnectReason.NETWORK_ERROR;
            ctx.channel().close().syncUninterruptibly();
        }
    }

    private void sendError(ChannelHandlerContext ctx, WirePacketFormat.WirePacket requestPacket, String message) {
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

    private synchronized Pair<RpcServiceCollector.RpcServiceInfo, Object> getServiceImplementationObject(int serviceIdentifier) {
        RpcServiceCollector.RpcServiceInfo serviceInfo = mProtobufRpcServer.getRpcServiceCollector().getServiceInfo(serviceIdentifier);
        if(serviceInfo == null) {
            return null;
        }

        if(serviceInfo.getImplClass() == null) {
            return null;
        }

        if(!mServiceImplementationObjectMap.containsKey(serviceInfo.getImplClass())) {
            Object implObject = serviceInfo.createImplClassObject(mRpcServerChannel);
            mServiceImplementationObjectMap.put(serviceInfo.getImplClass(), implObject);
        }

        return new Pair<>(serviceInfo, mServiceImplementationObjectMap.get(serviceInfo.getImplClass()));
    }

    private void handleIncomingRequest(ChannelHandlerContext ctx, WirePacketFormat.WirePacket requestWirePacket) throws Exception {
        do {
            final WirePacketFormat.ServiceIdentifier serviceIdentifier = requestWirePacket.getServiceIdentifier();
            if(serviceIdentifier == null) {
                sendError(ctx, requestWirePacket, "Client error.");

                throw new RuntimeException("Service with identifier is missing");
            }

            final Pair<RpcServiceCollector.RpcServiceInfo, Object> serviceInfoObjectPair = getServiceImplementationObject(serviceIdentifier.getServiceIdentifier());
            if(serviceInfoObjectPair == null) {
                sendError(ctx, requestWirePacket, "Internal server error.");

                throw new RuntimeException(String.format("Service with identifier %d is not registered", serviceIdentifier.getServiceIdentifier()));
            }

            final RpcServiceCollector.RpcServiceInfo rpcServiceInfo = serviceInfoObjectPair.getFirst();
            final RpcServiceCollector.RpcMethodInfo methodInfo = rpcServiceInfo.getMethodIdentifierMap().get(serviceIdentifier.getMethodIdentifier());
            final Object implObject = serviceInfoObjectPair.getSecond();

            if(methodInfo == null) {
                sendError(ctx, requestWirePacket, "Internal server error.");

                throw new RuntimeException(String.format("Service class %s does not contain method with identifier %d", rpcServiceInfo.getServiceClass().getName(), serviceIdentifier.getMethodIdentifier()));
            }

            if(implObject == null) {
                sendError(ctx, requestWirePacket, "Internal server error.");

                throw new RuntimeException(String.format("Unable to create implementation object of %s class", rpcServiceInfo.getServiceClass().getName()));
            }

            AbstractMessage requestMessage = null;
            AbstractMessage responseMessage = null;

            try {
                if(methodInfo.getRequestMessageParser() != null) {
                    requestMessage = (AbstractMessage) methodInfo.getRequestMessageParser().invoke(null, (Object) requestWirePacket.getPayload().toByteArray());
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                sendError(ctx, requestWirePacket, "Unable to parse call request parameter");
                throw ex;
            }

            try {
                if((requestMessage != null) && (methodInfo.getResponseMessageParser() != null)) {
                    responseMessage = (AbstractMessage) methodInfo.getMethod().invoke(implObject, requestMessage);
                } else if((requestMessage == null) && (methodInfo.getResponseMessageParser() != null)) {
                    responseMessage = (AbstractMessage) methodInfo.getMethod().invoke(implObject);
                } else if((requestMessage != null) && (methodInfo.getResponseMessageParser() == null)) {
                    methodInfo.getMethod().invoke(implObject, requestMessage);
                } else if((requestMessage == null) && (methodInfo.getResponseMessageParser() == null)) {
                    methodInfo.getMethod().invoke(implObject);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                sendError(ctx, requestWirePacket, "Unable to process call.");
                throw ex;
            }

            if((methodInfo.getResponseMessageParser() != null) && (responseMessage == null)) {
                sendError(ctx, requestWirePacket, "Unable to process call.");

                throw new RuntimeException(String.format("Response cannot be null from %s.%s", rpcServiceInfo.getImplClass().getName(), methodInfo.getMethod().getName()));
            }

            WirePacketFormat.WirePacket.Builder responseWirePacketBuilder = WirePacketFormat.WirePacket.newBuilder();
            responseWirePacketBuilder.setMessageIdentifier(requestWirePacket.getMessageIdentifier());
            responseWirePacketBuilder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_RESPONSE);
            responseWirePacketBuilder.setServiceIdentifier(requestWirePacket.getServiceIdentifier());
            if(responseMessage != null) {
                responseWirePacketBuilder.setPayload(responseMessage.toByteString());
            }
            ctx.writeAndFlush(responseWirePacketBuilder.build());
        } while (false);
    }

    private void handleIncomingKeepAlive(ChannelHandlerContext ctx) {
        if(!mKeepAlive) {
            initializeKeepAlive(ctx.channel());
        }
        sendKeepAlivePacket(ctx);
    }

    private void initializeKeepAlive(Channel channel) {
        if(channel.pipeline().get("keep-alive") == null) {
            channel.pipeline().addBefore(
                    "handler",
                    "keep-alive",
                    new IdleStateHandler(
                            true,
                            0,
                            0,
                            5000,
                            TimeUnit.MILLISECONDS));
            mKeepAlive = true;
        }
    }

    @SuppressWarnings("Duplicates")
    private void sendKeepAlivePacket(ChannelHandlerContext ctx) {
        WirePacketFormat.WirePacket.Builder builder = WirePacketFormat.WirePacket.newBuilder();
        builder.setMessageIdentifier(0);
        builder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE);
        ctx.writeAndFlush(builder.build());
    }
}