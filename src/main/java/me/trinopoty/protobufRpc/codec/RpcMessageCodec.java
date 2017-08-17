package me.trinopoty.protobufRpc.codec;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.TooLongFrameException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public final class RpcMessageCodec extends ByteToMessageCodec<WirePacketFormat.WirePacket> {

    private static final int PACKET_SIGNATURE = 0xad04ef64;

    private final int mMaxReceivePacketLength;
    private final boolean mDiscardLargerPacket;

    private final boolean mEnableEncodeLogging;
    private final boolean mEnableDecodeLogging;
    private final String mLoggingName;
    private final Logger mLogger;

    private int mDiscardLength = 0;

    public RpcMessageCodec(int maxReceivePacketLength, boolean discardLargerPacket, boolean enableEncodeLogging, boolean enableDecodeLogging, String loggingName) {
        mMaxReceivePacketLength = maxReceivePacketLength;
        mDiscardLargerPacket = discardLargerPacket;

        mEnableEncodeLogging = enableEncodeLogging;
        mEnableDecodeLogging = enableDecodeLogging;
        mLoggingName = loggingName;

        mLogger = (mEnableDecodeLogging || mEnableEncodeLogging)? LogManager.getLogger("NettyRpcLogger") : null;

        if((mLoggingName == null) && (mEnableEncodeLogging || mEnableDecodeLogging)) {
            throw new IllegalArgumentException("Logging name not provided.");
        }
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, WirePacketFormat.WirePacket wirePacket, ByteBuf byteBuf) throws Exception {
        byte[] messageBytes = wirePacket.toByteArray();

        if(mEnableEncodeLogging) {
            mLogger.info(String.format(
                    "[RpcEncoder:%s] Sending RPC call { serviceIdentifier: %d, methodIdentifier: %d }",
                    mLoggingName,
                    wirePacket.getServiceIdentifier().getServiceIdentifier(),
                    wirePacket.getServiceIdentifier().getMethodIdentifier()));
        }

        byteBuf.writeInt(PACKET_SIGNATURE);
        byteBuf.writeInt(messageBytes.length);
        byteBuf.writeBytes(messageBytes);
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        WirePacketFormat.WirePacket wirePacket = decode(byteBuf);
        if(wirePacket != null) {
            list.add(wirePacket);
        }
    }

    private WirePacketFormat.WirePacket decode(ByteBuf byteBuf) {
        int readerIdx = byteBuf.readerIndex();

        do {
            if(mDiscardLength > 0) {
                if(mEnableDecodeLogging) {
                    mLogger.info(String.format("[RpcDecoder:%s] Discarding data { size: %d }", mLoggingName, mDiscardLength));
                }
                mDiscardLength -= discardBytes(byteBuf, mDiscardLength);
            }
            if(mDiscardLength > 0) {
                return null;
            }

            readerIdx = byteBuf.readerIndex();

            final boolean signatureFound = findPacketSignature(byteBuf);

            if(signatureFound) {
                byteBuf.discardReadBytes();
                readerIdx = byteBuf.readerIndex();

                byteBuf.readInt();  // Read the signature

                final int payloadLength = byteBuf.readInt();

                if(mEnableDecodeLogging) {
                    mLogger.info(String.format("[RpcDecoder:%s] Received message { size: %s }", mLoggingName, payloadLength));
                }

                if(payloadLength > mMaxReceivePacketLength) {
                    if(mDiscardLargerPacket) {
                        mDiscardLength += payloadLength;
                        mDiscardLength -= discardBytes(byteBuf, mDiscardLength);

                        return null;
                    } else {
                        throw new TooLongFrameException("frame size (" + payloadLength + ") larger than maximum size (" + mMaxReceivePacketLength + ")");
                    }
                }

                if(byteBuf.readableBytes() >= payloadLength) {
                    final byte[] payloadBuffer = new byte[payloadLength];
                    byteBuf.readBytes(payloadBuffer);

                    byteBuf.discardReadBytes();

                    try {
                        WirePacketFormat.WirePacket wirePacket = WirePacketFormat.WirePacket.parseFrom(payloadBuffer);

                        if(mEnableDecodeLogging) {
                            mLogger.info(String.format(
                                    "[RpcDecoder:%s] Received RPC call { serviceIdentifier: %d, methodIdentifier: %d }",
                                    mLoggingName,
                                    wirePacket.getServiceIdentifier().getServiceIdentifier(),
                                    wirePacket.getServiceIdentifier().getMethodIdentifier()));
                        }

                        return wirePacket;
                    } catch (InvalidProtocolBufferException ex) {
                        if(mEnableDecodeLogging) {
                            mLogger.error(String.format("[RpcDecoder:%s] Received invalid message", mLoggingName));
                        }
                    }
                }
            }
        } while (false);

        byteBuf.readerIndex(readerIdx);
        return null;
    }

    private static boolean findPacketSignature(ByteBuf byteBuf) {
        final int readIdx = byteBuf.readerIndex();
        final int readLimit = (byteBuf.readableBytes() - 4) - readIdx;

        boolean result = false;
        for(int i = 0; i < readLimit; i++) {
            if(byteBuf.getInt(readIdx + i) == PACKET_SIGNATURE) {
                result = true;
                break;
            }
        }

        byteBuf.readerIndex(readIdx);
        byteBuf.discardReadBytes();
        return result;
    }

    private static int discardBytes(ByteBuf byteBuf, int discardLength) {
        int thisDiscardLength = Math.min(discardLength, byteBuf.readableBytes());
        byteBuf.skipBytes(thisDiscardLength);
        return thisDiscardLength;
    }
}