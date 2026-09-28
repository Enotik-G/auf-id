--liquibase formatted sql

--changeset enotik:003-create-password-credentials
CREATE TABLE password_credentials (
    user_id       uuid         NOT NULL,
    password_hash varchar(255) NOT NULL,
    must_change   boolean      NOT NULL DEFAULT false,
    changed_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT password_credentials_pkey         PRIMARY KEY (user_id),
    CONSTRAINT password_credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
