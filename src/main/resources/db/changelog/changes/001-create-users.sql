--liquibase formatted sql

--changeset enotik:001-create-users
CREATE TABLE users (
    id             uuid         NOT NULL DEFAULT uuidv7(),
    email          varchar(254) NOT NULL,
    full_name      varchar(255) NOT NULL,
    email_verified boolean      NOT NULL DEFAULT false,
    status         varchar(16)  NOT NULL DEFAULT 'INVITED',
    created_at     timestamptz  NOT NULL DEFAULT now(),
    last_login_at  timestamptz,

    CONSTRAINT users_pkey         PRIMARY KEY (id),
    CONSTRAINT users_email_key    UNIQUE (email),
    CONSTRAINT users_status_check CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'BLOCKED', 'DELETED'))
);
