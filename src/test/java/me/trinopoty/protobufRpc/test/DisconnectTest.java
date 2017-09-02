package me.trinopoty.protobufRpc.test;

import io.netty.channel.Channel;
import me.trinopoty.protobufRpc.DisconnectReason;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannelDisconnectListener;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.client.ProtobufRpcClientChannel;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannelDisconnectListener;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;
import me.trinopoty.protobufRpc.test.proto.EchoOuterClass;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.SocketChannel;

@SuppressWarnings({"JavaReflectionMemberAccess", "unchecked"})
public final class DisconnectTest {

    @SuppressWarnings("UnusedReturnValue")
    @RpcIdentifier(1)
    public interface EchoService {

        @RpcIdentifier(1)
        EchoOuterClass.Echo echo1(EchoOuterClass.Echo request);

        @RpcIdentifier(2)
        EchoOuterClass.Echo echo2(EchoOuterClass.Echo request);
    }

    @RpcIdentifier(100)
    public interface EchoOob {

        @RpcIdentifier(1)
        void echo1(EchoOuterClass.Echo request);
    }

    public static final class EchoServiceImpl implements EchoService {

        private final ProtobufRpcServerChannel mRpcServerChannel;

        private Thread mCloseThread;

        public EchoServiceImpl(ProtobufRpcServerChannel rpcServerChannel) {
            mRpcServerChannel = rpcServerChannel;
        }

        @Override
        public EchoOuterClass.Echo echo1(EchoOuterClass.Echo request) {
            if(mCloseThread == null) {
                mCloseThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignore) {
                        }

                        System.out.println("[Server] Closing connection");

                        //noinspection Duplicates
                        try {
                            Field channelField = mRpcServerChannel.getClass().getDeclaredField("mChannel");
                            channelField.setAccessible(true);

                            Channel channel = (Channel) channelField.get(mRpcServerChannel);
                            Class channelClass = channel.getClass();

                            Method javaChannelMethod = channelClass.getDeclaredMethod("javaChannel");
                            javaChannelMethod.setAccessible(true);
                            SocketChannel socketChannel = (SocketChannel) javaChannelMethod.invoke(channel);
                            socketChannel.socket().setSoLinger(true, 0);
                            socketChannel.socket().close();
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException | NoSuchFieldException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mCloseThread.start();
            }

            return request;
        }

        @Override
        public EchoOuterClass.Echo echo2(EchoOuterClass.Echo request) {
            System.out.println("[Server] Received echo2 request");

            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
            }

            mRpcServerChannel.getOobService(EchoOob.class).echo1(request);

            System.out.println("[Server] Sending echo2 response");

            return request;
        }
    }

    private static ProtobufRpcServer sProtobufRpcServer;

    @SuppressWarnings("Duplicates")
    @BeforeClass
    public static void setup() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        builder.addServiceImplementation(EchoService.class, EchoServiceImpl.class);
        builder.registerOob(EchoOob.class);
        ProtobufRpcServer server = builder.build();

        server.startServer();
        sProtobufRpcServer = server;
        sProtobufRpcServer.setChannelDisconnectListener(new ProtobufRpcServerChannelDisconnectListener() {
            @Override
            public void channelDisconnected(ProtobufRpcServerChannel channel, DisconnectReason reason) {
                System.out.println("[Server] Channel disconnect event. Reason: " + reason);
            }
        });
    }

    @AfterClass
    public static void cleanup() {
        sProtobufRpcServer.stopServer();
    }

    @Test(expected = RpcCallException.class)
    public void suddenServerDisconnectTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).registerService(EchoService.class).build();
        ProtobufRpcClientChannel clientChannel = client.getClientChannel("127.0.0.1", 6000);

        clientChannel.setChannelDisconnectListener(new ProtobufRpcClientChannelDisconnectListener() {
            @Override
            public void channelDisconnected(ProtobufRpcClientChannel channel, DisconnectReason reason) {
                System.out.println("[Client] Channel disconnect event. Reason: " + reason);
            }
        });

        EchoService echoService = clientChannel.getService(EchoService.class);

        for(int i = 0; i < 5; i++) {
            System.out.println("Sending echo1: " + i);
            echoService.echo1(EchoOuterClass.Echo.newBuilder().setMessage("Hello " + i).build());

            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
            }
        }

        clientChannel.close();
        client.close();
    }

    @Test
    public void suddenClientDisconnectTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        final ProtobufRpcClient client = (new ProtobufRpcClient.Builder()).registerService(EchoService.class).registerOob(EchoOob.class).build();
        final ProtobufRpcClientChannel clientChannel = client.getClientChannel("127.0.0.1", 6000);

        clientChannel.setChannelDisconnectListener(new ProtobufRpcClientChannelDisconnectListener() {
            @Override
            public void channelDisconnected(ProtobufRpcClientChannel channel, DisconnectReason reason) {
                System.out.println("[Client] Channel disconnect event. Reason: " + reason);
            }
        });

        clientChannel.addOobHandler(EchoOob.class, new EchoOob() {
            @Override
            public void echo1(EchoOuterClass.Echo request) {
                System.out.println("[Client] EchoOob.echo1: " + request);
            }
        });

        EchoService echoService = clientChannel.getService(EchoService.class);

        (new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignore) {
                }

                System.out.println("[Client] Closing channel");

                //noinspection Duplicates
                try {
                    Field channelField = clientChannel.getClass().getDeclaredField("mChannel");
                    channelField.setAccessible(true);

                    Channel channel = (Channel) channelField.get(clientChannel);
                    Class channelClass = channel.getClass();

                    Method javaChannelMethod = channelClass.getDeclaredMethod("javaChannel");
                    javaChannelMethod.setAccessible(true);
                    SocketChannel socketChannel = (SocketChannel) javaChannelMethod.invoke(channel);
                    socketChannel.socket().setSoLinger(true, 0);
                    socketChannel.socket().close();
                } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        System.out.println("[Client] Sending echo");
        echoService.echo2(EchoOuterClass.Echo.newBuilder().setMessage("Hello").build());

        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignore) {
        }

        clientChannel.close();
        client.close();
    }
}