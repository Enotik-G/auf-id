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
