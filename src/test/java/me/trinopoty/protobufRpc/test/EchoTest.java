package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.RpcClientChannel;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public final class EchoTest {

    @RpcIdentifier(identifier = 1)
    public interface EchoService {

        @RpcIdentifier(identifier = 1)
        Empty empty(Empty request);

        @RpcIdentifier(identifier = 2)
        EchoOuterClass.Echo echo(EchoOuterClass.Echo request);
    }

    public static final class EchoServiceImpl implements EchoService {

        @Override
        public Empty empty(Empty request) {
            return request;
        }

        @Override
        public EchoOuterClass.Echo echo(EchoOuterClass.Echo request) {
            return request;
        }
    }

    private static ProtobufRpcServer sProtobufRpcServer;

    @BeforeClass
    public static void setup() {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        ProtobufRpcServer server = builder.build();

        server.addServiceImplementation(EchoService.class, EchoServiceImpl.class);

        server.startServer();
        sProtobufRpcServer = server;
    }

    @AfterClass
    public static void cleanup() {
        sProtobufRpcServer.stopServer();
    }

    @Test
    public void emptyTest() {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).build();
        RpcClientChannel clientChannel = client.getClientChannel("127.0.0.1", 6000);
        EchoService echoService = clientChannel.getService(EchoService.class);

        assertNotNull(echoService.empty(Empty.getDefaultInstance()));

        clientChannel.close();
        client.close();
    }

    @Test
    public void echoTest() {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).build();
        RpcClientChannel clientChannel = client.getClientChannel("127.0.0.1", 6000);
        EchoService echoService = clientChannel.getService(EchoService.class);

        EchoOuterClass.Echo echo = echoService.echo(EchoOuterClass.Echo.newBuilder().setMessage("Hello World").build());
        assertNotNull(echo);
        assertEquals("Hello World", echo.getMessage());

        clientChannel.close();
        client.close();
    }
}