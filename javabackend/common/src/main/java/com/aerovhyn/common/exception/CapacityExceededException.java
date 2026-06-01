package com.aerovhyn.common.exception;

public class CapacityExceededException extends AerovhynException {
    public CapacityExceededException(String message) {
        super(message, 400);
    }
}
