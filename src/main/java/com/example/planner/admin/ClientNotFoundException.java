package com.example.planner.admin;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(String clientId) {
        super("Клиент " + clientId + " не зарегистрирован");
    }
}
