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
