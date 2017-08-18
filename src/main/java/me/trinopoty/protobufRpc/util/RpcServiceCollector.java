package me.trinopoty.protobufRpc.util;

import com.google.protobuf.AbstractMessage;
import me.trinopoty.protobufRpc.annotation.RpcIdentifier;
import me.trinopoty.protobufRpc.exception.DuplicateRpcMethodIdentifierException;
import me.trinopoty.protobufRpc.exception.DuplicateRpcServiceIdentifierException;
import me.trinopoty.protobufRpc.exception.IllegalMethodSignatureException;
import me.trinopoty.protobufRpc.exception.MissingRpcIdentifierException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class RpcServiceCollector {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static final class RpcMethodInfo {

        private Method mMethod;
        private int mMethodIdentifier;
        private Class<? extends AbstractMessage> mRequestMessageType;
        private Class<? extends AbstractMessage> mResponseMessageType;
        private Method mRequestMessageParser;
        private Method mResponseMessageParser;

        public Method getMethod() {
            return mMethod;
        }

        public int getMethodIdentifier() {
            return mMethodIdentifier;
        }

        public Class<? extends AbstractMessage> getRequestMessageType() {
            return mRequestMessageType;
        }

        public Class<? extends AbstractMessage> getResponseMessageType() {
            return mResponseMessageType;
        }

        public Method getRequestMessageParser() {
            return mRequestMessageParser;
        }

        public Method getResponseMessageParser() {
            return mResponseMessageParser;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static final class RpcServiceInfo {

        private Class mService;
        private int mServiceIdentifier;
        private Map<Method, RpcMethodInfo> mMethodMap;
        private Map<Integer, RpcMethodInfo> mMethodIdentifierMap;

        private Class mImplClass;
        private Constructor mImplClassConstructor;

        public Class getService() {
            return mService;
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

        public Class getImplClass() {
            return mImplClass;
        }

        public void setImplClass(Class implClass) {
            mImplClass = implClass;
        }

        public Constructor getImplClassConstructor() {
            return mImplClassConstructor;
        }

        public void setImplClassConstructor(Constructor implClassConstructor) {
            mImplClassConstructor = implClassConstructor;
        }
    }

    private HashSet<Integer> mServiceIdentifierList = new HashSet<>();
    private HashMap<Integer, Class> mServiceIdentifierClassMap = new HashMap<>();
    private HashMap<Class, RpcServiceInfo> mServiceInfoMap = new HashMap<>();

    public void parseServiceInterface(Class classOfService) throws DuplicateRpcMethodIdentifierException, MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, IllegalMethodSignatureException {
        if(!mServiceInfoMap.containsKey(classOfService)) {
            RpcServiceInfo rpcServiceInfo = parseServiceClass(classOfService);

            mServiceIdentifierList.add(rpcServiceInfo.getServiceIdentifier());
            mServiceIdentifierClassMap.put(rpcServiceInfo.getServiceIdentifier(), rpcServiceInfo.getService());
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

    private synchronized RpcServiceInfo parseServiceClass(Class classOfService) throws MissingRpcIdentifierException, DuplicateRpcServiceIdentifierException, DuplicateRpcMethodIdentifierException, IllegalMethodSignatureException {
        RpcServiceInfo rpcServiceInfo = new RpcServiceInfo();

        do {
            if(!classOfService.isInterface()) {
                throw new IllegalArgumentException(String.format("Class<%s> is not an interface.", classOfService.getName()));
            }

            rpcServiceInfo.mService = classOfService;

            Annotation rpcIdentifierAnnotation;
            rpcIdentifierAnnotation = classOfService.getAnnotation(RpcIdentifier.class);
            if(rpcIdentifierAnnotation == null) {
                throw new MissingRpcIdentifierException(String.format("Class<%s> does not contain @RpcIdentifier annotation.", classOfService.getName()));
            }

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

                Class responseType = method.getReturnType();
                if(!AbstractMessage.class.isAssignableFrom(responseType)) {
                    throw new IllegalMethodSignatureException(String.format("Class<%s>.%s does not return a protobuf message.", classOfService.getName(), method.getName()));
                }

                //noinspection unchecked
                rpcMethodInfo.mResponseMessageType = responseType;

                if((method.getParameterTypes().length != 1) || !AbstractMessage.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalMethodSignatureException(String.format("Class<%s>.%s does not accept a protobuf message as parameter.", classOfService.getName(), method.getName()));
                }

                //noinspection unchecked
                rpcMethodInfo.mRequestMessageType = (Class<? extends AbstractMessage>) method.getParameterTypes()[0];

                rpcMethodInfo.mRequestMessageParser = getProtobufParserMethod(rpcMethodInfo.mRequestMessageType);
                rpcMethodInfo.mResponseMessageParser = getProtobufParserMethod(rpcMethodInfo.mResponseMessageType);

                assert rpcMethodInfo.mRequestMessageParser != null;
                assert rpcMethodInfo.mResponseMessageParser != null;

                rpcMethodInfoMap.put(method, rpcMethodInfo);
                rpcMethodInfoIdentifierMap.put(rpcMethodInfo.mMethodIdentifier, rpcMethodInfo);
            }

            rpcServiceInfo.mMethodMap = Collections.unmodifiableMap(rpcMethodInfoMap);
            rpcServiceInfo.mMethodIdentifierMap = Collections.unmodifiableMap(rpcMethodInfoIdentifierMap);
        } while (false);

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