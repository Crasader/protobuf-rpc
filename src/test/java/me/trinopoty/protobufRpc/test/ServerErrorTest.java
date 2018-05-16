package me.trinopoty.protobufRpc.test;

import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.test.proto.EchoOuterClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public final class ServerErrorTest {

    @RpcIdentifier(1)
    public interface EchoService01 {

        @SuppressWarnings("unused")
        @RpcIdentifier(1)
        EchoOuterClass.Echo echo(EchoOuterClass.Echo request);
    }

    @RpcIdentifier(2)
    public interface EchoService02 {

        @RpcIdentifier(1)
        void echo(EchoOuterClass.Echo request);
    }

    public static final class EchoService01Impl implements EchoService01 {

        @Override
        public EchoOuterClass.Echo echo(EchoOuterClass.Echo request) {
            return request;
        }
    }

    @Test(expected = RpcCallServerException.class)
    public void serverTest01() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException, UnknownHostException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalAddress(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        builder.addServiceImplementation(EchoService01.class, EchoService01Impl.class);
        ProtobufRpcServer server = builder.build();
        server.startServer();

        ProtobufRpcClient client = (new ProtobufRpcClient.Builder())
                .registerService(EchoService02.class)
                .build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel(server.getActualLocalAddress());

        EchoService02 echoService02 = clientChannel.getService(EchoService02.class);
        echoService02.echo(EchoOuterClass.Echo.newBuilder().setMessage("Hello").build());

        clientChannel.close();
        client.close();

        server.stopServer();
    }
}