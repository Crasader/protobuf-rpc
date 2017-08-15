package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import org.junit.Test;

public final class ServerInitTest {

    @RpcIdentifier(identifier = 1)
    public interface Service01 {

        @RpcIdentifier(identifier = 1)
        Empty method1(Empty request);
    }

    public static final class Service01Impl implements Service01 {

        public Service01Impl() {

        }

        @Override
        public Empty method1(Empty request) {
            return null;
        }
    }

    @Test
    public void initTest01() {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        ProtobufRpcServer server = builder.build();

        server.addServiceImplementation(Service01.class, Service01Impl.class);
    }
}