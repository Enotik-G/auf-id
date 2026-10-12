--liquibase formatted sql

--changeset enotik:008-index-authorization-principal
-- Удаление авторизаций по владельцу: при блокировке пользователя (UserAuthorizationRevoker.revokeAll —
-- по principal_name) и при краже refresh-токена (revokeAllForClient — по principal_name и клиенту).
-- Без индекса каждое такое удаление читает всю таблицу, а строк в ней — по одной на каждый вход
-- каждого человека в каждый сервис.
--
-- Один составной индекс закрывает оба запроса: Postgres использует его и по одной первой колонке.
CREATE INDEX oauth2_authorization_principal_client_idx
    ON oauth2_authorization (principal_name, registered_client_id);
