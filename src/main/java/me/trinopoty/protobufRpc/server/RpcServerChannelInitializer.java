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

    private final String mLoggingName;
    private final boolean mEnableRpcLogging;
    private final boolean mEnableTrafficLogging;

    RpcServerChannelInitializer(
            ProtobufRpcServer protobufRpcServer,
            Integer maxReceivePacketLength,
            SslContext sslContext,
            String loggingName,
            boolean enableRpcLogging,
            boolean enableTrafficLogging) {
        mProtobufRpcServer = protobufRpcServer;
        mMaxReceivePacketLength = (maxReceivePacketLength != null)? maxReceivePacketLength : MAX_PACKET_LENGTH;
        mSslContext = sslContext;

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
                mEnableTrafficLogging));
        pipeline.addLast("handler", new RpcServerChannelHandler(
                mProtobufRpcServer,
                mLoggingName,
                mEnableRpcLogging));
    }
}