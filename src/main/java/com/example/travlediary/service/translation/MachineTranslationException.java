package com.example.travlediary.service.translation;

public class MachineTranslationException extends RuntimeException {
    public MachineTranslationException(String message) {
        super(message);
    }

    public MachineTranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
