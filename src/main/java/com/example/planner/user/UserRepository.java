package com.example.planner.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    /**
     * Сколько пользователей имеют эту роль — считая только тех, кто может ею воспользоваться.
     *
     * <p>Заблокированные и удалённые не считаются: оставить роль ADMIN у заблокированного — то же, что
     * не иметь администратора вовсе.
     */
    @Query("""
            select count(user)
              from User user
              join user.roles role
             where role = :role
               and user.status not in (
                   com.example.planner.user.UserStatus.BLOCKED,
                   com.example.planner.user.UserStatus.DELETED)
            """)
    long countUsableWithRole(@Param("role") Role role);
}
