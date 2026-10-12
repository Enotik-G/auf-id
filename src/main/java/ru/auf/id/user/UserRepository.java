package ru.auf.id.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                   ru.auf.id.user.UserStatus.BLOCKED,
                   ru.auf.id.user.UserStatus.DELETED)
            """)
    long countUsableWithRole(@Param("role") Role role);

    /**
     * Страница пользователей для админки: по подстроке в ФИО или почте и по статусу.
     *
     * <p>Оба фильтра необязательны: {@code null} — не фильтровать. {@code pattern} — уже готовый
     * шаблон LIKE в нижнем регистре ({@code %иван%}) с экранированными {@code %} и {@code _}: его
     * собирает {@code AdminUserService}, чтобы введённый админом {@code _} искался как символ, а не
     * как «любой символ».
     *
     * <p>{@code cast(user.email as String)} — потому что почта в сущности не строка, а
     * {@link EmailAddress}; в БД это обычный {@code varchar}, и сравнивается именно он.
     */
    @Query("""
            select user
              from User user
             where (:status is null or user.status = :status)
               and (:pattern is null
                    or lower(user.fullName) like :pattern escape '\\'
                    or cast(user.email as String) like :pattern escape '\\')
            """)
    Page<User> search(@Param("pattern") String pattern, @Param("status") UserStatus status, Pageable pageable);
}
