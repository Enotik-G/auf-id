package com.example.planner.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Ключ — id пользователя: {@code findById(user.getId())} находит его пароль. */
public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {
}
