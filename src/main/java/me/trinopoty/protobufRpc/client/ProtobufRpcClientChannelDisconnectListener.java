package me.trinopoty.protobufRpc.client;

import me.trinopoty.protobufRpc.DisconnectReason;

public interface ProtobufRpcClientChannelDisconnectListener {

    void channelDisconnected(ProtobufRpcClientChannel channel, DisconnectReason reason);
}