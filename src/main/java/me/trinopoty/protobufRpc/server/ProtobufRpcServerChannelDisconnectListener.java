package me.trinopoty.protobufRpc.server;

import me.trinopoty.protobufRpc.DisconnectReason;

public interface ProtobufRpcServerChannelDisconnectListener {

    void channelDisconnected(RpcServerChannel channel, DisconnectReason reason);
}