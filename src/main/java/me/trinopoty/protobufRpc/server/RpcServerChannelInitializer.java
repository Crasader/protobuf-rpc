package me.trinopoty.protobufRpc.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import me.trinopoty.protobufRpc.codec.RpcMessageCodec;

final class RpcServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_PACKET_LENGTH = 8 * 1024;

    private final ProtobufRpcServer mProtobufRpcServer;
    private final int mMaxReceivePacketLength;
    private final SslContext mSslContext;
    private final boolean mEnableTrafficLogging;
    private final String mLoggingName;

    RpcServerChannelInitializer(
            ProtobufRpcServer protobufRpcServer,
            Integer maxReceivePacketLength,
            SslContext sslContext,
            boolean enableTrafficLogging,
            String loggingName) {
        mProtobufRpcServer = protobufRpcServer;
        mMaxReceivePacketLength = (maxReceivePacketLength != null)? maxReceivePacketLength : MAX_PACKET_LENGTH;
        mSslContext = sslContext;
        mEnableTrafficLogging = enableTrafficLogging;
        mLoggingName = loggingName;
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
        pipeline.addLast("handler", new RpcServerChannelHandler(mProtobufRpcServer));
    }
}