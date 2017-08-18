package me.trinopoty.protobufRpc.exception;

public final class RpcCallException extends RuntimeException {

    public RpcCallException() {
        super();
    }

    public RpcCallException(String message) {
        super(message);
    }

    public RpcCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcCallException(Throwable cause) {
        super(cause);
    }

    protected RpcCallException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}