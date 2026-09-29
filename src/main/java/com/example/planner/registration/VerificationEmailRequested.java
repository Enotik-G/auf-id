package com.example.planner.registration;

import com.example.planner.user.EmailAddress;

/**
 * Событие «пользователю нужно письмо с подтверждением почты».
 * Публикуется внутри транзакции регистрации, обрабатывается после её коммита.
 */
public record VerificationEmailRequested(EmailAddress email, String fullName, String rawToken) {
}
