--liquibase formatted sql

--changeset enotik:011-add-password-reset-token-purpose
-- Возвращаем назначение PASSWORD_RESET, удалённое в 009. Тогда сброс шёл по почте и уехал вместе
-- с письмами, а «новую ссылку выдаёт администратор» опиралось на ссылку активации. Но её можно
-- выдать только учётке INVITED, и забывшему пароль студенту (ACTIVE) войти было нечем
-- (найдено 2026-10-16). Теперь ссылку сброса выдаёт администратор лично, срок — 24 часа.
--
-- Строк удалять не нужно: новое ограничение шире прежнего, все существующие ему подходят.
ALTER TABLE one_time_tokens DROP CONSTRAINT one_time_tokens_purpose_check;

ALTER TABLE one_time_tokens ADD CONSTRAINT one_time_tokens_purpose_check
    CHECK (purpose IN ('INVITE', 'PASSWORD_RESET'));
