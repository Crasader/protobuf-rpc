package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.exception.DuplicateRpcMethodIdentifierException;
import me.trinopoty.protobufRpc.exception.DuplicateRpcServiceIdentifierException;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import org.junit.Test;

public final class DuplicateIdentifierTest {

    @RpcIdentifier(1)
    public interface DuplicateService01 {

        @RpcIdentifier(1)
        Empty empty(Empty empty);
    }

    @RpcIdentifier(1)
    public interface DuplicateService02 {

        @RpcIdentifier(1)
        Empty empty(Empty empty);
    }

    @RpcIdentifier(1)
    public interface DuplicateMethod01 {

        @RpcIdentifier(1)
        Empty empty(Empty empty);

        @RpcIdentifier(1)
        Empty empty2(Empty empty);
    }

    public static class DuplicateService01Impl implements DuplicateService01 {

        @Override
        public Empty empty(Empty empty) {
            return null;
        }
    }

    public static class DuplicateService02Impl implements DuplicateService02 {

        @Override
        public Empty empty(Empty empty) {
            return null;
        }
    }

    public static class DuplicateMethod01Impl implements DuplicateMethod01 {

        @Override
        public Empty empty(Empty empty) {
            return null;
        }

        @Override
        public Empty empty2(Empty empty) {
            return null;
        }
    }

    @Test(expected = DuplicateRpcServiceIdentifierException.class)
    public void duplicateServiceIdentifierTest() {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        ProtobufRpcServer server = builder.build();

        server.addServiceImplementation(DuplicateService01.class, DuplicateService01Impl.class);
        server.addServiceImplementation(DuplicateService02.class, DuplicateService02Impl.class);
    }

    @Test(expected = DuplicateRpcMethodIdentifierException.class)
    public void duplicateMethodIdentifierTest() {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        ProtobufRpcServer server = builder.build();

        server.addServiceImplementation(DuplicateMethod01.class, DuplicateMethod01Impl.class);
    }
}