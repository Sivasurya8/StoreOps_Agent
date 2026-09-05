package com.kiranapilot.exception;

public class KiranaPilotException extends RuntimeException {
    public KiranaPilotException(String message) {
        super(message);
    }

    public KiranaPilotException(String message, Throwable cause) {
        super(message, cause);
    }
}
