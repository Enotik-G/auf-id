package ru.auf.id.admin;

public class ClientAlreadyExistsException extends RuntimeException {

    public ClientAlreadyExistsException(String clientId) {
        super("Клиент " + clientId + " уже зарегистрирован");
    }
}
