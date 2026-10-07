--liquibase formatted sql

--changeset enotik:007-drop-pending-verification-status
-- Саморегистрация с подтверждением почты отменена (решение от 2026-10-07): писем сервис не
-- отправляет, подтвердить владение ящиком нечем. Учётки заводит администратор, человек задаёт
-- пароль по одноразовой ссылке. Статус PENDING_VERIFICATION стал недостижимым.

-- Сначала переводим уже существующие записи, иначе новое ограничение на них упадёт и
-- приложение не стартует. Такие учётки никто не подтвердил, пароль у них может быть
-- установлен — но доступ им всё равно должен открыть администратор, поэтому INVITED.
UPDATE users SET status = 'INVITED' WHERE status = 'PENDING_VERIFICATION';

ALTER TABLE users DROP CONSTRAINT users_status_check;

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'BLOCKED', 'DELETED'));

--changeset enotik:007-drop-email-verify-token-purpose
-- Токены EMAIL_VERIFY больше не выдаются, а ручка, которая их гасила, удалена — открыть ими
-- нечего. Удаляем, а не помечаем использованными: значение purpose у таких строк новому
-- ограничению не подходит, и оно упало бы на них при создании. Ценности в них нет: там только
-- хеш токена и ссылка на пользователя.
DELETE FROM one_time_tokens WHERE purpose = 'EMAIL_VERIFY';

ALTER TABLE one_time_tokens DROP CONSTRAINT one_time_tokens_purpose_check;

ALTER TABLE one_time_tokens ADD CONSTRAINT one_time_tokens_purpose_check
    CHECK (purpose IN ('INVITE', 'PASSWORD_RESET'));
