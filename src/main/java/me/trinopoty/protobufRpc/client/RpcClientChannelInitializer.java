package me.trinopoty.protobufRpc.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import me.trinopoty.protobufRpc.codec.RpcMessageCodec;

import java.util.concurrent.TimeUnit;

final class RpcClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_PACKET_LENGTH = 8 * 1024;

    private final int mMaxReceivePacketLength;
    private final SslContext mSslContext;
    private final boolean mEnableTrafficLogging;
    private final String mLoggingName;
    private final boolean mKeepAlive;

    RpcClientChannelInitializer(
            Integer maxReceivePacketLength,
            SslContext sslContext,
            boolean enableTrafficLogging,
            String loggingName,
            boolean keepAlive) {
        mMaxReceivePacketLength = (maxReceivePacketLength != null)? maxReceivePacketLength : MAX_PACKET_LENGTH;
        mSslContext = sslContext;
        mEnableTrafficLogging = enableTrafficLogging;
        mLoggingName = loggingName;
        mKeepAlive = keepAlive;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) {
        ChannelPipeline pipeline = socketChannel.pipeline();

        if(mSslContext != null) {
            pipeline.addLast("ssl", mSslContext.newHandler(socketChannel.alloc()));
        }

        pipeline.addLast("protobuf-codec", new RpcMessageCodec(
                mMaxReceivePacketLength,
                true,
                mLoggingName, mEnableTrafficLogging,
                mEnableTrafficLogging
        ));
        if(mKeepAlive) {
            pipeline.addLast("keep-alive", new IdleStateHandler(
                    true,
                    3000,
                    1500,
                    0,
                    TimeUnit.MILLISECONDS));
        }
        pipeline.addLast("handler", new RpcClientChannelHandler());
    }
}