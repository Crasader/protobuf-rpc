package me.trinopoty.protobufRpc.codec;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.TooLongFrameException;
import me.trinopoty.protobufRpc.ProtobufRpcLog;
import me.trinopoty.protobufRpc.util.CRC32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public final class RpcMessageCodec extends ByteToMessageCodec<WirePacketFormat.WirePacket> {

    private static final int PACKET_SIGNATURE = 0xad04ef64;

    private final int mMaxReceivePacketLength;
    private final boolean mDiscardLargerPacket;

    private final Logger mLogger;
    private final String mLoggingName;
    private final boolean mEnableEncodeLogging;
    private final boolean mEnableDecodeLogging;

    private int mDiscardLength = 0;

    private boolean mIsReadingPacket = false;
    private byte[] mReadBuffer;
    private int mReadBufferPosition;
    private int mReadLengthRemaining;

    public RpcMessageCodec(
            int maxReceivePacketLength,
            boolean discardLargerPacket,
            String loggingName,
            boolean enableEncodeLogging,
            boolean enableDecodeLogging) {
        mMaxReceivePacketLength = maxReceivePacketLength;
        mDiscardLargerPacket = discardLargerPacket;

        mEnableEncodeLogging = enableEncodeLogging;
        mEnableDecodeLogging = enableDecodeLogging;
        mLoggingName = loggingName;

        mReadBuffer = new byte[mMaxReceivePacketLength];
        mReadBufferPosition = 0;

        mLogger = (mEnableDecodeLogging || mEnableEncodeLogging)? LogManager.getLogger(ProtobufRpcLog.CODEC) : null;

        if((mLoggingName == null) && (mLogger != null)) {
            throw new IllegalArgumentException("Logging name not provided.");
        }
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, WirePacketFormat.WirePacket wirePacket, ByteBuf byteBuf) {
        byte[] messageBytes = wirePacket.toByteArray();

        if(mEnableEncodeLogging) {
            logWireMessage(wirePacket);
        }

        byteBuf.writeInt(PACKET_SIGNATURE);
        byteBuf.writeInt(messageBytes.length);
        byteBuf.writeBytes(messageBytes);
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        WirePacketFormat.WirePacket wirePacket = decode(byteBuf);
        if(wirePacket != null) {
            list.add(wirePacket);
        }
    }

    private WirePacketFormat.WirePacket decode(ByteBuf byteBuf) {
        //noinspection UnusedAssignment
        int readerIdx = byteBuf.readerIndex();

        //noinspection ConstantConditions
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

            if(mIsReadingPacket) {
                final int bytesToRead = Math.min(byteBuf.readableBytes(), mReadLengthRemaining);
                byteBuf.readBytes(mReadBuffer, mReadBufferPosition, bytesToRead);
                mReadLengthRemaining -= bytesToRead;
                mReadBufferPosition += bytesToRead;

                readerIdx = byteBuf.readerIndex();

                if(mReadLengthRemaining == 0) {
                    try {
                        return processPacket(mReadBuffer, mReadBufferPosition);
                    } finally {
                        mIsReadingPacket = false;
                        mReadLengthRemaining = 0;
                        mReadBufferPosition = 0;
                    }
                }

                break;
            }

            boolean signatureFound = findPacketSignature(byteBuf);

            if(signatureFound) {
                byteBuf.discardReadBytes();
                readerIdx = byteBuf.readerIndex();

                signatureFound = (byteBuf.readableBytes() > 8);
            }

            if(signatureFound) {
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
                } else if(byteBuf.readableBytes() >= payloadLength) {
                    byteBuf.readBytes(mReadBuffer, 0, payloadLength);
                    return processPacket(mReadBuffer, payloadLength);
                } else {
                    readerIdx = byteBuf.readerIndex();

                    mReadLengthRemaining = payloadLength;
                    mReadBufferPosition = 0;
                    mIsReadingPacket = true;
                }
            }
        } while (false);

        byteBuf.readerIndex(readerIdx);
        return null;
    }

    private WirePacketFormat.WirePacket processPacket(byte[] buffer, int length) {
        try {
            byte[] localBuffer = new byte[length];
            System.arraycopy(buffer, 0, localBuffer, 0, length);

            WirePacketFormat.WirePacket wirePacket = WirePacketFormat.WirePacket.parseFrom(localBuffer);

            if(mEnableDecodeLogging) {
                logWireMessage(wirePacket);
            }

            if(wirePacket.hasPayload() && wirePacket.hasCrc32()) {
                final long crc32_provided = wirePacket.getCrc32() & 0x00000000ffffffffL;
                final long crc32_calculated = CRC32.calculateCrc32(wirePacket.getPayload());

                if(crc32_provided != crc32_calculated) {
                    if(mEnableDecodeLogging) {
                        mLogger.error(String.format("[RpcDecoder:%s] Invalid payload crc32", mLoggingName));
                    }
                    return null;
                }
            }

            return wirePacket;
        } catch (InvalidProtocolBufferException ex) {
            if(mEnableDecodeLogging) {
                mLogger.error(String.format("[RpcDecoder:%s] Received invalid message", mLoggingName));
            }
        }

        return null;
    }

    private void logWireMessage(WirePacketFormat.WirePacket wirePacket) {
        switch (wirePacket.getMessageType()) {
            case MESSAGE_TYPE_KEEP_ALIVE:
                break;
            case MESSAGE_TYPE_REQUEST:
                mLogger.info(String.format(
                        "[RpcEncoder:%s] Request { serviceIdentifier: %d; methodIdentifier: %d; messageIdentifier: %d }",
                        mLoggingName,
                        wirePacket.getServiceIdentifier().getServiceIdentifier(),
                        wirePacket.getServiceIdentifier().getMethodIdentifier(),
                        wirePacket.getMessageIdentifier()));
                break;
            case MESSAGE_TYPE_RESPONSE:
                mLogger.info(String.format(
                        "[RpcEncoder:%s] Response { serviceIdentifier: %d; methodIdentifier: %d; messageIdentifier: %d }",
                        mLoggingName,
                        wirePacket.getServiceIdentifier().getServiceIdentifier(),
                        wirePacket.getServiceIdentifier().getMethodIdentifier(),
                        wirePacket.getMessageIdentifier()));
                break;
            case MESSAGE_TYPE_OOB:
                mLogger.info(String.format(
                        "[RpcEncoder:%s] OOB { serviceIdentifier: %d; methodIdentifier: %d }",
                        mLoggingName,
                        wirePacket.getServiceIdentifier().getServiceIdentifier(),
                        wirePacket.getServiceIdentifier().getMethodIdentifier()));
                break;
            case MESSAGE_TYPE_ERROR:
                mLogger.info(String.format(
                        "[RpcEncoder:%s] Error { serviceIdentifier: %d; methodIdentifier: %d }",
                        mLoggingName,
                        wirePacket.getServiceIdentifier().getServiceIdentifier(),
                        wirePacket.getServiceIdentifier().getMethodIdentifier()));
                break;
        }
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