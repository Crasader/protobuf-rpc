package me.trinopoty.protobufRpc;

import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannelPool;
import me.trinopoty.protobufRpc.codec.RpcMessageCodec;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;

public interface ProtobufRpcLog {

    String CODEC = RpcMessageCodec.class.getCanonicalName();

    String SERVER_RPC = ProtobufRpcServerChannel.class.getCanonicalName() + "-RPC";

    String CLIENT_POOL = ProtobufRpcClientChannelPool.class.getCanonicalName();
    String CLIENT_RPC = ProtobufRpcClientChannel.class.getCanonicalName() + "-RPC";
}