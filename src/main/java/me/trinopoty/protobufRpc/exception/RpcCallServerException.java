package me.trinopoty.protobufRpc.exception;

public final class RpcCallServerException extends RuntimeException {

    public RpcCallServerException() {
        super();
    }

    public RpcCallServerException(String message) {
        super(message);
    }

    public RpcCallServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcCallServerException(Throwable cause) {
        super(cause);
    }

    protected RpcCallServerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}