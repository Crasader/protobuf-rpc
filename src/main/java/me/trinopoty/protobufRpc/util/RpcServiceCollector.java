package me.trinopoty.protobufRpc.util;

import com.google.protobuf.AbstractMessage;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.exception.DuplicateRpcMethodIdentifierException;
import me.trinopoty.protobufRpc.exception.DuplicateRpcServiceIdentifierException;
import me.trinopoty.protobufRpc.exception.IllegalMethodSignatureException;
import me.trinopoty.protobufRpc.exception.MissingRpcIdentifierException;
import me.trinopoty.protobufRpc.server.ProtobufRpcServerChannel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class RpcServiceCollector {

    public static final class RpcMethodInfo {

        private Method mMethod;
        private int mMethodIdentifier;
        private Method mRequestMessageParser;
        private Method mResponseMessageParser;

        public Method getMethod() {
            return mMethod;
        }

        public int getMethodIdentifier() {
            return mMethodIdentifier;
        }

        public Method getRequestMessageParser() {
            return mRequestMessageParser;
        }

        public Method getResponseMessageParser() {
            return mResponseMessageParser;
        }
    }

    public static final class RpcServiceInfo {

        public enum ConstructorType {
            DEFAULT,
            PARAMETERIZED
        }

        private Class mServiceClass;
        private int mServiceIdentifier;
        private Map<Method, RpcMethodInfo> mMethodMap;
        private Map<Integer, RpcMethodInfo> mMethodIdentifierMap;
        private boolean mIsOob;

        private Class mImplClass;
        private Pair<ConstructorType, Constructor> mImplClassConstructor;

        public Class getServiceClass() {
            return mServiceClass;
        }

        public int getServiceIdentifier() {
            return mServiceIdentifier;
        }

        public Map<Method, RpcMethodInfo> getMethodMap() {
            return mMethodMap;
        }

        public Map<Integer, RpcMethodInfo> getMethodIdentifierMap() {
            return mMethodIdentifierMap;
        }

        public boolean isOob() {
            return mIsOob;
        }

        public Class getImplClass() {
            return mImplClass;
        }

        public void setImplClass(Class implClass) {
            mImplClass = implClass;
        }

        public void setImplClassConstructor(Pair<ConstructorType, Constructor> implClassConstructor) {
            mImplClassConstructor = implClassConstructor;
        }

        public Object createImplClassObject(ProtobufRpcServerChannel rpcServerChannel) {
            if(mImplClassConstructor != null) {
                Object implObject = null;
                try {
                    switch (mImplClassConstructor.getFirst()) {
                        case DEFAULT:
                            implObject = mImplClassConstructor.getSecond().newInstance();
                            break;
                        case PARAMETERIZED:
                            implObject = mImplClassConstructor.getSecond().newInstance(rpcServerChannel);
                            break;
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw new RuntimeException("Unable to create service implementation object.", ex);
                }
                return implObject;
            } else {
                throw new IllegalStateException("Implementation class is not set for service interface " + mServiceClass.getName());
            }
        }
    }

    private HashSet<Integer> mServiceIdentifierList = new HashSet<>();
    private HashMap<Integer, Class> mServiceIdentifierClassMap = new HashMap<>();
    private HashMap<Class, RpcServiceInfo> mServiceInfoMap = new HashMap<>();

    public void parseServiceInterface(Class classOfService, boolean isOob) throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        if(!mServiceInfoMap.containsKey(classOfService)) {
            RpcServiceInfo rpcServiceInfo = parseServiceClass(classOfService, isOob);

            mServiceIdentifierList.add(rpcServiceInfo.getServiceIdentifier());
            mServiceIdentifierClassMap.put(rpcServiceInfo.getServiceIdentifier(), rpcServiceInfo.getServiceClass());
            mServiceInfoMap.put(classOfService, rpcServiceInfo);
        }
    }

    public RpcServiceInfo getServiceInfo(Class classOfService) {
        return mServiceInfoMap.get(classOfService);
    }

    public RpcServiceInfo getServiceInfo(int serviceIdentifier) {
        if(mServiceIdentifierList.contains(serviceIdentifier)) {
            return mServiceInfoMap.get(mServiceIdentifierClassMap.get(serviceIdentifier));
        }
        return null;
    }

    private synchronized RpcServiceInfo parseServiceClass(Class classOfService, boolean isOob) throws MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, DuplicateRpcMethodIdentifierException, IllegalMethodSignatureException {
        RpcServiceInfo rpcServiceInfo = new RpcServiceInfo();

        if(!classOfService.isInterface()) {
            throw new IllegalArgumentException(String.format("Class<%s> is not an interface.", classOfService.getName()));
        }

        rpcServiceInfo.mServiceClass = classOfService;

        Annotation rpcIdentifierAnnotation;
        rpcIdentifierAnnotation = classOfService.getAnnotation(RpcIdentifier.class);
        if(rpcIdentifierAnnotation == null) {
            throw new MissingRpcIdentifierException(String.format("Class<%s> does not contain @RpcIdentifier annotation.", classOfService.getName()));
        }

        rpcServiceInfo.mIsOob = isOob;

        rpcServiceInfo.mServiceIdentifier = ((RpcIdentifier) rpcIdentifierAnnotation).value();
        if(mServiceIdentifierList.contains(rpcServiceInfo.mServiceIdentifier)) {
            throw new DuplicateRpcServiceIdentifierException(String.format("Class<%s> contains duplicate @RpcIdentifier value. Duplicate class: %s", classOfService.getName(), mServiceIdentifierClassMap.get(rpcServiceInfo.mServiceIdentifier).getName()));
        }

        HashSet<Integer> methodIdentifierList = new HashSet<>();
        HashMap<Method, RpcMethodInfo> rpcMethodInfoMap = new HashMap<>();
        HashMap<Integer, RpcMethodInfo> rpcMethodInfoIdentifierMap = new HashMap<>();
        for(Method method : classOfService.getDeclaredMethods()) {
            RpcMethodInfo rpcMethodInfo = new RpcMethodInfo();
            rpcMethodInfo.mMethod = method;

            rpcIdentifierAnnotation = method.getAnnotation(RpcIdentifier.class);
            if(rpcIdentifierAnnotation == null) {
                throw new MissingRpcIdentifierException(String.format("Class<%s>.%s does not contain @RpcIdentifier annotation.", classOfService.getName(), method.getName()));
            }

            rpcMethodInfo.mMethodIdentifier = ((RpcIdentifier) rpcIdentifierAnnotation).value();
            if(methodIdentifierList.contains(rpcMethodInfo.mMethodIdentifier)) {
                throw new DuplicateRpcMethodIdentifierException(String.format("Class<%s>.%s contains duplicate @RpcIdentifier value.", classOfService.getName(), method.getName()));
            } else {
                methodIdentifierList.add(rpcMethodInfo.mMethodIdentifier);
            }

            if(method.getParameterTypes().length == 1) {
                Class requestType = method.getParameterTypes()[0];
                if(!AbstractMessage.class.isAssignableFrom(requestType)) {
                    throw new IllegalMethodSignatureException(String.format("Class<%s>.%s does not return accept a protobuf message.", classOfService.getName(), method.getName()));
                }

                //noinspection unchecked
                rpcMethodInfo.mRequestMessageParser = getProtobufParserMethod(requestType);
                assert rpcMethodInfo.mRequestMessageParser != null;
            } else if(method.getParameterTypes().length != 0) {
                throw new IllegalMethodSignatureException(String.format("Class<%s>.%s has invalid method signature.", classOfService.getName(), method.getName()));
            }

            Class responseType = method.getReturnType();
            if(!isOob) {
                if(!responseType.equals(void.class)) {
                    if(!AbstractMessage.class.isAssignableFrom(responseType)) {
                        throw new IllegalMethodSignatureException(String.format("Class<%s>.%s does not return a protobuf message.", classOfService.getName(), method.getName()));
                    }

                    //noinspection unchecked
                    rpcMethodInfo.mResponseMessageParser = getProtobufParserMethod(responseType);
                    assert rpcMethodInfo.mResponseMessageParser != null;
                }
            } else {
                if(!responseType.equals(void.class)) {
                    throw new IllegalMethodSignatureException(String.format("Class<%s>.%s does not return void.", classOfService.getName(), method.getName()));
                }
            }

            rpcMethodInfoMap.put(method, rpcMethodInfo);
            rpcMethodInfoIdentifierMap.put(rpcMethodInfo.mMethodIdentifier, rpcMethodInfo);
        }

        rpcServiceInfo.mMethodMap = Collections.unmodifiableMap(rpcMethodInfoMap);
        rpcServiceInfo.mMethodIdentifierMap = Collections.unmodifiableMap(rpcMethodInfoIdentifierMap);

        return rpcServiceInfo;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private Method getProtobufParserMethod(Class<? extends AbstractMessage> messageClass) {
        Method parserMethod = null;
        try {
            parserMethod = messageClass.getMethod("parseFrom", byte[].class);
        } catch (NoSuchMethodException ignore) {
        }
        return parserMethod;
    }
}