package com.aerovhyn.common.exception;

public class AerovhynException extends RuntimeException {
    private final int status;

    public AerovhynException(String message) {
        super(message);
        this.status = 500;
    }

    public AerovhynException(String message, int status) {
        super(message);
        this.status = status;
    }

    public AerovhynException(String message, Throwable cause) {
        super(message, cause);
        this.status = 500;
    }

    public int getStatus() {
        return status;
    }
}
