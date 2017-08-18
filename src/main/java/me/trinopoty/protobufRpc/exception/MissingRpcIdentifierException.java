package me.trinopoty.protobufRpc.exception;

public final class MissingRpcIdentifierException extends Exception {

    public MissingRpcIdentifierException() {
        super();
    }

    public MissingRpcIdentifierException(String message) {
        super(message);
    }

    public MissingRpcIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingRpcIdentifierException(Throwable cause) {
        super(cause);
    }

    protected MissingRpcIdentifierException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}