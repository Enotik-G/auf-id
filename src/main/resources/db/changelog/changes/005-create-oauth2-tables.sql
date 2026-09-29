--liquibase formatted sql

-- Таблицы Spring Authorization Server. Структура — из официальных схем Spring
-- (spring-security-oauth2-authorization-server, файлы *-schema.sql), адаптированная под PostgreSQL
-- и наши соглашения: blob → text, timestamp → timestamptz (так советует сама схема для PostgreSQL),
-- именованные ограничения, уникальный client_id, индексы для поиска по токенам.
-- Имена и типы колонок менять нельзя: их читают JDBC-репозитории Spring.

--changeset enotik:005-create-oauth2-registered-client
-- Зарегистрированные сервисы-клиенты (планировщик и т.д.): куда возвращать после входа, какие права.
CREATE TABLE oauth2_registered_client (
    id                            varchar(100)  NOT NULL,
    client_id                     varchar(100)  NOT NULL,
    client_id_issued_at           timestamptz   NOT NULL DEFAULT now(),
    client_secret                 varchar(200),
    client_secret_expires_at      timestamptz,
    client_name                   varchar(200)  NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types     varchar(1000) NOT NULL,
    redirect_uris                 varchar(1000),
    post_logout_redirect_uris     varchar(1000),
    scopes                        varchar(1000) NOT NULL,
    client_settings               varchar(2000) NOT NULL,
    token_settings                varchar(2000) NOT NULL,

    CONSTRAINT oauth2_registered_client_pkey          PRIMARY KEY (id),
    CONSTRAINT oauth2_registered_client_client_id_key UNIQUE (client_id)
);

--changeset enotik:005-create-oauth2-authorization
-- Выданные авторизации: коды, access/refresh/id-токены — по одной строке на «вход в сервис».
CREATE TABLE oauth2_authorization (
    id                            varchar(100)  NOT NULL,
    registered_client_id          varchar(100)  NOT NULL,
    principal_name                varchar(200)  NOT NULL,
    authorization_grant_type      varchar(100)  NOT NULL,
    authorized_scopes             varchar(1000),
    attributes                    text,
    state                         varchar(500),
    authorization_code_value      text,
    authorization_code_issued_at  timestamptz,
    authorization_code_expires_at timestamptz,
    authorization_code_metadata   text,
    access_token_value            text,
    access_token_issued_at        timestamptz,
    access_token_expires_at       timestamptz,
    access_token_metadata         text,
    access_token_type             varchar(100),
    access_token_scopes           varchar(1000),
    oidc_id_token_value           text,
    oidc_id_token_issued_at       timestamptz,
    oidc_id_token_expires_at      timestamptz,
    oidc_id_token_metadata        text,
    refresh_token_value           text,
    refresh_token_issued_at       timestamptz,
    refresh_token_expires_at      timestamptz,
    refresh_token_metadata        text,
    user_code_value               text,
    user_code_issued_at           timestamptz,
    user_code_expires_at          timestamptz,
    user_code_metadata            text,
    device_code_value             text,
    device_code_issued_at         timestamptz,
    device_code_expires_at        timestamptz,
    device_code_metadata          text,

    CONSTRAINT oauth2_authorization_pkey PRIMARY KEY (id),
    CONSTRAINT oauth2_authorization_registered_client_id_fkey
        FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);

-- Spring ищет авторизацию по значению кода/токена; без индексов — перебор всей таблицы на каждый запрос.
CREATE INDEX oauth2_authorization_state_idx                    ON oauth2_authorization (state);
CREATE INDEX oauth2_authorization_authorization_code_value_idx ON oauth2_authorization (authorization_code_value);
CREATE INDEX oauth2_authorization_access_token_value_idx       ON oauth2_authorization (access_token_value);
CREATE INDEX oauth2_authorization_refresh_token_value_idx      ON oauth2_authorization (refresh_token_value);

--changeset enotik:005-create-oauth2-authorization-consent
-- Согласия «разрешить сервису X доступ к моим данным» (для своих сервисов экран согласия выключен).
CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,

    CONSTRAINT oauth2_authorization_consent_pkey PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT oauth2_authorization_consent_registered_client_id_fkey
        FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);
