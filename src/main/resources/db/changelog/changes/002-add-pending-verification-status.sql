--liquibase formatted sql

--changeset enotik:002-add-pending-verification-status
ALTER TABLE users ALTER COLUMN status TYPE varchar(32);

ALTER TABLE users DROP CONSTRAINT users_status_check;

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING_VERIFICATION', 'INVITED', 'ACTIVE', 'LOCKED', 'BLOCKED', 'DELETED'));
