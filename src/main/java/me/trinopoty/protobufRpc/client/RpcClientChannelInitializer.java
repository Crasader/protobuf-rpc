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
    private final boolean mKeepAlive;

    private final String mLoggingName;
    private final boolean mEnableRpcLogging;
    private final boolean mEnableTrafficLogging;

    RpcClientChannelInitializer(
            Integer maxReceivePacketLength,
            SslContext sslContext,
            boolean keepAlive,
            String loggingName,
            boolean enableRpcLogging,
            boolean enableTrafficLogging) {
        mMaxReceivePacketLength = (maxReceivePacketLength != null)? maxReceivePacketLength : MAX_PACKET_LENGTH;
        mSslContext = sslContext;
        mKeepAlive = keepAlive;

        mLoggingName = loggingName;
        mEnableRpcLogging = enableRpcLogging;
        mEnableTrafficLogging = enableTrafficLogging;
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
                mLoggingName,
                mEnableTrafficLogging,
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
        pipeline.addLast("handler", new RpcClientChannelHandler(
                mLoggingName,
                mEnableRpcLogging
        ));
    }
}