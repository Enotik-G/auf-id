import type { Role } from './types'

// Как показывать человеку то, что API отдаёт в «машинном» виде.

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Администратор',
  CURATOR: 'Куратор',
  STUDENT: 'Студент',
}

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role]
}

/**
 * «16 октября 2026 г., 12:00» из «2026-10-16T09:00:00Z».
 *
 * Сервер отдаёт время в UTC, а показываем в часовом поясе браузера — у человека он свой.
 * timeZone задают только тесты: так результат не зависит от пояса компьютера, где они идут.
 */
export function formatDateTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeStyle: 'short', timeZone }).format(new Date(iso))
}
