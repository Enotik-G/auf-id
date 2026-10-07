package com.example.planner.admin;

/** Набор настроек клиента несочетаемый или неполный. */
public class InvalidClientSpecException extends RuntimeException {

    public InvalidClientSpecException(String message) {
        super(message);
    }
}
