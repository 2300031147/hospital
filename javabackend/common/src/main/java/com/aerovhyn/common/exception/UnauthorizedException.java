package com.aerovhyn.common.exception;

public class UnauthorizedException extends AerovhynException {
    public UnauthorizedException(String message) {
        super(message, 401);
    }
}
