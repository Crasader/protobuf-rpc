package me.trinopoty.protobufRpc.test;

import com.google.protobuf.Empty;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.client.ProtobufRpcClient;
import me.trinopoty.protobufRpc.exception.DuplicateRpcMethodIdentifierException;
import me.trinopoty.protobufRpc.exception.DuplicateRpcServiceIdentifierException;
import me.trinopoty.protobufRpc.exception.IllegalMethodSignatureException;
import me.trinopoty.protobufRpc.exception.MissingRpcIdentifierException;
import org.junit.Test;

@SuppressWarnings("unused")
public final class RpcInterfaceTest {

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

    public interface MissingIdentifierService {

        @RpcIdentifier(1)
        Empty empty(Empty empty);
    }

    @RpcIdentifier(1)
    public interface MissingIdentifierMethod {

        Empty empty(Empty empty);
    }

    @RpcIdentifier(1)
    public interface RpcMethodSignature {

        @RpcIdentifier(1)
        void method1();

        @RpcIdentifier(2)
        void method2();

        @RpcIdentifier(3)
        void method3(Empty empty);

        @RpcIdentifier(4)
        Empty method4();

        @RpcIdentifier(5)
        Empty method5(Empty empty);
    }

    @Test(expected = DuplicateRpcServiceIdentifierException.class)
    public void duplicateServiceIdentifierTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient.Builder builder = new ProtobufRpcClient.Builder();
        builder.registerService(DuplicateService01.class, DuplicateService02.class);
    }

    @Test(expected = DuplicateRpcMethodIdentifierException.class)
    public void duplicateMethodIdentifierTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient.Builder builder = new ProtobufRpcClient.Builder();
        builder.registerService(DuplicateMethod01.class);
    }

    @Test(expected = MissingRpcIdentifierException.class)
    public void missingIdentifierServiceTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient.Builder builder = new ProtobufRpcClient.Builder();
        builder.registerService(MissingIdentifierService.class);
    }

    @Test(expected = MissingRpcIdentifierException.class)
    public void missingIdentifierMethodTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient.Builder builder = new ProtobufRpcClient.Builder();
        builder.registerService(MissingIdentifierMethod.class);
    }

    @Test
    public void rpcMethodSignatureTest() throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        ProtobufRpcClient.Builder builder = new ProtobufRpcClient.Builder();
        builder.registerService(RpcMethodSignature.class);
    }
}