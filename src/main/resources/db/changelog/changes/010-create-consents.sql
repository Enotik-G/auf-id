--liquibase formatted sql

--changeset enotik:010-create-consents
-- Согласия на обработку персональных данных (152-ФЗ). Человек даёт его при активации учётки,
-- вместе с паролем. Строка — доказательство: кто, на какой документ, какой его версии, когда и
-- откуда согласился. Поэтому строки только добавляются: новая версия документа — новое согласие,
-- прежние остаются как история.
CREATE TABLE consents (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    user_id     uuid        NOT NULL,
    doc_type    varchar(32) NOT NULL,
    doc_version varchar(64) NOT NULL,
    accepted_at timestamptz NOT NULL,
    -- Адрес, с которого дано согласие (IPv6 — до 45 символов). Тоже ПДн, но без него запись
    -- плохо годится как доказательство.
    ip          varchar(45) NOT NULL,

    CONSTRAINT consents_pkey           PRIMARY KEY (id),
    CONSTRAINT consents_doc_type_check CHECK (doc_type IN ('PERSONAL_DATA')),
    CONSTRAINT consents_user_id_fkey   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX consents_user_id_idx ON consents (user_id);
