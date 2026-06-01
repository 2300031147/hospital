package com.aerovhyn.common.exception;

public class ResourceNotFoundException extends AerovhynException {
    public ResourceNotFoundException(String message) {
        super(message, 404);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id, 404);
    }
}
