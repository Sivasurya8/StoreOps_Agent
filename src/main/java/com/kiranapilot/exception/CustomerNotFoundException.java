package com.kiranapilot.exception;

public class CustomerNotFoundException extends KiranaPilotException {
    public CustomerNotFoundException(String identifier) {
        super(String.format("Customer '%s' does not exist in Khata ledger.", identifier));
    }
}
