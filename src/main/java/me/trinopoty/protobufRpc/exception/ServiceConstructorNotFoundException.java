package me.trinopoty.protobufRpc.exception;

public final class ServiceConstructorNotFoundException extends Exception {

    public ServiceConstructorNotFoundException() {
        super();
    }

    public ServiceConstructorNotFoundException(String message) {
        super(message);
    }

    public ServiceConstructorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceConstructorNotFoundException(Throwable cause) {
        super(cause);
    }

    protected ServiceConstructorNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}