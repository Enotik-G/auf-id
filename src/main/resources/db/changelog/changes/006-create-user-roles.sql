--liquibase formatted sql

--changeset enotik:006-create-user-roles
CREATE TABLE user_roles (
    user_id    uuid        NOT NULL,
    role       varchar(16) NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_roles_pkey         PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_role_check   CHECK (role IN ('ADMIN', 'CURATOR', 'STUDENT')),
    CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
