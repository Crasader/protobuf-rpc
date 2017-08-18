package me.trinopoty.protobufRpc.exception;

public final class DuplicateRpcServiceIdentifierException extends Exception {

    public DuplicateRpcServiceIdentifierException() {
        super();
    }

    public DuplicateRpcServiceIdentifierException(String message) {
        super(message);
    }

    public DuplicateRpcServiceIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateRpcServiceIdentifierException(Throwable cause) {
        super(cause);
    }

    protected DuplicateRpcServiceIdentifierException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}