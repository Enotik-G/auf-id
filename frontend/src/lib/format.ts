import type { ClientKind, Role, UserStatus } from './types'

// Как показывать человеку то, что API отдаёт в «машинном» виде.

const ROLE_LABELS: Record<Role, string> = {
  STUDENT: 'Студент',
  CURATOR: 'Куратор',
  ADMIN: 'Администратор',
}

/** Все роли по порядку — для флажков. */
export const ROLES = Object.keys(ROLE_LABELS) as Role[]

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role]
}

const STATUS_LABELS: Record<UserStatus, string> = {
  INVITED: 'Ждёт активации',
  ACTIVE: 'Активна',
  BLOCKED: 'Заблокирована',
  LOCKED: 'Вход закрыт',
  DELETED: 'Удалена',
}

export function statusLabel(status: UserStatus): string {
  return STATUS_LABELS[status]
}

/** Все статусы по порядку — для списка в фильтре. */
export const USER_STATUSES = Object.keys(STATUS_LABELS) as UserStatus[]

const CLIENT_KIND_LABELS: Record<ClientKind, string> = {
  BROWSER: 'Браузерное приложение',
  NATIVE: 'Настольное приложение',
  CONFIDENTIAL: 'Серверное приложение',
  SERVICE: 'Сервис без пользователя',
}

export function clientKindLabel(kind: ClientKind): string {
  return CLIENT_KIND_LABELS[kind]
}

/** Все виды клиентов по порядку — для выбора при регистрации. */
export const CLIENT_KINDS = Object.keys(CLIENT_KIND_LABELS) as ClientKind[]

/**
 * «16 октября 2026 г. в 12:00» из «2026-10-16T09:00:00Z».
 *
 * Сервер отдаёт время в UTC, а показываем в часовом поясе браузера — у человека он свой.
 * timeZone задают только тесты: так результат не зависит от пояса компьютера, где они идут.
 */
export function formatDateTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeStyle: 'short', timeZone }).format(new Date(iso))
}
