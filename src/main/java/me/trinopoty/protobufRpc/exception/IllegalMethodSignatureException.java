package me.trinopoty.protobufRpc.exception;

public final class IllegalMethodSignatureException extends Exception {

    public IllegalMethodSignatureException() {
        super();
    }

    public IllegalMethodSignatureException(String message) {
        super(message);
    }

    public IllegalMethodSignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalMethodSignatureException(Throwable cause) {
        super(cause);
    }

    protected IllegalMethodSignatureException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}