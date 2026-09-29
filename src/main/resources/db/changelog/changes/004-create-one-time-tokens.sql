--liquibase formatted sql

--changeset enotik:004-create-one-time-tokens
CREATE TABLE one_time_tokens (
    id         uuid        NOT NULL DEFAULT uuidv7(),
    user_id    uuid        NOT NULL,
    purpose    varchar(32) NOT NULL,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,

    CONSTRAINT one_time_tokens_pkey            PRIMARY KEY (id),
    CONSTRAINT one_time_tokens_token_hash_key  UNIQUE (token_hash),
    CONSTRAINT one_time_tokens_purpose_check   CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'INVITE')),
    CONSTRAINT one_time_tokens_expires_check   CHECK (expires_at > created_at),
    CONSTRAINT one_time_tokens_user_id_fkey    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX one_time_tokens_user_id_idx ON one_time_tokens (user_id);
