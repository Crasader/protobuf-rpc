package me.trinopoty.protobufRpc.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.codec.RpcMessageCodec;

final class RpcClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_PACKET_LENGTH = 8 * 1024;

    private final int mMaxReceivePacketLength;
    private final SslContext mSslContext;
    private final boolean mEnableTrafficLogging;
    private final String mLoggingName;

    RpcClientChannelInitializer(
            Integer maxReceivePacketLength,
            SslContext sslContext,
            boolean enableTrafficLogging,
            String loggingName) {
        mMaxReceivePacketLength = (maxReceivePacketLength != null)? maxReceivePacketLength : MAX_PACKET_LENGTH;
        mEnableTrafficLogging = enableTrafficLogging;
        mLoggingName = loggingName;
        mSslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();

        if(mSslContext != null) {
            pipeline.addLast("ssl", mSslContext.newHandler(socketChannel.alloc()));
        }

        pipeline.addLast("protobuf-codec", new RpcMessageCodec(
                mMaxReceivePacketLength,
                true,
                mEnableTrafficLogging,
                mEnableTrafficLogging,
                mLoggingName));
        pipeline.addLast("handler", new RpcClientChannelHandler());
    }
}