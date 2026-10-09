--liquibase formatted sql

--changeset enotik:009-drop-password-reset-token-purpose
-- Сброса пароля по почте нет: писем сервис не отправляет (решение 2026-10-07), новую ссылку
-- человеку выдаёт администратор (POST /api/v1/admin/users/{id}/activation-link). Токены
-- PASSWORD_RESET никогда не выдавались, назначение осталось от отменённого плана.
--
-- Сначала удаляем строки (их быть не должно, но новое ограничение упало бы на любой), потом
-- меняем ограничение — так же, как 007 убирал EMAIL_VERIFY.
DELETE FROM one_time_tokens WHERE purpose = 'PASSWORD_RESET';

ALTER TABLE one_time_tokens DROP CONSTRAINT one_time_tokens_purpose_check;

ALTER TABLE one_time_tokens ADD CONSTRAINT one_time_tokens_purpose_check
    CHECK (purpose IN ('INVITE'));
