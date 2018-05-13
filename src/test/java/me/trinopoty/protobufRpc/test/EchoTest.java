package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.test.proto.EchoOuterClass;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class EchoTest {

    @RpcIdentifier(1)
    public interface EchoService {

        @RpcIdentifier(1)
        Empty empty(Empty request);

        @RpcIdentifier(2)
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

    @SuppressWarnings("Duplicates")
    @BeforeClass
    public static void setup() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException, UnknownHostException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalAddress(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        builder.addServiceImplementation(EchoService.class, EchoServiceImpl.class);
        ProtobufRpcServer server = builder.build();

        server.startServer();
        sProtobufRpcServer = server;
    }

    @AfterClass
    public static void cleanup() {
        sProtobufRpcServer.stopServer();
    }

    @Test
    public void emptyTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).registerService(EchoService.class).build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel(sProtobufRpcServer.getActualLocalAddress());
        EchoService echoService = clientChannel.getService(EchoService.class);

        assertNotNull(echoService.empty(Empty.getDefaultInstance()));

        clientChannel.close();
        client.close();
    }

    @Test
    public void echoTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).registerService(EchoService.class).build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel(sProtobufRpcServer.getActualLocalAddress());
        EchoService echoService = clientChannel.getService(EchoService.class);

        EchoOuterClass.Echo echo = echoService.echo(EchoOuterClass.Echo.newBuilder().setMessage("Hello World").build());
        assertNotNull(echo);
        assertEquals("Hello World", echo.getMessage());

        clientChannel.close();
        client.close();
    }
}