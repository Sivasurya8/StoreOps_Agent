package com.kiranapilot.exception;

public class ProductNotFoundException extends KiranaPilotException {
    public ProductNotFoundException(String identifier) {
        super(String.format("Product '%s' not found in store catalog.", identifier));
    }
}
