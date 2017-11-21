package me.trinopoty.protobufRpc;

import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.codec.RpcMessageCodec;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;

public interface ProtobufRpcLog {

    String CODEC = RpcMessageCodec.class.getCanonicalName();
    String SERVER = ProtobufRpcServer.class.getCanonicalName();
    String CLIENT = ProtobufRpcClient.class.getCanonicalName();
}