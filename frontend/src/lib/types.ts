// Типы ответов API AUF ID. Повторяют Java-классы ответов — при их изменении править и здесь.

export type Role = 'ADMIN' | 'CURATOR' | 'STUDENT'

/** GET /api/v1/me — account/MeResponse.java */
export interface Me {
  id: string
  email: string
  fullName: string
  roles: Role[]
  /** Время — строкой ISO-8601 (2026-10-16T09:00:00Z): так его отдаёт Spring. */
  createdAt: string
  lastLoginAt: string | null
}

export type UserStatus = 'INVITED' | 'ACTIVE' | 'BLOCKED' | 'LOCKED' | 'DELETED'

/** Учётка глазами администратора — admin/UserResponse.java */
export interface AdminUser {
  id: string
  email: string
  fullName: string
  status: UserStatus
  roles: Role[]
  createdAt: string
  lastLoginAt: string | null
}

/** GET /api/v1/admin/users — admin/UserPage.java */
export interface UserPage {
  items: AdminUser[]
  /** Номер страницы, с нуля. */
  page: number
  size: number
  /** Сколько всего учёток подходит под фильтр. */
  total: number
}

/** POST /api/v1/admin/users — admin/InvitationResponse.java */
export interface InvitationResponse {
  userId: string
  /** Одноразовая ссылка: показать её можно только сейчас, сервер хранит лишь хеш. */
  activationLink: string
}

/** POST /api/v1/admin/users/{id}/password-reset-link — admin/PasswordResetLinkResponse.java */
export interface PasswordResetLinkResponse {
  userId: string
  passwordResetLink: string
}

/** Вид клиента — admin/ClientKind.java. Из него сервер выводит все настройки OAuth. */
export type ClientKind = 'NATIVE' | 'BROWSER' | 'CONFIDENTIAL' | 'SERVICE'

/** Зарегистрированный сервис — admin/ClientSummary.java. Секрета здесь нет: сервер хранит только хеш. */
export interface ClientSummary {
  clientId: string
  name: string
  kind: ClientKind
  redirectUris: string[]
  scopes: string[]
  registeredAt: string
}

/** Клиент и его секрет — admin/ClientCredentials.java. secret === null у BROWSER. */
export interface ClientCredentials {
  clientId: string
  secret: string | null
}
