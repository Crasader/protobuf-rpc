package me.trinopoty.protobufRpc.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;

import java.io.IOException;

final class RpcClientChannelHandler extends ChannelInboundHandlerAdapter {

    private RpcClientChannelImpl mRpcClientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        assert mRpcClientChannel != null;

        WirePacketFormat.WirePacket wirePacket = (WirePacketFormat.WirePacket) msg;

        if (wirePacket.getMessageType() != WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE) {
            mRpcClientChannel.receivedRpcPacket((WirePacketFormat.WirePacket) msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if(mRpcClientChannel != null) {
            mRpcClientChannel.channelException(cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if(evt instanceof IdleStateEvent) {
            if(((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                sendKeepAlivePacket(ctx);
            } else if(((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                mRpcClientChannel.channelException(new IOException("Reader idle"));
            }
        }
    }

    void setRpcClientChannel(RpcClientChannelImpl rpcClientChannel) {
        mRpcClientChannel = rpcClientChannel;
    }

    @SuppressWarnings("Duplicates")
    private void sendKeepAlivePacket(ChannelHandlerContext ctx) {
        WirePacketFormat.WirePacket.Builder builder = WirePacketFormat.WirePacket.newBuilder();
        builder.setMessageIdentifier(0);
        builder.setMessageType(WirePacketFormat.MessageType.MESSAGE_TYPE_KEEP_ALIVE);
        ctx.writeAndFlush(builder.build());
    }
}