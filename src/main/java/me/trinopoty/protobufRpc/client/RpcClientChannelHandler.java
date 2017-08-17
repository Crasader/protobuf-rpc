package me.trinopoty.protobufRpc.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.trinopoty.protobufRpc.codec.WirePacketFormat;

final class RpcClientChannelHandler extends ChannelInboundHandlerAdapter {

    private RpcClientChannelImpl mRpcClientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        assert mRpcClientChannel != null;
        mRpcClientChannel.receivedRpcPacket((WirePacketFormat.WirePacket) msg);
    }

    void setRpcClientChannel(RpcClientChannelImpl rpcClientChannel) {
        mRpcClientChannel = rpcClientChannel;
    }
}