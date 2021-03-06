package me.trinopoty.protobufRpc.test;

import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;
import me.trinopoty.protobufRpc.test.proto.EchoOuterClass;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertNotNull;

public final class OobEchoTest {

    @RpcIdentifier(1)
    public interface EchoService {

        @RpcIdentifier(1)
        EchoOuterClass.Echo echo(EchoOuterClass.Echo request);
    }

    @RpcIdentifier(2)
    public interface OobService {

        @RpcIdentifier(1)
        void oob1(EchoOuterClass.Echo message);
    }

    public static final class EchoServiceImpl implements EchoService {

        private final ProtobufRpcServerChannel mRpcServerChannel;

        public EchoServiceImpl(ProtobufRpcServerChannel rpcServerChannel) {
            mRpcServerChannel = rpcServerChannel;
        }

        @Override
        public EchoOuterClass.Echo echo(EchoOuterClass.Echo request) {
            mRpcServerChannel.getOobService(OobService.class).oob1(request);
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
        builder.registerOob(OobService.class);
        ProtobufRpcServer server = builder.build();

        server.startServer();
        sProtobufRpcServer = server;
    }

    @AfterClass
    public static void cleanup() {
        sProtobufRpcServer.stopServer();
    }

    @Test
    public void oobEchoTest01() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).registerService(EchoService.class).registerOob(OobService.class).build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel(sProtobufRpcServer.getActualLocalAddress());
        EchoService echoService = clientChannel.getService(EchoService.class);

        clientChannel.addOobHandler(OobService.class, new OobService() {
            @Override
            public void oob1(EchoOuterClass.Echo message) {
                assertNotNull(message);

                System.out.println("OOB: " + message.getMessage());
            }
        });
        EchoOuterClass.Echo echo = echoService.echo(EchoOuterClass.Echo.newBuilder().setMessage("Hello World").build());
        assertNotNull(echo);

        clientChannel.close();
        client.close();
    }
}