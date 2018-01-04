package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.exception.*;
import me.trinopoty.protobufRpc.server.ProtobufRpcServer;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;
import org.junit.Test;

@SuppressWarnings("unused")
public final class RpcImplementationTest {

    @RpcIdentifier(1)
    public interface Service01 {

        @RpcIdentifier(1)
        Empty method01(Empty empty);
    }

    public static final class Service01Impl01 implements Service01 {

        @Override
        public Empty method01(Empty empty) {
            return null;
        }
    }

    public static final class Service01Impl02 implements Service01 {

        public Service01Impl02(ProtobufRpcServerChannel serverChannel) {
        }

        @Override
        public Empty method01(Empty empty) {
            return null;
        }
    }

    public static final class Service01Impl03 implements Service01 {

        public Service01Impl03(int value) {
        }

        @Override
        public Empty method01(Empty empty) {
            return null;
        }
    }

    @Test
    public void testEmptyConstructor() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.addServiceImplementation(Service01.class, Service01Impl01.class);
    }

    @Test
    public void testParameterizedConstructor() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.addServiceImplementation(Service01.class, Service01Impl02.class);
    }

    @Test(expected = ServiceConstructorNotFoundException.class)
    public void testInvalidConstructor() throws DuplicateRpcMethodIdentifierException, ServiceConstructorNotFoundException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcServer.Builder builder = new ProtobufRpcServer.Builder();
        builder.addServiceImplementation(Service01.class, Service01Impl03.class);
    }
}