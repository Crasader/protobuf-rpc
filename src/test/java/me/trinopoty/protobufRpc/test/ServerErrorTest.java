package me.trinopoty.protobufRpc.test;

import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.test.proto.EchoOuterClass;
import org.junit.Test;

public final class ServerErrorTest {

    @RpcIdentifier(1)
    public interface EchoService01 {

        @RpcIdentifier(1)
        EchoOuterClass.Echo echo(EchoOuterClass.Echo request);
    }

    @RpcIdentifier(2)
    public interface EchoService02 {

        @RpcIdentifier(1)
        EchoOuterClass.Echo echo(EchoOuterClass.Echo request);
    }

    public static final class EchoService01Impl implements EchoService01 {

        @Override
        public EchoOuterClass.Echo echo(EchoOuterClass.Echo request) {
            return request;
        }
    }

    @Test(expected = RpcCallServerException.class)
    public void serverTest01() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        ProtobufRpcServer server = builder.build();
        server.addServiceImplementation(EchoService01.class, EchoService01Impl.class);
        server.startServer();

        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel("127.0.0.1", 6000);

        EchoService02 echoService02 = clientChannel.getService(EchoService02.class);
        echoService02.echo(EchoOuterClass.Echo.newBuilder().setMessage("Hello").build());

        clientChannel.close();
        client.close();

        server.stopServer();
    }
}