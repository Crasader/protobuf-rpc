package me.trinopoty.protobufRpc.exception;

public final class DuplicateRpcMethodIdentifierException extends Exception {

    public DuplicateRpcMethodIdentifierException() {
        super();
    }

    public DuplicateRpcMethodIdentifierException(String message) {
        super(message);
    }

    public DuplicateRpcMethodIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateRpcMethodIdentifierException(Throwable cause) {
        super(cause);
    }

    protected DuplicateRpcMethodIdentifierException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}