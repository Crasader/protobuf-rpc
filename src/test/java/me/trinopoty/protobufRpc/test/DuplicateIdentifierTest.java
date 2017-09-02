package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;
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

        public DuplicateService01Impl(ProtobufRpcServerChannel serverChannel) {
        }

        @Override
        public Empty empty(Empty empty) {
            return null;
        }
    }

    public static class DuplicateMethod01Impl implements DuplicateMethod01 {

        public DuplicateMethod01Impl(ProtobufRpcServerChannel serverChannel) {
        }

        @Override
        public Empty empty(Empty empty) {
            return null;
        }

        @Override
        public Empty empty2(Empty empty) {
            return null;
        }
    }

    public static class DuplicateService02Impl implements DuplicateService02 {

        public DuplicateService02Impl(ProtobufRpcServerChannel serverChannel) {
        }

        @Override
        public Empty empty(Empty empty) {
            return null;
        }
    }

    @Test(expected = DuplicateRpcServiceIdentifierException.class)
    public void duplicateServiceIdentifierTest() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        builder.addServiceImplementation(DuplicateService01.class, DuplicateService01Impl.class);
        builder.addServiceImplementation(DuplicateService02.class, DuplicateService02Impl.class);
        ProtobufRpcServer server = builder.build();
    }

    @Test(expected = DuplicateRpcMethodIdentifierException.class)
    public void duplicateMethodIdentifierTest() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.setLocalPort(6000);
        builder.addServiceImplementation(DuplicateMethod01.class, DuplicateMethod01Impl.class);
        ProtobufRpcServer server = builder.build();
    }
}