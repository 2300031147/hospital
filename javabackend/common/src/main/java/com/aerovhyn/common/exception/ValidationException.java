package com.aerovhyn.common.exception;

public class ValidationException extends AerovhynException {
    public ValidationException(String message) {
        super(message, 400);
    }
}
