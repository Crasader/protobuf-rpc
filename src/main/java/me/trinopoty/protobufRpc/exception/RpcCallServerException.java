package me.trinopoty.protobufRpc.exception;

import java.io.IOException;

public final class RpcCallServerException extends IOException {

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
}